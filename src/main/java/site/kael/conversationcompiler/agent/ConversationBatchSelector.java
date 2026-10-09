package site.kael.conversationcompiler.agent;

import site.kael.conversationcompiler.domain.ConversationEvent;
import java.util.ArrayList;
import java.util.List;

/** Batches collected events by user-message boundaries without validating conversation completeness. */
public final class ConversationBatchSelector {
    private ConversationBatchSelector() { }

    public static Selection select(List<ConversationEvent> events, int maxTurns, int maxChars) {
        if (maxTurns < 1 || maxChars < 1) throw new IllegalArgumentException("batch limits must be positive");
        if (events.isEmpty()) throw new IllegalStateException("no collected conversation events available");
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        boolean hasConversation = false;
        for (int i = 0; i < events.size(); i++) {
            String type = events.get(i).eventType();
            if ("user_prompt".equals(type) && hasConversation) starts.add(i);
            if (!"session_start".equals(type) && !"session_end".equals(type)) hasConversation = true;
        }
        starts.add(events.size());
        int selectedEnd = 0;
        int turns = 0;
        long chars = ConversationTranscriptFormatter.HEADER.length();
        for (int round = 0; round < starts.size() - 1 && turns < maxTurns; round++) {
            int start = starts.get(round), end = starts.get(round + 1);
            long roundChars = 0;
            for (int i = start; i < end; i++) roundChars += ConversationTranscriptFormatter.formatEvent(events.get(i)).length();
            if (chars + roundChars <= maxChars) {
                selectedEnd = end;
                chars += roundChars;
                turns++;
                continue;
            }
            if (selectedEnd > 0) break; // leave the next whole round for a later batch
            // An oversized first round must not block the conversation forever.
            // Split at event boundaries; an oversized single event is explicitly truncated by the formatter.
            turns = 1;
            for (int i = start; i < end; i++) {
                long eventChars = ConversationTranscriptFormatter.formatEvent(events.get(i)).length();
                if (selectedEnd > 0 && chars + eventChars > maxChars) break;
                selectedEnd = i + 1;
                chars += eventChars;
                if (chars >= maxChars) break;
            }
            break;
        }
        return new Selection(List.copyOf(events.subList(0, selectedEnd)), turns, selectedEnd - 1,
                (int) Math.min(chars, maxChars));
    }

    public record Selection(List<ConversationEvent> events, int turns, int lastIndex, int estimatedChars) { }
}
