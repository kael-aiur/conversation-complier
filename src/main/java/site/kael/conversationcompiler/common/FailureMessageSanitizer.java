package site.kael.conversationcompiler.common;

import java.util.regex.Pattern;

/** Redacts common credential formats before provider failures are persisted or exposed. */
public final class FailureMessageSanitizer {
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern HEADER_SECRET = Pattern.compile(
            "(?i)\\b(x-api-key|x-mcp-key|cf-access-client-id|cf-access-client-secret|api[_-]?key|access[_-]?token|token|secret)\\b(\\s*[:=]\\s*)([^\\s,;]+)");
    private static final Pattern URL_CREDENTIALS = Pattern.compile("(https?://)[^/@\\s:]+:[^/@\\s]+@");

    private FailureMessageSanitizer() { }

    public static String sanitize(String message) {
        if (message == null || message.isBlank()) return message;
        String value = BEARER.matcher(message).replaceAll("Bearer [redacted]");
        value = HEADER_SECRET.matcher(value).replaceAll("$1$2[redacted]");
        return URL_CREDENTIALS.matcher(value).replaceAll("$1[redacted]@");
    }
}
