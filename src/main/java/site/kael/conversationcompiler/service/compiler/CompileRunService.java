package site.kael.conversationcompiler.service.compiler;

import org.springframework.stereotype.Service;
import site.kael.conversationcompiler.common.ConflictException;
import site.kael.conversationcompiler.common.NotFoundException;
import site.kael.conversationcompiler.domain.compiler.*;
import site.kael.conversationcompiler.manager.ConversationQueryManager;
import site.kael.conversationcompiler.repository.compiler.CompileRunRepository;

import java.util.List;

@Service
public class CompileRunService {
    private final CompileRunRepository runs;
    private final ConversationQueryManager conversations;
    public CompileRunService(CompileRunRepository runs, ConversationQueryManager conversations) { this.runs = runs; this.conversations = conversations; }
    public List<CompileRun> list(String sessionId, String status, int limit, int offset) { return runs.findAll(sessionId, status, Math.min(Math.max(limit, 1), 200), Math.max(offset, 0)); }
    public CompileRun get(long id) { return runs.findById(id).orElseThrow(() -> new NotFoundException("compile run not found: " + id)); }
    public List<CompileRunKnowledgeItem> knowledge(long id) { get(id); return runs.findKnowledgeItems(id); }
    public long retry(long id) { var run = get(id); if (run.status() != site.kael.conversationcompiler.domain.compiler.CompileRunStatus.failed) throw new ConflictException("only failed compile runs can be retried"); return create(run.sessionId(), CompileTriggerType.retry); }
    public long create(String sessionId, CompileTriggerType trigger) {
        var conversation = conversations.get(sessionId);
        if (runs.hasActiveRun(sessionId)) throw new ConflictException("conversation already has an active compile run");
        long from = conversation.compiledVersion() + 1, to = conversation.version();
        if (from > to) throw new ConflictException("conversation has no uncompiled events");
        return runs.createPending(sessionId, from, to, 0, 0, to - from + 1, trigger);
    }
}
