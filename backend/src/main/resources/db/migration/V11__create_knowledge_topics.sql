CREATE TABLE knowledge_topics (
    id UUID PRIMARY KEY,
    study_block_id UUID NOT NULL REFERENCES study_blocks (id),
    parent_topic_id UUID REFERENCES knowledge_topics (id),
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT knowledge_topics_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT knowledge_topics_not_own_parent CHECK (parent_topic_id IS NULL OR parent_topic_id <> id)
);

CREATE INDEX idx_knowledge_topics_block_status ON knowledge_topics (study_block_id, status);
CREATE INDEX idx_knowledge_topics_parent ON knowledge_topics (parent_topic_id);

CREATE TABLE knowledge_topic_questions (
    topic_id UUID NOT NULL REFERENCES knowledge_topics (id),
    question_id UUID NOT NULL REFERENCES study_questions (id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (topic_id, question_id)
);

CREATE INDEX idx_knowledge_topic_questions_question ON knowledge_topic_questions (question_id);
