CREATE TABLE practical_exercises (
    id UUID PRIMARY KEY,
    study_block_id UUID NOT NULL REFERENCES study_blocks(id),
    title VARCHAR(160) NOT NULL,
    prompt TEXT NOT NULL,
    guidance TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE practical_exercise_attempts (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES practical_exercises(id),
    answer TEXT NOT NULL,
    self_rating INTEGER NOT NULL,
    feedback TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT practical_exercise_attempts_rating_check CHECK (self_rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_practical_exercises_block_status
    ON practical_exercises (study_block_id, status, updated_at DESC);

CREATE INDEX idx_practical_attempts_exercise_created
    ON practical_exercise_attempts (exercise_id, created_at DESC);
