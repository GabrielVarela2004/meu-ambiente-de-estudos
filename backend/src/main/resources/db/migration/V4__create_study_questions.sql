CREATE TABLE study_questions (
    id UUID PRIMARY KEY,
    study_block_id UUID NOT NULL REFERENCES study_blocks (id),
    study_material_id UUID REFERENCES study_materials (id),
    prompt VARCHAR(2000) NOT NULL,
    explanation VARCHAR(2000),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT study_questions_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE study_question_options (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES study_questions (id) ON DELETE CASCADE,
    option_text VARCHAR(500) NOT NULL,
    option_order INTEGER NOT NULL,
    correct BOOLEAN NOT NULL
);

CREATE INDEX idx_study_questions_block_status ON study_questions (study_block_id, status);
CREATE INDEX idx_study_question_options_question ON study_question_options (question_id, option_order);
