package site.kael.conversationcompiler.common;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Preserve a bounded, redacted cause chain without exposing request bodies or stack traces. */
public final class FailureDetails {
    private static final int MAX_CHARS = 6000;
    private FailureDetails() { }

    public static String describe(Throwable error) {
        if (error == null) return null;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        StringBuilder details = new StringBuilder();
        for (Throwable cause = error; cause != null && visited.size() < 8 && visited.add(cause); cause = cause.getCause()) {
            if (!details.isEmpty()) details.append(" -> ");
            details.append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) details.append(": ").append(cause.getMessage());
        }
        String safe = FailureMessageSanitizer.sanitize(details.toString());
        return safe.length() <= MAX_CHARS ? safe : safe.substring(0, MAX_CHARS) + "…";
    }
}
