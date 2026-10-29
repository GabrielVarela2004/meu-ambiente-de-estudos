-- Etapa 1: Preparação da Arquitetura, Isolamento por Usuário e Histórico Central de Aprendizagem

-- 1. Isolamento por usuário nos blocos de estudo e nas conversas
ALTER TABLE study_blocks
    ADD COLUMN owner_id VARCHAR(120) NOT NULL DEFAULT 'default-user';

CREATE INDEX idx_study_blocks_owner_status ON study_blocks (owner_id, status, updated_at DESC);

ALTER TABLE chat_messages
    ADD COLUMN owner_id VARCHAR(120) NOT NULL DEFAULT 'default-user',
    ADD COLUMN scope_type VARCHAR(20) NOT NULL DEFAULT 'BLOCK',
    ADD COLUMN scoped_block_ids VARCHAR(2000),
    ADD CONSTRAINT chat_messages_scope_type_check CHECK (scope_type IN ('BLOCK', 'UNIVERSAL'));

CREATE INDEX idx_chat_messages_owner_scope ON chat_messages (owner_id, scope_type, created_at DESC);

-- 2. Metadados adicionais nas fontes para rastreamento de páginas/segmentos
ALTER TABLE study_materials
    ADD COLUMN metadata_json TEXT;

-- 3. Vínculo opcional direto ao tópico principal na questão teórica
ALTER TABLE study_questions
    ADD COLUMN topic_id UUID REFERENCES knowledge_topics (id);

CREATE INDEX idx_study_questions_topic ON study_questions (topic_id);

-- 4. Expansão do histórico de tentativas teóricas (study_question_attempts)
ALTER TABLE study_question_attempts
    ADD COLUMN owner_id VARCHAR(120) NOT NULL DEFAULT 'default-user',
    ADD COLUMN topic_id UUID REFERENCES knowledge_topics (id),
    ADD COLUMN study_session_id UUID REFERENCES study_sessions (id),
    ADD COLUMN source_material_id UUID REFERENCES study_materials (id),
    ADD COLUMN origin_type VARCHAR(20) NOT NULL DEFAULT 'BLOCK',
    ADD COLUMN evaluation_status VARCHAR(20) NOT NULL DEFAULT 'CORRECT',
    ADD COLUMN score_percent INTEGER NOT NULL DEFAULT 100,
    ADD COLUMN time_spent_seconds INTEGER,
    ADD COLUMN attempt_number INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN help_level VARCHAR(25) NOT NULL DEFAULT 'NONE',
    ADD COLUMN hints_used_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN evaluator_feedback TEXT,
    ADD CONSTRAINT question_attempts_origin_check CHECK (origin_type IN ('BLOCK', 'UNIVERSAL')),
    ADD CONSTRAINT question_attempts_eval_status_check CHECK (evaluation_status IN ('CORRECT', 'INCORRECT', 'UNGRADABLE')),
    ADD CONSTRAINT question_attempts_score_check CHECK (score_percent BETWEEN 0 AND 100),
    ADD CONSTRAINT question_attempts_attempt_number_check CHECK (attempt_number >= 1),
    ADD CONSTRAINT question_attempts_help_level_check CHECK (help_level IN ('NONE', 'WITH_HINTS', 'SOLUTION_REVEALED')),
    ADD CONSTRAINT question_attempts_hints_count_check CHECK (hints_used_count >= 0);

-- Sincroniza registros anteriores: se correct = false, evaluation_status = 'INCORRECT' e score_percent = 0
UPDATE study_question_attempts
SET evaluation_status = CASE WHEN correct THEN 'CORRECT' ELSE 'INCORRECT' END,
    score_percent = CASE WHEN correct THEN 100 ELSE 0 END;

CREATE INDEX idx_question_attempts_owner_answered ON study_question_attempts (owner_id, answered_at DESC);
CREATE INDEX idx_question_attempts_topic_answered ON study_question_attempts (topic_id, answered_at DESC);
CREATE INDEX idx_question_attempts_question_answered ON study_question_attempts (question_id, answered_at DESC);

