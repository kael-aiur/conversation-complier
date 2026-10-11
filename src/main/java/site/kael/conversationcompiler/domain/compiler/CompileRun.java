package site.kael.conversationcompiler.domain.compiler;

import java.util.List;

public record CompileRun(
        long id, String sessionId, String sessionTitle, long fromVersion, long toVersion,
        long fromEventId, long toEventId, long eventCount, CompileRunStatus status,
        String phase, int progress, CompileTriggerType triggerType, String summary,
        long knowledgeCount, String queuedAt, String startedAt, String finishedAt,
        long durationSeconds, String errorMessage, String compilerVersion,
        String createdAt, String updatedAt, List<String> warnings
) {
    public CompileRun {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
