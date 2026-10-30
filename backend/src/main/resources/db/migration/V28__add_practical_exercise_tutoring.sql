-- V28: tutoria por chat dentro do exercício prático e autonomia medida pela ajuda recebida

ALTER TABLE practical_exercises
    ADD COLUMN steps_json TEXT;

CREATE TABLE practical_exercise_chat_messages (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(120) NOT NULL,
    exercise_id UUID NOT NULL REFERENCES practical_exercises (id) ON DELETE CASCADE,
    step_index INT,
    user_message TEXT NOT NULL,
    assistant_answer TEXT NOT NULL,
    help_level VARCHAR(30) NOT NULL,
    provider_name VARCHAR(60),
    model_name VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT exercise_chat_help_level_check CHECK (help_level IN (
        'CLARIFICATION', 'CONCEPT', 'DIRECTION', 'SPECIFIC_HINT', 'PARTIAL_CODE', 'STEP_SOLUTION', 'FULL_SOLUTION')),
    CONSTRAINT exercise_chat_step_index_check CHECK (step_index IS NULL OR step_index >= 0)
);

CREATE INDEX idx_exercise_chat_owner_exercise
    ON practical_exercise_chat_messages (owner_id, exercise_id, created_at);

ALTER TABLE practical_exercise_attempts
    ADD COLUMN autonomy_percent INT,
    ADD CONSTRAINT exercise_attempts_autonomy_check CHECK (autonomy_percent IS NULL OR autonomy_percent BETWEEN 0 AND 100);
