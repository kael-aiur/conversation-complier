package site.kael.conversationcompiler.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import site.kael.conversationcompiler.common.FailureMessageSanitizer;
import site.kael.conversationcompiler.domain.ConversationEvent;

import java.util.List;

/** Human-readable historical records, not executable model tool messages or strict YAML. */
public final class ConversationTranscriptFormatter {
    public static final String HEADER = "# 对话记录\n";
    private static final String TRUNCATED = "\n[记录过长：部分内容已截断，无需补全。]";
    private static final ObjectMapper JSON = new ObjectMapper();

    private ConversationTranscriptFormatter() { }

    public static String format(List<ConversationEvent> events, int maxChars) {
        if (maxChars < 1) throw new IllegalArgumentException("maxChars must be positive");
        StringBuilder text = new StringBuilder(HEADER);
        for (ConversationEvent event : events) {
            text.append(formatEvent(event));
            if (text.length() > maxChars) break;
        }
        if (text.length() <= maxChars) return text.toString();
        if (maxChars <= TRUNCATED.length()) return TRUNCATED.substring(0, maxChars);
        return text.substring(0, maxChars - TRUNCATED.length()) + TRUNCATED;
    }

    public static String formatEvent(ConversationEvent event) {
        JsonNode payload;
        try { payload = JSON.readTree(event.payloadJson() == null ? "{}" : event.payloadJson()); }
        catch (Exception ignored) { payload = null; }
        JsonNode data = payload != null && payload.has("data") ? payload.get("data") : payload;
        String label;
        String content;
        switch (event.eventType()) {
            case "user_prompt" -> {
                label = "用户";
                content = first(data, "prompt", "content", "message", "text");
            }
            case "assistant_response", "assistant_message", "llm_response" -> {
                label = "Agent";
                content = first(data, "text", "content", "message", "response", "result");
            }
            case "tool_call" -> {
                label = "ToolCall";
                content = "工具：" + first(data, "tool_name", "tool", "name")
                        + "\n参数：" + first(data, "arguments", "input", "parameters");
            }
            case "tool_result" -> {
                label = "ToolResult";
                content = "工具：" + first(data, "tool_name", "tool", "name")
                        + "\n结果：" + first(data, "result", "output", "content", "text");
            }
            default -> {
                label = "Event(" + event.eventType() + ")";
                content = first(data, "text", "content", "message", "result", "prompt");
            }
        }
        if (payload == null) content = event.payloadJson() == null ? "（未采集到内容）" : event.payloadJson();
        else if (content.equals("（未采集到内容）") && data != null && !data.isEmpty()) content = display(data);
        content = FailureMessageSanitizer.sanitize(content);
        // Indentation keeps historical role-like text inside its original record block.
        return label + ": |-\n  " + content.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\n  ") + "\n\n";
    }

    private static String first(JsonNode node, String... keys) {
        if (node != null && node.isObject()) {
            for (String key : keys) {
                JsonNode value = node.get(key);
                if (value != null && !value.isNull()) return display(value);
            }
        } else if (node != null && !node.isNull()) return display(node);
        return "（未采集到内容）";
    }

    private static String display(JsonNode node) {
        return node.isTextual() ? node.asText() : node.toPrettyString();
    }
}
