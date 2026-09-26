package site.kael.conversationcompiler.domain.compiler;

public record CompileRunTraceEntry(long id, long compileRunId, Long attemptId, String eventType,
                                  String role, String title, String content, String status,
                                  String createdAt, String updatedAt) {}
