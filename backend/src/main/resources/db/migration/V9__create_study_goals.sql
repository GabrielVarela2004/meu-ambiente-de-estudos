CREATE TABLE study_goals (
    id UUID PRIMARY KEY,
    study_block_id UUID NOT NULL REFERENCES study_blocks(id),
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    target_units INTEGER NOT NULL,
    completed_units INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT study_goals_target_check CHECK (target_units BETWEEN 1 AND 100000),
    CONSTRAINT study_goals_completed_check CHECK (completed_units BETWEEN 0 AND target_units)
);

CREATE INDEX idx_study_goals_block_status
    ON study_goals (study_block_id, status, updated_at DESC);
