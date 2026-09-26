package site.kael.conversationcompiler.repository.compiler;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import site.kael.conversationcompiler.common.FailureMessageSanitizer;
import site.kael.conversationcompiler.domain.compiler.CompileRunTraceEntry;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcCompileRunTraceRepository implements CompileRunTraceRepository {
    private static final int MAX_CONTENT_CHARS = 12_000;
    private final JdbcTemplate jdbc;
    public JdbcCompileRunTraceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public long append(long runId, Long attemptId, String eventType, String role, String title, String content, String status) {
        String now = Instant.now().toString();
        jdbc.update("INSERT INTO compile_run_trace_events(compile_run_id,attempt_id,event_type,role,title,content,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                runId, attemptId, eventType, role, title, sanitize(content), status, now, now);
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    @Override
    public void update(long traceId, String status, String content) {
        jdbc.update("UPDATE compile_run_trace_events SET status=?,content=?,updated_at=? WHERE id=?",
                status, sanitize(content), Instant.now().toString(), traceId);
    }

    @Override
    public List<CompileRunTraceEntry> findByRunId(long runId) {
        return jdbc.query("SELECT * FROM compile_run_trace_events WHERE compile_run_id=? ORDER BY id ASC",
                (r,n) -> new CompileRunTraceEntry(r.getLong("id"), r.getLong("compile_run_id"), nullableLong(r.getObject("attempt_id")),
                        r.getString("event_type"), r.getString("role"), r.getString("title"), r.getString("content"),
                        r.getString("status"), r.getString("created_at"), r.getString("updated_at")), runId);
    }

    private String sanitize(String value) {
        String safe = FailureMessageSanitizer.sanitize(value == null ? "" : value);
        if (safe.length() <= MAX_CONTENT_CHARS) return safe;
        return safe.substring(0, MAX_CONTENT_CHARS) + "\n…（内容过长，已截断）";
    }

    private Long nullableLong(Object value) { return value == null ? null : ((Number) value).longValue(); }
}
