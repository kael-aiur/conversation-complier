package site.kael.conversationcompiler.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import site.kael.conversationcompiler.agent.CompileResultRequest;
import site.kael.conversationcompiler.domain.SessionMetadata;
import site.kael.conversationcompiler.repository.compiler.CompileRunExecutionRepository;
import site.kael.conversationcompiler.repository.compiler.CompileRunRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:file:repository-test?mode=memory&cache=shared",
        "spring.sql.init.mode=always"
})
class RepositoryIntegrationTest {
    @Autowired ConversationRepository conversations;
    @Autowired EventRepository events;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompileRunExecutionRepository compileExecution;
    @Autowired CompileRunRepository compileRuns;

    @Test
    void persistsConversationAndEventsForQueries() {
        conversations.createOrUpdate(new SessionMetadata("repo-session", 1d, null, "Codex", "cmd", "workspace", ""));
        assertThat(events.insert("repo-session", "evt-1", "user_prompt", 2d, "now", "{\"event_type\":\"user_prompt\"}")).isTrue();
        conversations.updateAfterEvent("repo-session", 2d, "A title");
        assertThat(conversations.findById("repo-session")).isPresent();
        assertThat(events.findBySessionId("repo-session", 10, 0)).hasSize(1);
        assertThat(events.findBySessionIdAndVersionRange("repo-session", 1, 1)).hasSize(1);
        assertThat(events.findBySessionIdAndVersionRange("repo-session", 2, 2)).isEmpty();
        assertThat(events.insert("repo-session", "evt-1", "user_prompt", 2d, "now", "{}")).isFalse();
    }

    @Test
    void persistsCompileResultAndAllowsDuplicateKnowledgeItems() {
        String sessionId = "compile-session-" + System.nanoTime();
        conversations.createOrUpdate(new SessionMetadata(sessionId, 1d, null, "Codex", "cmd", "workspace", ""));
        String now = java.time.Instant.now().toString();
        jdbc.update("INSERT INTO compile_runs(session_id,from_version,to_version,status,phase,progress,trigger_type,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                sessionId, 1, 1, "running", "agent_running", 20, "manual", now, now);
        long runId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        var item = new CompileResultRequest.KnowledgeResultItem("key", "decision", "Title", "Summary", "create", "candidate", 0.9, "wiki", "slug", "hash");
        var result = new CompileResultRequest("summary", java.util.List.of(item), java.util.List.of());

        long attemptId = compileExecution.startAttempt(runId, "provider-id", "model-name");
        compileExecution.finishAttempt(attemptId, "failed", "HttpError", "HTTP 401 Authorization: Bearer sensitive-token");
        var attempts = compileRuns.findAttempts(runId);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).modelName()).isEqualTo("model-name");
        assertThat(attempts.get(0).errorMessage()).contains("Bearer [redacted]").doesNotContain("sensitive-token");

        compileExecution.insertKnowledgeItems(runId, result);
        compileExecution.insertKnowledgeItems(runId, result);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM compile_run_knowledge_items WHERE compile_run_id=?", Integer.class, runId)).isEqualTo(2);
        assertThat(compileExecution.markCompleted(runId, result)).isTrue();
        assertThat(compileExecution.markCompleted(runId, result)).isFalse();
        assertThat(jdbc.queryForObject("SELECT summary FROM compile_runs WHERE id=?", String.class, runId)).isEqualTo("summary");

        compileExecution.advanceCompiledVersion(sessionId, 1);
        assertThat(conversations.findById(sessionId).orElseThrow().compiledVersion()).isEqualTo(1);
    }

    @Test
    void retriesFailedRunInPlaceAndKeepsAttemptHistory() {
        String sessionId = "retry-session-" + System.nanoTime();
        conversations.createOrUpdate(new SessionMetadata(sessionId, 1d, null, "Codex", "cmd", "workspace", ""));
        String now = java.time.Instant.now().toString();
        jdbc.update("INSERT INTO compile_runs(session_id,from_version,to_version,status,phase,progress,trigger_type,error_message,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                sessionId, 1, 1, "failed", "failed", 20, "manual", "previous error", now, now);
        long runId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        long firstAttemptId = compileExecution.startAttempt(runId, "provider", "model-a");
        compileExecution.finishAttempt(firstAttemptId, "failed", "HttpError", "HTTP 429");

        assertThat(compileRuns.requeueFailed(runId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM compile_runs WHERE session_id=?", Integer.class, sessionId)).isEqualTo(1);
        assertThat(compileRuns.findById(runId).orElseThrow().status()).isEqualTo(site.kael.conversationcompiler.domain.compiler.CompileRunStatus.pending);
        assertThat(compileRuns.findById(runId).orElseThrow().errorMessage()).isNull();
        assertThat(compileRuns.findAttempts(runId)).hasSize(1);

        long secondAttemptId = compileExecution.startAttempt(runId, "provider", "model-b");
        compileExecution.finishAttempt(secondAttemptId, "completed", null, null);
        assertThat(compileRuns.findAttempts(runId)).extracting("attemptNumber").containsExactly(1, 2);
    }

}
