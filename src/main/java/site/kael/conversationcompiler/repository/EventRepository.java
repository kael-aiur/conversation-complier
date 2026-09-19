package site.kael.conversationcompiler.repository;

import site.kael.conversationcompiler.domain.ConversationEvent;

import java.util.List;

public interface EventRepository {
    boolean insert(String sessionId, String eventId, String eventType, double timestamp, String receivedAt, String payloadJson);
    List<ConversationEvent> findBySessionId(String sessionId, int limit, int offset);
}
