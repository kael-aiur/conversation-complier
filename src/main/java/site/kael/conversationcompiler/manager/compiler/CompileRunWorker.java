package site.kael.conversationcompiler.manager.compiler;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(CompileRunWorker.class);
    private final CompileRunRepository runs;
    private final CompileRunExecutionRepository execution;
    private final EventRepository events;
    private final KnowledgeSettingsRepository settings;
    private final CompilerAgentService agent;
    private final site.kael.conversationcompiler.repository.compiler.CompileRunTraceRepository trace;
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
        this(runs, execution, events, settings, agent, null, 1, 600, 20, 200000, false);
    }

    @Autowired
    public CompileRunWorker(CompileRunRepository runs, CompileRunExecutionRepository execution,
                            EventRepository events, KnowledgeSettingsRepository settings,
                            CompilerAgentService agent,
                            site.kael.conversationcompiler.repository.compiler.CompileRunTraceRepository trace,
                            @Value("${conversation-compiler.compiler.max-concurrent-runs:1}") int maxConcurrentRuns,
                            @Value("${conversation-compiler.compiler.task-timeout-seconds:600}") long taskTimeoutSeconds,
                            @Value("${conversation-compiler.compiler.max-turns-per-run:20}") int maxTurnsPerRun,
                            @Value("${conversation-compiler.compiler.max-chars-per-run:200000}") int maxCharsPerRun) {
        this(runs, execution, events, settings, agent, trace, maxConcurrentRuns, taskTimeoutSeconds, maxTurnsPerRun, maxCharsPerRun, true);
    }

    private CompileRunWorker(CompileRunRepository runs, CompileRunExecutionRepository execution,
                             EventRepository events, KnowledgeSettingsRepository settings,
                             CompilerAgentService agent,
                             site.kael.conversationcompiler.repository.compiler.CompileRunTraceRepository trace,
                             int maxConcurrentRuns, long taskTimeoutSeconds, int maxTurnsPerRun, int maxCharsPerRun, boolean background) {
        this.runs = runs; this.execution = execution; this.events = events;
        this.settings = settings; this.agent = agent; this.trace = trace;
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
            appendTrace(id, null, "status", "system", "正在读取整理范围", "固定当前事件快照并检查完整对话轮次。", "completed");
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
            appendTrace(id, null, "status", "system", "开始知识整理", "已选择 " + selection.turns() + " 个完整对话轮次，共 " + list.size() + " 条事件。", "completed");
            var attemptTraceIds = new HashMap<String, Long>();
            var activeAttemptId = new java.util.concurrent.atomic.AtomicLong(0L);
            var observer = new site.kael.conversationcompiler.agent.ModelFailoverRunner.AttemptObserver() {
                public void started(String candidate, int attempt) {
                    String[] parts = candidate.split("\\n", 2);
                    String key = candidate + "#" + attempt;
                    long attemptId = execution.startAttempt(id, parts[0], parts.length > 1 ? parts[1] : "");
                    attempts.put(key, attemptId);
                    activeAttemptId.set(attemptId);
                    attemptTraceIds.put(key, appendTrace(id, attemptId, "model_attempt", "system",
                            "尝试模型：" + parts[1], "供应商：" + parts[0] + "；第 " + attempt + " 次请求。", "running"));
                }
                public void finished(String candidate, int attempt, String status, Throwable error) {
                    Long attemptId = attempts.get(candidate + "#" + attempt);
                    if (attemptId != null) execution.finishAttempt(attemptId, status,
                            error == null ? null : error.getClass().getSimpleName(),
                            error == null ? null : error.getMessage());
                    Long traceId = attemptTraceIds.get(candidate + "#" + attempt);
                    if (traceId != null) updateTrace(traceId, status,
                            error == null ? "模型请求完成，正在继续整理。" : error.getMessage());
                    activeAttemptId.set(0L);
                }
                public long traceStarted(String eventType, String role, String title, String content) {
                    return appendTrace(id, activeAttemptId.get() == 0L ? null : activeAttemptId.get(), eventType, role, title, content, "running");
                }
                public void trace(String eventType, String role, String title, String content) {
                    appendTrace(id, activeAttemptId.get() == 0L ? null : activeAttemptId.get(), eventType, role, title, content, "completed");
                }
                public void traceFinished(long traceId, String status, String content) {
                    updateTrace(traceId, status, content);
                }
            };
            var result = setting.models().size() == 1
                    ? agent.compile(run.id(), setting.prompt(), setting.models().get(0).providerId(), setting.models().get(0).modelName(),
                    run.sessionId(), run.fromVersion(), boundedToVersion, list, observer)
                    : agent.compile(run.id(), setting.prompt(), setting.models(),
                    run.sessionId(), run.fromVersion(), boundedToVersion, list, observer);
            execution.updatePhase(id, "saving_result", 90);
            long savingTraceId = appendTrace(id, null, "status", "system", "保存整理结果", "模型已报告整理摘要与知识条目元数据。", "running");
            execution.insertKnowledgeItems(id, result);
            if (!execution.markCompleted(id, result)) return;
            execution.advanceCompiledVersion(run.sessionId(), boundedToVersion);
            updateTrace(savingTraceId, "completed", "知识条目已保存。");
            appendTrace(id, null, "status", "system", "整理完成", "本次整理已成功完成。", "completed");
        } catch (Exception e) {
            execution.markFailed(id, rootMessage(e));
            appendTrace(id, null, "error", "system", "整理失败", rootMessage(e), "failed");
        }
    }

    private long appendTrace(long runId, Long attemptId, String eventType, String role, String title, String content, String status) {
        if (trace == null) return 0L;
        try { return trace.append(runId, attemptId, eventType, role, title, content, status); }
        catch (RuntimeException error) {
            log.warn("Could not persist compile trace event for run {} ({}); continuing compilation", runId, error.getClass().getSimpleName());
            return 0L;
        }
    }

    private void updateTrace(long traceId, String status, String content) {
        if (trace == null || traceId <= 0) return;
        try { trace.update(traceId, status, content); }
        catch (RuntimeException error) {
            log.warn("Could not update compile trace event {} ({}); continuing compilation", traceId, error.getClass().getSimpleName());
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
