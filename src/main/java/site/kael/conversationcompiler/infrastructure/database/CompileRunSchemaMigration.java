package site.kael.conversationcompiler.infrastructure.database;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class CompileRunSchemaMigration {
    private final JdbcTemplate jdbc;
    public CompileRunSchemaMigration(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @PostConstruct
    public void migrate() {
        addColumn("compile_runs", "from_event_id", "INTEGER"); addColumn("compile_runs", "to_event_id", "INTEGER");
        addColumn("compile_runs", "event_count", "INTEGER NOT NULL DEFAULT 0"); addColumn("compile_runs", "trigger_type", "TEXT NOT NULL DEFAULT 'scheduler'");
        addColumn("compile_runs", "summary", "TEXT"); addColumn("compile_runs", "knowledge_count", "INTEGER NOT NULL DEFAULT 0");
        addColumn("compile_runs", "queued_at", "TEXT"); addColumn("compile_runs", "created_at", "TEXT"); addColumn("compile_runs", "updated_at", "TEXT");
        jdbc.execute("CREATE TABLE IF NOT EXISTS compile_run_knowledge_items (id INTEGER PRIMARY KEY AUTOINCREMENT, compile_run_id INTEGER NOT NULL, item_key TEXT NOT NULL, item_type TEXT NOT NULL, title TEXT NOT NULL, summary TEXT NOT NULL, action TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'candidate', confidence REAL, content TEXT, created_at TEXT NOT NULL, FOREIGN KEY (compile_run_id) REFERENCES compile_runs(id) ON DELETE CASCADE)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_compile_run_items_run ON compile_run_knowledge_items(compile_run_id)");
    }
    private void addColumn(String table, String column, String definition) { boolean exists = jdbc.queryForList("PRAGMA table_info(" + table + ")").stream().anyMatch(row -> column.equals(row.get("name"))); if (!exists) jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition); }
}
