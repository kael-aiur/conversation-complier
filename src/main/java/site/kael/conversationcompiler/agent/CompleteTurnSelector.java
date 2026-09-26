package site.kael.conversationcompiler.agent;

import site.kael.conversationcompiler.domain.ConversationEvent;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Selects only a bounded prefix ending at a complete assistant answer. */
public final class CompleteTurnSelector {
    private static final ObjectMapper JSON = new ObjectMapper();
    private CompleteTurnSelector() {}

    public static Selection select(List<ConversationEvent> events, int maxTurns, int maxChars) {
        if (maxChars < 1) throw new IllegalArgumentException("maxChars must be positive");
        if (maxTurns < 1) throw new IllegalArgumentException("maxTurns must be positive");
        int turns = 0;
        int anonymousToolCalls = 0;
        Set<String> pendingToolIds = new HashSet<>();
        int safeEnd = -1;
        int lastTurnStart = -1;
        int selectedChars = 0;
        int inputChars = 0;
        boolean inTurn = false;
        for (int i = 0; i < events.size(); i++) {
            inputChars += eventChars(events.get(i));
            String type = events.get(i).eventType();
            if ("user_prompt".equals(type)) {
                if (turns >= maxTurns || inTurn) break;
                inTurn = true;
                lastTurnStart = i;
            } else if (inTurn && "tool_call".equals(type)) {
                String callId = toolCallId(events.get(i).payloadJson());
                if (callId == null) anonymousToolCalls++; else pendingToolIds.add(callId);
            } else if (inTurn && "tool_result".equals(type)) {
                String callId = toolCallId(events.get(i).payloadJson());
                if (callId != null) pendingToolIds.remove(callId);
                else if (anonymousToolCalls > 0) anonymousToolCalls--;
                else if (pendingToolIds.size() == 1) pendingToolIds.clear();
            } else if (inTurn && ("assistant_response".equals(type) || "assistant_message".equals(type)) && anonymousToolCalls == 0 && pendingToolIds.isEmpty()) {
                if (inputChars > maxChars) break;
                selectedChars = inputChars;
                turns++;
                safeEnd = i;
                inTurn = false;
            }
        }
        if (safeEnd < 0) throw new IllegalStateException("no complete conversation turn found; waiting for an assistant answer and all tool results");
        return new Selection(List.copyOf(events.subList(0, safeEnd + 1)), turns, safeEnd, selectedChars);
    }

    private static int eventChars(ConversationEvent event) {
        return event.eventType().length() + event.payloadJson().length() + 128;
    }

    private static String toolCallId(String payload) {
        try {
            JsonNode root = JSON.readTree(payload);
            return findCallId(root);
        } catch (Exception ignored) { return null; }
    }

    private static String findCallId(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            for (String key : List.of("tool_call_id", "toolCallId", "call_id", "callId")) {
                JsonNode value = node.get(key);
                if (value != null && value.isValueNode() && !value.asText().isBlank()) return value.asText();
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                String found = findCallId(fields.next().getValue());
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findCallId(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    public record Selection(List<ConversationEvent> events, int turns, int lastIndex, int estimatedChars) {}
}
