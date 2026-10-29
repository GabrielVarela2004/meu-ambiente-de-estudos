CREATE TABLE study_question_attempts (
    id UUID PRIMARY KEY,
    study_block_id UUID NOT NULL REFERENCES study_blocks (id),
    question_id UUID NOT NULL REFERENCES study_questions (id),
    option_id UUID NOT NULL REFERENCES study_question_options (id),
    correct BOOLEAN NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_question_attempts_block_answered
    ON study_question_attempts (study_block_id, answered_at DESC);
