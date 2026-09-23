package site.kael.conversationcompiler.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import site.kael.conversationcompiler.agent.CompileResultRequest;
import site.kael.conversationcompiler.domain.SessionMetadata;
import site.kael.conversationcompiler.repository.compiler.CompileRunExecutionRepository;

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

        compileExecution.insertKnowledgeItems(runId, result);
        compileExecution.insertKnowledgeItems(runId, result);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM compile_run_knowledge_items WHERE compile_run_id=?", Integer.class, runId)).isEqualTo(2);
        assertThat(compileExecution.markCompleted(runId, result)).isTrue();
        assertThat(compileExecution.markCompleted(runId, result)).isFalse();
        assertThat(jdbc.queryForObject("SELECT summary FROM compile_runs WHERE id=?", String.class, runId)).isEqualTo("summary");

        compileExecution.advanceCompiledVersion(sessionId, 1);
        assertThat(conversations.findById(sessionId).orElseThrow().compiledVersion()).isEqualTo(1);
    }
}
