package site.kael.conversationcompiler.manager;

import org.springframework.stereotype.Component;
import site.kael.conversationcompiler.common.NotFoundException;
import site.kael.conversationcompiler.domain.ConversationEvent;
import site.kael.conversationcompiler.domain.ConversationSummary;
import site.kael.conversationcompiler.repository.ConversationRepository;
import site.kael.conversationcompiler.repository.EventRepository;

import java.util.List;

@Component
public class ConversationQueryManager {
    private final ConversationRepository conversations;
    private final EventRepository events;
    public ConversationQueryManager(ConversationRepository conversations, EventRepository events) { this.conversations = conversations; this.events = events; }
    public List<ConversationSummary> list(int limit, int offset) { return conversations.findAll(safeLimit(limit), safeOffset(offset)); }
    public ConversationSummary get(String sessionId) { return conversations.findById(sessionId).orElseThrow(() -> new NotFoundException("session not found: " + sessionId)); }
    public List<ConversationEvent> events(String sessionId, int limit, int offset) { get(sessionId); return events.findBySessionId(sessionId, safeLimit(limit), safeOffset(offset)); }
    private int safeLimit(int value) { return value <= 0 ? 50 : Math.min(value, 500); }
    private int safeOffset(int value) { return Math.max(value, 0); }
}
