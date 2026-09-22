package site.kael.conversationcompiler.repository.compiler;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import site.kael.conversationcompiler.domain.compiler.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCompileRunRepository implements CompileRunRepository {
    private final JdbcTemplate jdbc;
    public JdbcCompileRunRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<CompileRun> findAll(String sessionId, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT r.*, c.title session_title FROM compile_runs r LEFT JOIN conversations c ON c.session_id=r.session_id WHERE 1=1");
        if (sessionId != null && !sessionId.isBlank()) sql.append(" AND r.session_id=?");
        if (status != null && !status.isBlank()) sql.append(" AND r.status=?");
        sql.append(" ORDER BY r.id DESC LIMIT ? OFFSET ?");
        List<Object> args = new java.util.ArrayList<>();
        if (sessionId != null && !sessionId.isBlank()) args.add(sessionId);
        if (status != null && !status.isBlank()) args.add(status);
        args.add(limit); args.add(offset);
        return jdbc.query(sql.toString(), mapper(), args.toArray());
    }

    @Override
    public Optional<CompileRun> findById(long id) {
        return jdbc.query("SELECT r.*, c.title session_title FROM compile_runs r LEFT JOIN conversations c ON c.session_id=r.session_id WHERE r.id=?", mapper(), id).stream().findFirst();
    }

    @Override
    public List<CompileRunKnowledgeItem> findKnowledgeItems(long runId) {
        return jdbc.query("SELECT * FROM compile_run_knowledge_items WHERE compile_run_id=? ORDER BY id ASC", (r, n) ->
                new CompileRunKnowledgeItem(r.getLong("id"), r.getLong("compile_run_id"), r.getString("item_key"),
                        r.getString("item_type"), r.getString("title"), r.getString("summary"), r.getString("action"),
                        r.getString("status"), (Double) r.getObject("confidence"), r.getString("content"), r.getString("wiki"), r.getString("slug"), r.getString("content_hash"), r.getString("created_at")), runId);
    }

    @Override
    public boolean hasActiveRun(String sessionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM compile_runs WHERE session_id=? AND status IN ('pending','running')", Integer.class, sessionId) > 0;
    }

    @Override
    public List<Long> findPendingIds(int limit) { return jdbc.queryForList("SELECT id FROM compile_runs WHERE status='pending' ORDER BY id ASC LIMIT ?", Long.class, limit); }

    @Override
    public long createPending(String sessionId, long fromVersion, long toVersion, long fromEventId,
                              long toEventId, long eventCount, CompileTriggerType triggerType) {
        String now = Instant.now().toString();
        jdbc.update("""
                INSERT INTO compile_runs(session_id, from_version, to_version, from_event_id, to_event_id,
                    event_count, status, phase, progress, trigger_type, queued_at, compiler_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'pending', 'queued', 0, ?, ?, 'mvp', ?, ?)
                """, sessionId, fromVersion, toVersion, fromEventId, toEventId, eventCount,
                triggerType.name(), now, now, now);
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    private org.springframework.jdbc.core.RowMapper<CompileRun> mapper() {
        return (r, n) -> {
            String started = r.getString("started_at");
            String finished = r.getString("finished_at");
            long duration = 0;
            if (started != null && finished != null) {
                try { duration = Duration.between(Instant.parse(started), Instant.parse(finished)).toSeconds(); } catch (Exception ignored) { }
            }
            return new CompileRun(r.getLong("id"), r.getString("session_id"), r.getString("session_title"),
                    r.getLong("from_version"), r.getLong("to_version"), nullableLong(r, "from_event_id"),
                    nullableLong(r, "to_event_id"), nullableLong(r, "event_count"), CompileRunStatus.valueOf(r.getString("status")),
                    r.getString("phase"), r.getInt("progress"), CompileTriggerType.valueOf(r.getString("trigger_type")),
                    r.getString("summary"), nullableLong(r, "knowledge_count"), r.getString("queued_at"), started,
                    finished, duration, r.getString("error_message"), r.getString("compiler_version"),
                    r.getString("created_at"), r.getString("updated_at"));
        };
    }
    private long nullableLong(java.sql.ResultSet r, String column) throws java.sql.SQLException { Long value = (Long) r.getObject(column); return value == null ? 0 : value; }
}
