-- PostgreSQL schema for WebServer
CREATE TABLE IF NOT EXISTS works (
    work_id TEXT PRIMARY KEY,
    model TEXT NOT NULL,
    serial TEXT NOT NULL,
    process TEXT NOT NULL,
    first_recorded_at TIMESTAMPTZ NOT NULL,
    last_recorded_at TIMESTAMPTZ NOT NULL,
    segment_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS segments (
    segment_id UUID PRIMARY KEY,
    segment_uuid UUID NOT NULL UNIQUE,
    work_id TEXT NOT NULL REFERENCES works(work_id) ON DELETE CASCADE,
    segment_index INTEGER NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    duration_sec INTEGER NULL,
    size_bytes BIGINT NULL,
    local_path TEXT NOT NULL,
    sha256 TEXT NULL,
    adls_path TEXT NULL,
    archived_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_segments_work_recorded ON segments(work_id, recorded_at);
