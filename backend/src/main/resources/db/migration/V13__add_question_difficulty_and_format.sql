ALTER TABLE study_questions
    ADD COLUMN difficulty VARCHAR(20) NOT NULL DEFAULT 'INTERMEDIATE',
    ADD COLUMN question_format VARCHAR(25) NOT NULL DEFAULT 'MULTIPLE_CHOICE',
    ADD COLUMN expected_answer VARCHAR(4000),
    ADD CONSTRAINT study_questions_difficulty_check CHECK (difficulty IN ('EASY', 'INTERMEDIATE', 'HARD')),
    ADD CONSTRAINT study_questions_format_check CHECK (question_format IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'DISCURSIVE'));

ALTER TABLE study_question_attempts
    ALTER COLUMN option_id DROP NOT NULL,
    ADD COLUMN discursive_answer VARCHAR(10000);
