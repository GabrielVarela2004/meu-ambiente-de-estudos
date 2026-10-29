ALTER TABLE study_questions
    ADD COLUMN review_interval_days INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN correct_streak INTEGER NOT NULL DEFAULT 0;

ALTER TABLE study_questions
    ADD CONSTRAINT study_questions_review_interval_check CHECK (review_interval_days BETWEEN 1 AND 365),
    ADD CONSTRAINT study_questions_correct_streak_check CHECK (correct_streak >= 0);
