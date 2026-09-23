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
    provider_id TEXT,
    model_name TEXT,
    prompt_snapshot TEXT,
    from_event_id INTEGER,
    to_event_id INTEGER,
    event_count INTEGER NOT NULL DEFAULT 0,
    trigger_type TEXT NOT NULL DEFAULT 'scheduler',
    summary TEXT,
    knowledge_count INTEGER NOT NULL DEFAULT 0,
    queued_at TEXT,
    created_at TEXT,
    updated_at TEXT,
    compiler_version TEXT NOT NULL DEFAULT 'mvp'
);

CREATE INDEX IF NOT EXISTS idx_events_session_id_id ON conversation_events(session_id, id);
CREATE INDEX IF NOT EXISTS idx_events_session_received ON conversation_events(session_id, received_at);
CREATE INDEX IF NOT EXISTS idx_conversations_compile_scan ON conversations(status, last_event_at, version, compiled_version);
CREATE INDEX IF NOT EXISTS idx_compile_runs_session ON compile_runs(session_id, started_at);

CREATE TABLE IF NOT EXISTS model_providers (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    interface_type TEXT NOT NULL,
    base_url TEXT NOT NULL,
    api_key_ciphertext TEXT NOT NULL,
    api_key_fingerprint TEXT,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS provider_models (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_id TEXT NOT NULL,
    model_name TEXT NOT NULL,
    fetched_at TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (provider_id) REFERENCES model_providers(id) ON DELETE CASCADE,
    UNIQUE(provider_id, model_name)
);

CREATE TABLE IF NOT EXISTS knowledge_compile_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    provider_id TEXT,
    model_name TEXT,
    interval_minutes INTEGER NOT NULL DEFAULT 30,
    prompt TEXT NOT NULL DEFAULT '',
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (provider_id) REFERENCES model_providers(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS knowledge_compile_setting_models (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    setting_id INTEGER NOT NULL DEFAULT 1,
    provider_id TEXT NOT NULL,
    model_name TEXT NOT NULL,
    selection_order INTEGER NOT NULL,
    FOREIGN KEY (setting_id) REFERENCES knowledge_compile_settings(id) ON DELETE CASCADE,
    FOREIGN KEY (provider_id) REFERENCES model_providers(id) ON DELETE CASCADE,
    UNIQUE(setting_id, provider_id, model_name)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_compile_setting_models_order ON knowledge_compile_setting_models(setting_id, selection_order);

CREATE TABLE IF NOT EXISTS compile_run_knowledge_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    compile_run_id INTEGER NOT NULL,
    item_key TEXT NOT NULL,
    item_type TEXT NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    action TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'candidate',
    confidence REAL,
    content TEXT,
    wiki TEXT,
    slug TEXT,
    content_hash TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (compile_run_id) REFERENCES compile_runs(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_compile_run_items_run ON compile_run_knowledge_items(compile_run_id);

CREATE TABLE IF NOT EXISTS compile_run_attempts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    compile_run_id INTEGER NOT NULL,
    attempt_number INTEGER NOT NULL,
    provider_id TEXT,
    model_name TEXT,
    status TEXT NOT NULL,
    error_type TEXT,
    error_message TEXT,
    tool_call_count INTEGER NOT NULL DEFAULT 0,
    mcp_call_count INTEGER NOT NULL DEFAULT 0,
    started_at TEXT,
    finished_at TEXT,
    FOREIGN KEY (compile_run_id) REFERENCES compile_runs(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_compile_run_attempts_run ON compile_run_attempts(compile_run_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_compile_runs_one_active_per_session ON compile_runs(session_id) WHERE status IN ('pending','running');
