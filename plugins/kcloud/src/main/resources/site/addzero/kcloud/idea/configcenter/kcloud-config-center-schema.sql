CREATE TABLE IF NOT EXISTS config_entry (
    id TEXT PRIMARY KEY,
    key TEXT NOT NULL,
    namespace TEXT NOT NULL,
    domain TEXT NOT NULL,
    profile TEXT NOT NULL,
    value_type TEXT NOT NULL,
    storage_mode TEXT NOT NULL,
    cipher_text TEXT,
    plain_text TEXT,
    description TEXT,
    tags_json TEXT NOT NULL DEFAULT '[]',
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_config_entry_lookup
    ON config_entry(namespace, key, profile, storage_mode);

CREATE INDEX IF NOT EXISTS idx_config_entry_profile
    ON config_entry(profile, namespace, key);

CREATE TABLE IF NOT EXISTS config_bundle_meta (
    meta_key TEXT PRIMARY KEY,
    meta_value TEXT NOT NULL
);
