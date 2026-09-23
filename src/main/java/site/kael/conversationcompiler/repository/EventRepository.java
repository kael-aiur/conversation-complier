package site.kael.conversationcompiler.repository;

import site.kael.conversationcompiler.domain.ConversationEvent;

import java.util.List;

public interface EventRepository {
    boolean insert(String sessionId, String eventId, String eventType, double timestamp, String receivedAt, String payloadJson);
    List<ConversationEvent> findBySessionId(String sessionId, int limit, int offset);

    /** Returns the immutable ordinal snapshot represented by a compile run. */
    default List<ConversationEvent> findBySessionIdAndVersionRange(String sessionId, long fromVersion, long toVersion) {
        if (toVersion < fromVersion) return List.of();
        long count = toVersion - fromVersion + 1;
        if (count > Integer.MAX_VALUE || fromVersion < 1 || fromVersion > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("event version range is outside supported bounds");
        }
        return findBySessionId(sessionId, (int) count, (int) (fromVersion - 1));
    }
}
