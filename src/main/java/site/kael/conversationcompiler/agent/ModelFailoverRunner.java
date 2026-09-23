package site.kael.conversationcompiler.agent;

import java.util.List;
import java.util.function.Function;
import java.util.function.LongConsumer;

/** Executes an agent attempt and applies the deliberately small, predictable failover policy. */
public class ModelFailoverRunner {
    public interface AttemptObserver {
        void started(String candidate, int attempt);
        void finished(String candidate, int attempt, String status, Throwable error);
    }

    private final LongConsumer sleeper;

    public ModelFailoverRunner() {
        this(millis -> {
            try { Thread.sleep(millis); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("retry interrupted", e); }
        });
    }

    ModelFailoverRunner(LongConsumer sleeper) {
        this.sleeper = sleeper;
    }

    public <T> T run(List<String> candidates, Function<String, T> invocation) {
        return run(candidates, invocation, new AttemptObserver() {
            public void started(String candidate, int attempt) { }
            public void finished(String candidate, int attempt, String status, Throwable error) { }
        });
    }

    public <T> T run(List<String> candidates, Function<String, T> invocation, AttemptObserver observer) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("no model candidates configured");
        }
        Throwable last = null;
        for (String candidate : candidates) {
            // A transient failure is retried once on the same model. Authentication and
            // authorization failures immediately move to the next configured model.
            for (int attempt = 1; attempt <= 2; attempt++) {
                observer.started(candidate, attempt);
                try {
                    T result = invocation.apply(candidate);
                    observer.finished(candidate, attempt, "completed", null);
                    return result;
                } catch (RuntimeException error) {
                    observer.finished(candidate, attempt, "failed", error);
                    last = error;
                    Failure failure = classify(error);
                    if (!failure.failover()) {
                        throw error;
                    }
                    if (!failure.retryable() || attempt == 2) {
                        break;
                    }
                    try {
                        sleeper.accept(failure.delayMillis());
                    } catch (RuntimeException interruptedOrSleepFailure) {
                        throw interruptedOrSleepFailure;
                    }
                }
            }
        }
        throw new IllegalStateException("all model candidates failed", last);
    }

    Failure classify(Throwable error) {
        String message = errorMessage(error).toLowerCase();
        if (containsAny(message, "429", "rate limit", "too many requests")) {
            return new Failure(true, true, retryAfterMillis(message, 1_000));
        }
        if (containsAny(message, "401", "403", "unauthorized", "forbidden")) {
            return new Failure(true, false, 0);
        }
        if (containsAny(message, "400", "bad request")) {
            return new Failure(false, false, 0);
        }
        if (containsAny(message, "408", "timeout", "timed out", "connect", "connection reset",
                "connection refused", "broken pipe", "502", "503", "504", "500")) {
            return new Failure(true, true, retryAfterMillis(message, 500));
        }
        return new Failure(false, false, 0);
    }

    private String errorMessage(Throwable error) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null) result.append(' ').append(current.getMessage());
        }
        return result.toString();
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private long retryAfterMillis(String message, long fallback) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("retry-after\\s*[:=]\\s*(\\d+)")
                .matcher(message);
        if (!matcher.find()) return fallback;
        try {
            // Keep a malformed or hostile upstream value from blocking the worker forever.
            return Math.min(Long.parseLong(matcher.group(1)) * 1_000L, 60_000L);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    record Failure(boolean failover, boolean retryable, long delayMillis) { }
}
