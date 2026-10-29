ALTER TABLE knowledge_topics
    ADD COLUMN prerequisite_topic_id UUID REFERENCES knowledge_topics (id),
    ADD CONSTRAINT knowledge_topics_not_own_prerequisite CHECK (prerequisite_topic_id IS NULL OR prerequisite_topic_id <> id);

CREATE INDEX idx_knowledge_topics_prerequisite ON knowledge_topics (prerequisite_topic_id);
