package site.kael.conversationcompiler.manager.compiler;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.kael.conversationcompiler.agent.CompilerAgentService;
import site.kael.conversationcompiler.repository.EventRepository;
import site.kael.conversationcompiler.repository.compiler.*;
import site.kael.conversationcompiler.repository.settings.KnowledgeSettingsRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
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
    private final ExecutorService executor;
    private final ScheduledExecutorService timeoutExecutor;
    private final long taskTimeoutSeconds;
    private final int maxTurnsPerRun;
    private final int maxCharsPerRun;

    /** Constructor used by unit tests and synchronous callers. */
    public CompileRunWorker(CompileRunRepository runs, CompileRunExecutionRepository execution,
                            EventRepository events, KnowledgeSettingsRepository settings,
                            CompilerAgentService agent) {
        this(runs, execution, events, settings, agent, 1, 600, 20, 200000, false);
    }

    @Autowired
    public CompileRunWorker(CompileRunRepository runs, CompileRunExecutionRepository execution,
                            EventRepository events, KnowledgeSettingsRepository settings,
                            CompilerAgentService agent,
                            @Value("${conversation-compiler.compiler.max-concurrent-runs:1}") int maxConcurrentRuns,
                            @Value("${conversation-compiler.compiler.task-timeout-seconds:600}") long taskTimeoutSeconds,
                            @Value("${conversation-compiler.compiler.max-turns-per-run:20}") int maxTurnsPerRun,
                            @Value("${conversation-compiler.compiler.max-chars-per-run:200000}") int maxCharsPerRun) {
        this(runs, execution, events, settings, agent, maxConcurrentRuns, taskTimeoutSeconds, maxTurnsPerRun, maxCharsPerRun, true);
    }

    private CompileRunWorker(CompileRunRepository runs, CompileRunExecutionRepository execution,
                             EventRepository events, KnowledgeSettingsRepository settings,
                             CompilerAgentService agent, int maxConcurrentRuns,
                             long taskTimeoutSeconds, int maxTurnsPerRun, int maxCharsPerRun, boolean background) {
        this.runs = runs; this.execution = execution; this.events = events;
        this.settings = settings; this.agent = agent;
        int concurrency = Math.max(1, maxConcurrentRuns);
        this.permits = new Semaphore(concurrency);
        this.taskTimeoutSeconds = Math.max(1, taskTimeoutSeconds);
        this.maxTurnsPerRun = Math.max(1, maxTurnsPerRun);
        this.maxCharsPerRun = Math.max(1, maxCharsPerRun);
        this.executor = background ? Executors.newFixedThreadPool(concurrency, daemonFactory("compiler-agent")) : null;
        this.timeoutExecutor = background ? Executors.newScheduledThreadPool(concurrency, daemonFactory("compiler-timeout")) : null;
    }

    @Scheduled(fixedDelayString="${conversation-compiler.compiler.scan-interval-seconds:60}000")
    public void scan() {
        int capacity = permits.availablePermits();
        if (capacity == 0) return;
        for (Long id : runs.findPendingIds(capacity)) {
            if (!permits.tryAcquire()) break;
            if (executor == null) executeClaimedWithPermit(id);
            else submitTimed(id);
        }
    }

    /** Synchronous entry point retained for tests and manual invocations. */
    public void execute(long id) {
        if (!permits.tryAcquire()) return;
        try {
            executeClaimed(id);
        } finally {
            permits.release();
        }
    }

    private void executeClaimedWithPermit(long id) {
        try {
            executeClaimed(id);
        } finally {
            permits.release();
        }
    }

    private void submitTimed(long id) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                executeClaimed(id);
            } finally {
                permits.release();
            }
            return null;
        });
        executor.execute(task);
        timeoutExecutor.schedule(() -> {
            if (!task.isDone()) {
                task.cancel(true);
                execution.markFailed(id, "compiler task timed out after " + taskTimeoutSeconds + " seconds");
            }
        }, taskTimeoutSeconds, TimeUnit.SECONDS);
    }

    private void executeClaimed(long id) {
        var run = runs.findById(id).orElseThrow();
        Map<String, Long> attempts = new HashMap<>();
        try {
            var setting = settings.find().orElseThrow(() -> new IllegalStateException("knowledge compile settings are not configured"));
            if (!execution.markRunning(id, "loading_events", setting.providerId(), setting.modelName(), setting.prompt())) return;
            // The range is fixed when the pending run is created. Events appended while the
            // agent is working are deliberately left for the next run.
            var sourceEvents = events.findBySessionIdAndVersionRange(run.sessionId(), run.fromVersion(), run.toVersion());
            var selection = site.kael.conversationcompiler.agent.CompleteTurnSelector.select(sourceEvents, maxTurnsPerRun, maxCharsPerRun);
            var list = selection.events();
            long boundedToVersion = run.fromVersion() + selection.lastIndex();
            var lastEventId = list.get(list.size() - 1).id();
            if (!runs.truncatePendingRun(id, boundedToVersion, lastEventId, list.size())) {
                throw new IllegalStateException("could not persist the bounded complete-turn snapshot");
            }
            execution.updatePhase(id, "agent_running", 20);
            var observer = new site.kael.conversationcompiler.agent.ModelFailoverRunner.AttemptObserver() {
                public void started(String candidate, int attempt) {
                    String[] parts = candidate.split("\\n", 2);
                    attempts.put(candidate + "#" + attempt,
                            execution.startAttempt(id, parts[0], parts.length > 1 ? parts[1] : ""));
                }
                public void finished(String candidate, int attempt, String status, Throwable error) {
                    Long attemptId = attempts.get(candidate + "#" + attempt);
                    if (attemptId != null) execution.finishAttempt(attemptId, status,
                            error == null ? null : error.getClass().getSimpleName(),
                            error == null ? null : error.getMessage());
                }
            };
            var result = setting.models().size() == 1
                    ? agent.compile(run.id(), setting.prompt(), setting.models().get(0).providerId(), setting.models().get(0).modelName(),
                    run.sessionId(), run.fromVersion(), boundedToVersion, list, observer)
                    : agent.compile(run.id(), setting.prompt(), setting.models(),
                    run.sessionId(), run.fromVersion(), boundedToVersion, list, observer);
            execution.updatePhase(id, "saving_result", 90);
            execution.insertKnowledgeItems(id, result);
            if (!execution.markCompleted(id, result)) return;
            execution.advanceCompiledVersion(run.sessionId(), boundedToVersion);
        } catch (Exception e) {
            execution.markFailed(id, rootMessage(e));
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private ThreadFactory daemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) executor.shutdownNow();
        if (timeoutExecutor != null) timeoutExecutor.shutdownNow();
    }
}
