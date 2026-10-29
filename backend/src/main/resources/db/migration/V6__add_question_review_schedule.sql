ALTER TABLE study_questions
    ADD COLUMN next_review_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_study_questions_review
    ON study_questions (study_block_id, next_review_at, status);
