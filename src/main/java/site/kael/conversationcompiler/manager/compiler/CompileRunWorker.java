package site.kael.conversationcompiler.manager.compiler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.kael.conversationcompiler.agent.CompilerAgentService;
import site.kael.conversationcompiler.repository.EventRepository;
import site.kael.conversationcompiler.repository.compiler.*;
import site.kael.conversationcompiler.repository.settings.KnowledgeSettingsRepository;

@Component
@ConditionalOnProperty(name={"conversation-compiler.compiler.enabled", "conversation-compiler.agent.enabled"}, havingValue="true")
public class CompileRunWorker {
    private final CompileRunRepository runs;
    private final CompileRunExecutionRepository execution;
    private final EventRepository events;
    private final KnowledgeSettingsRepository settings;
    private final CompilerAgentService agent;

    public CompileRunWorker(CompileRunRepository runs, CompileRunExecutionRepository execution,
                            EventRepository events, KnowledgeSettingsRepository settings,
                            CompilerAgentService agent) {
        this.runs = runs; this.execution = execution; this.events = events;
        this.settings = settings; this.agent = agent;
    }

    @Scheduled(fixedDelayString="${conversation-compiler.compiler.scan-interval-seconds:60}000")
    public void scan() { for (Long id : runs.findPendingIds(1)) execute(id); }

    public void execute(long id) {
        var run = runs.findById(id).orElseThrow();
        java.util.Map<String, Long> attempts = new java.util.HashMap<>();
        java.util.concurrent.atomic.AtomicInteger attemptSequence = new java.util.concurrent.atomic.AtomicInteger();
        try {
            var setting = settings.find().orElseThrow(() -> new IllegalStateException("knowledge compile settings are not configured"));
            if (!execution.markRunning(id, "loading_events", setting.providerId(), setting.modelName(), setting.prompt())) return;
            var list = events.findBySessionId(run.sessionId(), (int) Math.min(run.eventCount(), 5000), Math.max((int) run.fromVersion() - 1, 0));
            execution.markRunning(id, "agent_running", setting.providerId(), setting.modelName(), setting.prompt());
            var observer = new site.kael.conversationcompiler.agent.ModelFailoverRunner.AttemptObserver() {
                public void started(String candidate, int attempt) {
                    String[] parts = candidate.split("\\n", 2);
                    attempts.put(candidate + "#" + attempt, execution.startAttempt(id, attemptSequence.incrementAndGet(), parts[0], parts.length > 1 ? parts[1] : ""));
                }
                public void finished(String candidate, int attempt, String status, Throwable error) {
                    Long attemptId = attempts.get(candidate + "#" + attempt);
                    if (attemptId != null) execution.finishAttempt(attemptId, status, error == null ? null : error.getClass().getSimpleName(), error == null ? null : error.getMessage());
                }
            };
            var result = agent.compile(setting.prompt(), setting.providerId(), setting.modelName(), run.sessionId(), run.fromVersion(), run.toVersion(), list, observer);
            execution.insertKnowledgeItems(id, result);
            execution.markCompleted(id, result);
            execution.advanceCompiledVersion(run.sessionId(), run.toVersion());
        } catch (Exception e) {
            execution.markFailed(id, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
