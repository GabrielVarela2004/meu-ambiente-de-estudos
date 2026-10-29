CREATE TABLE study_materials (
    id UUID PRIMARY KEY,
    study_block_id UUID NOT NULL REFERENCES study_blocks (id),
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT study_materials_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_study_materials_block_status
    ON study_materials (study_block_id, status);
