package site.kael.conversationcompiler.repository.compiler;

import site.kael.conversationcompiler.domain.compiler.*;
import java.util.List;
import java.util.Optional;

public interface CompileRunRepository {
    List<CompileRun> findAll(String sessionId, String status, int limit, int offset);
    Optional<CompileRun> findById(long id);
    List<CompileRunKnowledgeItem> findKnowledgeItems(long runId);
    boolean hasActiveRun(String sessionId);
    java.util.List<Long> findPendingIds(int limit);
    long createPending(String sessionId, long fromVersion, long toVersion, long fromEventId,
                       long toEventId, long eventCount, CompileTriggerType triggerType);
}
