package site.kael.conversationcompiler.manager.compiler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.kael.conversationcompiler.agent.CompilerAgentService;
import site.kael.conversationcompiler.repository.EventRepository;
import site.kael.conversationcompiler.repository.compiler.*;
import site.kael.conversationcompiler.repository.settings.KnowledgeSettingsRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name={"conversation-compiler.compiler.enabled", "conversation-compiler.agent.enabled"}, havingValue="true")
public class CompileRunWorker {
    private final CompileRunRepository runs;
    private final CompileRunExecutionRepository execution;
    private final EventRepository events;
    private final KnowledgeSettingsRepository settings;
    private final CompilerAgentService agent;
    private final Semaphore permits;

    public CompileRunWorker(CompileRunRepository runs, CompileRunExecutionRepository execution,
                            EventRepository events, KnowledgeSettingsRepository settings,
                            CompilerAgentService agent) {
        this(runs, execution, events, settings, agent, 1);
    }

    public CompileRunWorker(CompileRunRepository runs, CompileRunExecutionRepository execution,
                            EventRepository events, KnowledgeSettingsRepository settings,
                            CompilerAgentService agent, int maxConcurrentRuns) {
        this.runs = runs; this.execution = execution; this.events = events;
        this.settings = settings; this.agent = agent;
        this.permits = new Semaphore(Math.max(1, maxConcurrentRuns));
    }

    @Scheduled(fixedDelayString="${conversation-compiler.compiler.scan-interval-seconds:60}000")
    public void scan() {
        for (Long id : runs.findPendingIds(1)) execute(id);
    }

    public void execute(long id) {
        if (!permits.tryAcquire()) return;
        try {
            executeClaimed(id);
        } finally {
            permits.release();
        }
    }

    private void executeClaimed(long id) {
        var run = runs.findById(id).orElseThrow();
        Map<String, Long> attempts = new HashMap<>();
        AtomicInteger attemptSequence = new AtomicInteger();
        try {
            var setting = settings.find().orElseThrow(() -> new IllegalStateException("knowledge compile settings are not configured"));
            if (!execution.markRunning(id, "loading_events", setting.providerId(), setting.modelName(), setting.prompt())) return;
            // The range is fixed when the pending run is created. Events appended while the
            // agent is working are deliberately left for the next run.
            var list = events.findBySessionIdAndVersionRange(run.sessionId(), run.fromVersion(), run.toVersion());
            execution.updatePhase(id, "agent_running", 20);
            var observer = new site.kael.conversationcompiler.agent.ModelFailoverRunner.AttemptObserver() {
                public void started(String candidate, int attempt) {
                    String[] parts = candidate.split("\\n", 2);
                    attempts.put(candidate + "#" + attempt,
                            execution.startAttempt(id, attemptSequence.incrementAndGet(), parts[0], parts.length > 1 ? parts[1] : ""));
                }
                public void finished(String candidate, int attempt, String status, Throwable error) {
                    Long attemptId = attempts.get(candidate + "#" + attempt);
                    if (attemptId != null) execution.finishAttempt(attemptId, status,
                            error == null ? null : error.getClass().getSimpleName(),
                            error == null ? null : error.getMessage());
                }
            };
            var result = agent.compile(setting.prompt(), setting.providerId(), setting.modelName(),
                    run.sessionId(), run.fromVersion(), run.toVersion(), list, observer);
            execution.updatePhase(id, "saving_result", 90);
            execution.insertKnowledgeItems(id, result);
            execution.markCompleted(id, result);
            execution.advanceCompiledVersion(run.sessionId(), run.toVersion());
        } catch (Exception e) {
            execution.markFailed(id, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
