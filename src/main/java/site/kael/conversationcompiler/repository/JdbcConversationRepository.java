package site.kael.conversationcompiler.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import site.kael.conversationcompiler.domain.ConversationSummary;
import site.kael.conversationcompiler.domain.SessionMetadata;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcConversationRepository implements ConversationRepository {
    private final JdbcTemplate jdbc;

    public JdbcConversationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void createOrUpdate(SessionMetadata m) {
        String now = Instant.now().toString();
        jdbc.update("""
                INSERT INTO conversations(session_id, source, agent_name, command, workspace_id, first_event_at, created_at, updated_at)
                VALUES (?, 'agent-trace', ?, ?, ?, ?, ?, ?)
                ON CONFLICT(session_id) DO UPDATE SET
                  agent_name=COALESCE(NULLIF(excluded.agent_name, ''), conversations.agent_name),
                  command=COALESCE(NULLIF(excluded.command, ''), conversations.command),
                  workspace_id=COALESCE(NULLIF(excluded.workspace_id, ''), conversations.workspace_id),
                  updated_at=excluded.updated_at
                """, m.sessionId(), m.agentName(), m.command(), m.workspaceId(), m.startedAt(), now, now);
    }

    @Override
    public void ensureExists(String sessionId, double eventTimestamp) {
        String now = Instant.now().toString();
        jdbc.update("""
                INSERT INTO conversations(session_id, source, first_event_at, created_at, updated_at)
                VALUES (?, 'agent-trace', ?, ?, ?)
                ON CONFLICT(session_id) DO NOTHING
                """, sessionId, eventTimestamp, now, now);
    }

    @Override
    public void updateAfterEvent(String sessionId, double eventTimestamp, String title) {
        String now = Instant.now().toString();
        jdbc.update("""
                UPDATE conversations SET
                  title=COALESCE(NULLIF(title, ''), NULLIF(?, '')),
                  first_event_at=COALESCE(first_event_at, ?),
                  last_event_at=?, event_count=event_count+1, version=version+1,
                  status=CASE WHEN status='compiling' THEN status ELSE 'active' END,
                  updated_at=?
                WHERE session_id=?
                """, title, eventTimestamp, eventTimestamp, now, sessionId);
    }

    @Override
    public Optional<ConversationSummary> findById(String sessionId) {
        List<ConversationSummary> result = jdbc.query("SELECT * FROM conversations WHERE session_id=?", mapper(), sessionId);
        return result.stream().findFirst();
    }

    @Override
    public List<ConversationSummary> findIdleCandidates(double cutoffEpochSeconds, int limit) {
        return jdbc.query("SELECT * FROM conversations WHERE status IN ('active','stale') AND version > compiled_version AND last_event_at <= ? ORDER BY last_event_at ASC LIMIT ?", mapper(), cutoffEpochSeconds, limit);
    }

    @Override
    public List<ConversationSummary> findAll(int limit, int offset) {
        return jdbc.query("SELECT * FROM conversations ORDER BY COALESCE(last_event_at, first_event_at, 0) DESC LIMIT ? OFFSET ?", mapper(), limit, offset);
    }

    private org.springframework.jdbc.core.RowMapper<ConversationSummary> mapper() {
        return (rs, rowNum) -> new ConversationSummary(
                rs.getString("session_id"), rs.getString("source"), rs.getString("title"),
                rs.getString("agent_name"), rs.getString("command"), rs.getString("workspace_id"),
                (Double) rs.getObject("first_event_at"), (Double) rs.getObject("last_event_at"),
                rs.getLong("event_count"), rs.getLong("version"), rs.getLong("compiled_version"),
                rs.getString("status"), rs.getString("last_compiled_at"), rs.getString("created_at"), rs.getString("updated_at"));
    }
}
