package site.kael.conversationcompiler.repository;

import site.kael.conversationcompiler.domain.ConversationSummary;
import site.kael.conversationcompiler.domain.SessionMetadata;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository {
    void createOrUpdate(SessionMetadata metadata);
    void ensureExists(String sessionId, double eventTimestamp);
    void updateAfterEvent(String sessionId, double eventTimestamp, String title);
    Optional<ConversationSummary> findById(String sessionId);
    List<ConversationSummary> findAll(int limit, int offset);
    List<ConversationSummary> findIdleCandidates(double cutoffEpochSeconds, int limit);
}
