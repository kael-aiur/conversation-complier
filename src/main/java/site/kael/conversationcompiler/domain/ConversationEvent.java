package site.kael.conversationcompiler.domain;

public record ConversationEvent(
        long id,
        String sessionId,
        String eventId,
        String eventType,
        double eventTimestamp,
        String receivedAt,
        String payloadJson
) {}
