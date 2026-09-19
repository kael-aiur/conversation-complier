package site.kael.conversationcompiler.service;

import org.springframework.stereotype.Service;
import site.kael.conversationcompiler.domain.ConversationEvent;
import site.kael.conversationcompiler.domain.ConversationSummary;
import site.kael.conversationcompiler.manager.ConversationQueryManager;

import java.util.List;

@Service
public class ConversationService {
    private final ConversationQueryManager manager;
    public ConversationService(ConversationQueryManager manager) { this.manager = manager; }
    public List<ConversationSummary> list(int limit, int offset) { return manager.list(limit, offset); }
    public ConversationSummary get(String id) { return manager.get(id); }
    public List<ConversationEvent> events(String id, int limit, int offset) { return manager.events(id, limit, offset); }
}
