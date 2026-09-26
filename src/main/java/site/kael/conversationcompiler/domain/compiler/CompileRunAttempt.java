package site.kael.conversationcompiler.domain.compiler;

public record CompileRunAttempt(long id, long compileRunId, int attemptNumber,
                                String providerId, String providerName, String modelName, String status,
                                String errorType, String errorMessage,
                                int toolCallCount, int mcpCallCount,
                                String startedAt, String finishedAt) {}
