package site.kael.conversationcompiler.manager;

import org.springframework.stereotype.Component;
import site.kael.conversationcompiler.domain.SessionMetadata;
import site.kael.conversationcompiler.repository.ConversationRepository;

@Component
public class SessionManager {
    private final ConversationRepository conversations;
    public SessionManager(ConversationRepository conversations) { this.conversations = conversations; }
    public void register(SessionMetadata metadata) { conversations.createOrUpdate(metadata); }
}
