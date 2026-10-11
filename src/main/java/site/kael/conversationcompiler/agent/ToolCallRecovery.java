package site.kael.conversationcompiler.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import site.kael.conversationcompiler.common.FailureDetails;
import site.kael.conversationcompiler.common.FailureMessageSanitizer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Returns tool failures to the model without losing the tool-call/result pairing. */
final class ToolCallRecovery {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FAILURE_MARKER = "toolCallFailed";

    private ToolCallRecovery() { }

    static ToolCallback wrap(ToolCallback delegate, ModelFailoverRunner.AttemptObserver observer) {
        String name = delegate.getToolDefinition().name();
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
            @Override public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
            @Override public String call(String arguments) { return invoke(arguments, null); }
            @Override public String call(String arguments, ToolContext context) { return invoke(arguments, context); }

            private String invoke(String arguments, ToolContext context) {
                // Cancellation is not a tool error: do not let the agent keep writing after its deadline.
                checkCancellation(null);
                long traceId = observer.traceStarted("tool_execution", "tool", "执行工具：" + name, arguments);
                String result;
                try {
                    result = context == null ? delegate.call(arguments) : delegate.call(arguments, context);
                    checkCancellation(null);
                    if ("compile_result".equals(name) && stringResult(result).startsWith("compile_result rejected:")) {
                        throw new IllegalArgumentException(stringResult(result));
                    }
                } catch (RuntimeException error) {
                    try {
                        checkCancellation(error);
                    } catch (CancellationException cancelled) {
                        observer.traceFinished(traceId, "failed", "工具执行已取消，不再继续模型纠错；远端写入是否发生需另行核对。");
                        throw cancelled;
                    }
                    String failure = failure(name, error);
                    // Put the outcome first, so long arguments cannot hide it in a truncated trace.
                    observer.traceFinished(traceId, "failed", "工具调用失败（已返回给模型）：\n" + failure + "\n\n参数：\n" + arguments);
                    return failure;
                }
                observer.traceFinished(traceId, "completed", "参数：\n" + arguments + "\n\n结果：\n" + result);
                if ("okf_write_concept".equals(name)) {
                    // This is an independent acknowledgement, not a fabricated compile_result item.
                    // A returned response is not proof of success when a server reports errors as text.
                    Map<String, Object> acknowledgement = new LinkedHashMap<>();
                    acknowledgement.put("outcome", "tool_returned");
                    try {
                        var parameters = JSON.readTree(arguments);
                        acknowledgement.put("wiki", parameters.path("wiki").asText("main"));
                        acknowledgement.put("slug", parameters.path("slug").asText(""));
                    } catch (Exception ignored) {
                        acknowledgement.put("target", "unavailable");
                    }
                    acknowledgement.put("argumentChars", arguments == null ? 0 : arguments.length());
                    String safeResult = FailureMessageSanitizer.sanitize(result == null ? "" : result);
                    acknowledgement.put("responseChars", safeResult.length());
                    acknowledgement.put("responsePreviewTruncated", safeResult.length() > 4000);
                    acknowledgement.put("response", safeResult.substring(0, Math.min(safeResult.length(), 4000)));
                    observer.trace("knowledge_write", "tool", "知识库写入响应（请核对远端结果）", json(acknowledgement));
                }
                return result;
            }
        };
    }

    static String failure(String tool, RuntimeException error) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put(FAILURE_MARKER, true);
        payload.put("tool", tool);
        payload.put("errorType", error.getClass().getSimpleName());
        payload.put("message", FailureDetails.describe(error));
        payload.put("sideEffects", "unknown");
        payload.put("guidance", "工具调用失败，不得声称已成功。根据错误检查工具名、参数、权限或服务状态，自行纠正后再调用。"
                + "写入可能已经发生：重试写入前先搜索或读取核对，避免重复覆盖；不要原样无限重试。"
                + "若无法恢复，在 compile_result 中如实报告失败或警告，不得伪造知识条目。");
        return json(payload);
    }

    private static String stringResult(String result) {
        if (result == null) return "";
        try {
            var parsed = JSON.readTree(result);
            if (parsed != null && parsed.isTextual()) return parsed.asText();
        } catch (Exception ignored) { }
        return result;
    }

    static boolean isFailure(String result) {
        if (result == null) return false;
        try { return JSON.readTree(result).path(FAILURE_MARKER).asBoolean(false); }
        catch (Exception ignored) { return false; }
    }

    static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception ignored) { return "{\"toolCallFailed\":true,\"message\":\"Could not serialize tool diagnostics\"}"; }
    }

    private static void checkCancellation(RuntimeException error) {
        boolean cancelled = Thread.currentThread().isInterrupted();
        var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = error; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof CancellationException || cause instanceof InterruptedException) cancelled = true;
        }
        if (cancelled) {
            Thread.currentThread().interrupt();
            throw new CancellationException("compiler tool execution cancelled");
        }
    }
}
