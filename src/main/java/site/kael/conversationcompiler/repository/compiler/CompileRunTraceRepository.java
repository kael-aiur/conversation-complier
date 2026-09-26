package site.kael.conversationcompiler.repository.compiler;

import site.kael.conversationcompiler.domain.compiler.CompileRunTraceEntry;
import java.util.List;

public interface CompileRunTraceRepository {
    long append(long runId, Long attemptId, String eventType, String role, String title, String content, String status);
    void update(long traceId, String status, String content);
    List<CompileRunTraceEntry> findByRunId(long runId);
}
