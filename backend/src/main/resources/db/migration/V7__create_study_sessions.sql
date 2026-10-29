CREATE TABLE study_sessions (
    id UUID PRIMARY KEY,
    study_block_id UUID NOT NULL REFERENCES study_blocks(id),
    planned_minutes INTEGER NOT NULL,
    notes VARCHAR(2000),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT study_sessions_planned_minutes_check CHECK (planned_minutes BETWEEN 5 AND 720)
);

CREATE INDEX idx_study_sessions_block_status
    ON study_sessions (study_block_id, status, updated_at DESC);
