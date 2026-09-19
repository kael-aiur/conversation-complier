PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA busy_timeout = 5000;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS conversations (
    session_id TEXT PRIMARY KEY,
    source TEXT NOT NULL DEFAULT 'agent-trace',
    title TEXT,
    agent_name TEXT,
    command TEXT,
    workspace_id TEXT,
    first_event_at REAL,
    last_event_at REAL,
    event_count INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    compiled_version INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'active',
    last_compiled_at TEXT,
    compile_lock_until TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS conversation_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    event_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    event_timestamp REAL NOT NULL,
    received_at TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES conversations(session_id) ON DELETE CASCADE,
    UNIQUE(session_id, event_id)
);

CREATE TABLE IF NOT EXISTS compile_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    from_version INTEGER NOT NULL,
    to_version INTEGER NOT NULL,
    status TEXT NOT NULL,
    phase TEXT,
    progress INTEGER,
    started_at TEXT,
    finished_at TEXT,
    result_json TEXT,
    error_message TEXT,
    compiler_version TEXT NOT NULL DEFAULT 'mvp'
);

CREATE INDEX IF NOT EXISTS idx_events_session_id_id ON conversation_events(session_id, id);
CREATE INDEX IF NOT EXISTS idx_events_session_received ON conversation_events(session_id, received_at);
CREATE INDEX IF NOT EXISTS idx_conversations_compile_scan ON conversations(status, last_event_at, version, compiled_version);
CREATE INDEX IF NOT EXISTS idx_compile_runs_session ON compile_runs(session_id, started_at);
