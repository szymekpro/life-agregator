-- SQLite schema for fetcher-service
-- Single-user app: one row in fitatu_tokens (id = 1)

CREATE TABLE IF NOT EXISTS fitatu_tokens (
    id              INTEGER PRIMARY KEY CHECK (id = 1),
    bearer_token    TEXT,
    refresh_token   TEXT,
    fitatu_user_id  TEXT,
    session_json    TEXT NOT NULL,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);