-- 5. Expansão dos exercícios práticos (practical_exercises) para avaliação automatizada e revisão espaçada
ALTER TABLE practical_exercises
    ADD COLUMN topic_id UUID REFERENCES knowledge_topics (id),
    ADD COLUMN study_material_id UUID REFERENCES study_materials (id),
    ADD COLUMN difficulty VARCHAR(20) NOT NULL DEFAULT 'INTERMEDIATE',
    ADD COLUMN exercise_type VARCHAR(30) NOT NULL DEFAULT 'PROBLEM_SOLVING',
    ADD COLUMN starter_code TEXT,
    ADD COLUMN solution_code TEXT,
    ADD COLUMN hints_json TEXT,
    ADD COLUMN test_cases_json TEXT,
    ADD COLUMN next_review_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN review_interval_days INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN correct_streak INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT practical_exercises_difficulty_check CHECK (difficulty IN ('EASY', 'INTERMEDIATE', 'HARD')),
    ADD CONSTRAINT practical_exercises_type_check CHECK (
        exercise_type IN (
            'METHOD_IMPLEMENTATION',
            'PROBLEM_SOLVING',
            'SQL_QUERY',
            'FEATURE_IMPLEMENTATION',
            'CODE_FIX',
            'CODE_ANALYSIS',
            'SMALL_SOLUTION',
            'MULTI_TOPIC_INTEGRATION'
        )
    ),
    ADD CONSTRAINT practical_exercises_review_interval_check CHECK (review_interval_days BETWEEN 1 AND 365),
    ADD CONSTRAINT practical_exercises_streak_check CHECK (correct_streak >= 0);

CREATE INDEX idx_practical_exercises_topic ON practical_exercises (topic_id);
CREATE INDEX idx_practical_exercises_review ON practical_exercises (study_block_id, next_review_at, status);

-- 6. Expansão do histórico de tentativas práticas (practical_exercise_attempts)
ALTER TABLE practical_exercise_attempts
    ADD COLUMN owner_id VARCHAR(120) NOT NULL DEFAULT 'default-user',
    ADD COLUMN study_block_id UUID REFERENCES study_blocks (id),
    ADD COLUMN topic_id UUID REFERENCES knowledge_topics (id),
    ADD COLUMN study_session_id UUID REFERENCES study_sessions (id),
    ADD COLUMN source_material_id UUID REFERENCES study_materials (id),
    ADD COLUMN origin_type VARCHAR(20) NOT NULL DEFAULT 'BLOCK',
    ADD COLUMN evaluation_status VARCHAR(25) NOT NULL DEFAULT 'SELF_RATED',
    ADD COLUMN assistance_level VARCHAR(25) NOT NULL DEFAULT 'INDEPENDENT',
    ADD COLUMN hints_used_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN solution_revealed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN attempt_number INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN time_spent_seconds INTEGER,
    ADD COLUMN tests_total INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN tests_passed INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN mandatory_tests_passed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN score_percent INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN execution_status VARCHAR(30) NOT NULL DEFAULT 'NOT_EXECUTED',
    ADD COLUMN test_results_json TEXT,
    ADD COLUMN error_category VARCHAR(60),
    ADD CONSTRAINT exercise_attempts_origin_check CHECK (origin_type IN ('BLOCK', 'UNIVERSAL')),
    ADD CONSTRAINT exercise_attempts_eval_status_check CHECK (
        evaluation_status IN ('PASSED', 'FAILED', 'PARTIAL', 'COMPILATION_ERROR', 'RUNTIME_ERROR', 'TIMEOUT', 'SELF_RATED', 'UNGRADABLE')
    ),
    ADD CONSTRAINT exercise_attempts_assistance_check CHECK (
        assistance_level IN ('INDEPENDENT', 'WITH_HINTS', 'SOLUTION_REVEALED', 'NOT_COMPLETED')
    ),
    ADD CONSTRAINT exercise_attempts_hints_count_check CHECK (hints_used_count >= 0),
    ADD CONSTRAINT exercise_attempts_attempt_num_check CHECK (attempt_number >= 1),
    ADD CONSTRAINT exercise_attempts_tests_check CHECK (tests_total >= 0 AND tests_passed >= 0 AND tests_passed <= tests_total),
    ADD CONSTRAINT exercise_attempts_score_check CHECK (score_percent BETWEEN 0 AND 100);

-- Preenche study_block_id nos registros de tentativas práticas já existentes
UPDATE practical_exercise_attempts pea
SET study_block_id = pe.study_block_id,
    score_percent = pea.self_rating * 20,
    mandatory_tests_passed = (pea.self_rating >= 4)
FROM practical_exercises pe
WHERE pea.exercise_id = pe.id
  AND pea.study_block_id IS NULL;

CREATE INDEX idx_practical_attempts_owner_created ON practical_exercise_attempts (owner_id, created_at DESC);
CREATE INDEX idx_practical_attempts_block_created ON practical_exercise_attempts (study_block_id, created_at DESC);
CREATE INDEX idx_practical_attempts_topic_created ON practical_exercise_attempts (topic_id, created_at DESC);
