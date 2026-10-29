ALTER TABLE knowledge_topics
    ADD COLUMN IF NOT EXISTS source_material_id UUID REFERENCES study_materials(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS supporting_excerpt VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS origin VARCHAR(25) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS user_validated BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS extracted_at TIMESTAMP WITH TIME ZONE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'knowledge_topics_origin_check'
    ) THEN
        ALTER TABLE knowledge_topics
            ADD CONSTRAINT knowledge_topics_origin_check CHECK (origin IN ('MANUAL', 'AI_EXTRACTED'));
    END IF;
END $$;

ALTER TABLE study_questions
    ADD COLUMN IF NOT EXISTS topic_id UUID REFERENCES knowledge_topics(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS source_excerpt VARCHAR(2000);

CREATE INDEX IF NOT EXISTS idx_knowledge_topics_source_material ON knowledge_topics (source_material_id);
CREATE INDEX IF NOT EXISTS idx_study_questions_topic_id ON study_questions (topic_id);
