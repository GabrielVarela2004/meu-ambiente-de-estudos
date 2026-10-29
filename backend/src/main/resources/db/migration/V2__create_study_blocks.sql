CREATE TABLE study_blocks (
    id UUID PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    learning_objective VARCHAR(2000),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT study_blocks_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_study_blocks_status ON study_blocks (status);
