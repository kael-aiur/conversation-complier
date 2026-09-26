package site.kael.conversationcompiler.repository.compiler;
import site.kael.conversationcompiler.agent.CompileResultRequest;
public interface CompileRunExecutionRepository {
 long startAttempt(long runId, String providerId, String modelName);
 void finishAttempt(long attemptId, String status, String errorType, String errorMessage);
 boolean markRunning(long id, String phase, String providerId, String modelName, String prompt);
 void updatePhase(long id, String phase, int progress);
 boolean markCompleted(long id, CompileResultRequest result);
 void markFailed(long id, String error);
 void insertKnowledgeItems(long runId, CompileResultRequest result);
 void advanceCompiledVersion(String sessionId, long version);
}
