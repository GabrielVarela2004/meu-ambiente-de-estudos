-- Etapa 5: Histórico permanente e snapshots das tentativas para auditoria e métricas globais

ALTER TABLE study_question_attempts
    ADD COLUMN IF NOT EXISTS prompt_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS user_answer_text TEXT,
    ADD COLUMN IF NOT EXISTS expected_answer_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS explanation_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS study_block_title_snapshot VARCHAR(255),
    ADD COLUMN IF NOT EXISTS topic_title_snapshot VARCHAR(255),
    ADD COLUMN IF NOT EXISTS difficulty_snapshot VARCHAR(20) DEFAULT 'INTERMEDIATE',
    ADD COLUMN IF NOT EXISTS question_format_snapshot VARCHAR(25) DEFAULT 'MULTIPLE_CHOICE',
    ADD COLUMN IF NOT EXISTS source_title_snapshot VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source_excerpt_snapshot TEXT;

-- Preenche snapshots dos registros anteriores a partir das tabelas relacionais
UPDATE study_question_attempts sqa
SET prompt_snapshot = sq.prompt,
    explanation_snapshot = sq.explanation,
    expected_answer_snapshot = COALESCE(sq.expected_answer, (
        SELECT o.option_text FROM study_question_options o
        WHERE o.question_id = sq.id AND o.correct = TRUE
        LIMIT 1
    )),
    difficulty_snapshot = sq.difficulty,
    question_format_snapshot = sq.question_format,
    source_excerpt_snapshot = sq.source_excerpt,
    study_block_title_snapshot = sb.title,
    topic_title_snapshot = COALESCE(kt.title, (
        SELECT t.title FROM knowledge_topics t WHERE t.id = sqa.topic_id
    )),
    source_title_snapshot = sm.title,
    user_answer_text = COALESCE(
        sqa.discursive_answer,
        (SELECT opt.option_text FROM study_question_options opt WHERE opt.id = sqa.option_id)
    )
FROM study_questions sq
JOIN study_blocks sb ON sq.study_block_id = sb.id
LEFT JOIN knowledge_topics kt ON sq.topic_id = kt.id
LEFT JOIN study_materials sm ON sq.study_material_id = sm.id
WHERE sqa.question_id = sq.id;

-- Garante que se uma questão, opção, tópico ou fonte for removido futuramente, a tentativa e seu snapshot histórico permaneçam
ALTER TABLE study_question_attempts
    ALTER COLUMN question_id DROP NOT NULL;

DO $$
DECLARE
    fk_name text;
BEGIN
    SELECT tc.constraint_name INTO fk_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_name = 'study_question_attempts'
      AND kcu.column_name = 'question_id'
      AND tc.constraint_type = 'FOREIGN KEY';
    IF fk_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE study_question_attempts DROP CONSTRAINT ' || quote_ident(fk_name);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'study_question_attempts' AND constraint_name = 'fk_question_attempts_question'
    ) THEN
        ALTER TABLE study_question_attempts
            ADD CONSTRAINT fk_question_attempts_question
            FOREIGN KEY (question_id) REFERENCES study_questions (id) ON DELETE SET NULL;
    END IF;
END $$;

DO $$
DECLARE
    fk_name text;
BEGIN
    SELECT tc.constraint_name INTO fk_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_name = 'study_question_attempts'
      AND kcu.column_name = 'option_id'
      AND tc.constraint_type = 'FOREIGN KEY';
    IF fk_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE study_question_attempts DROP CONSTRAINT ' || quote_ident(fk_name);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'study_question_attempts' AND constraint_name = 'fk_question_attempts_option'
    ) THEN
        ALTER TABLE study_question_attempts
            ADD CONSTRAINT fk_question_attempts_option
            FOREIGN KEY (option_id) REFERENCES study_question_options (id) ON DELETE SET NULL;
    END IF;
END $$;

DO $$
DECLARE
    fk_name text;
BEGIN
    SELECT tc.constraint_name INTO fk_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_name = 'study_question_attempts'
      AND kcu.column_name = 'topic_id'
      AND tc.constraint_type = 'FOREIGN KEY';
    IF fk_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE study_question_attempts DROP CONSTRAINT ' || quote_ident(fk_name);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'study_question_attempts' AND constraint_name = 'fk_question_attempts_topic'
    ) THEN
        ALTER TABLE study_question_attempts
            ADD CONSTRAINT fk_question_attempts_topic
            FOREIGN KEY (topic_id) REFERENCES knowledge_topics (id) ON DELETE SET NULL;
    END IF;
END $$;

DO $$
DECLARE
    fk_name text;
BEGIN
    SELECT tc.constraint_name INTO fk_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_name = 'study_question_attempts'
      AND kcu.column_name = 'source_material_id'
      AND tc.constraint_type = 'FOREIGN KEY';
    IF fk_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE study_question_attempts DROP CONSTRAINT ' || quote_ident(fk_name);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'study_question_attempts' AND constraint_name = 'fk_question_attempts_source_material'
    ) THEN
        ALTER TABLE study_question_attempts
            ADD CONSTRAINT fk_question_attempts_source_material
            FOREIGN KEY (source_material_id) REFERENCES study_materials (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_question_attempts_block_diff
    ON study_question_attempts (study_block_id, difficulty_snapshot);
