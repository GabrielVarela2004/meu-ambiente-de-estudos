CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    study_block_id UUID REFERENCES study_blocks (id),
    user_message VARCHAR(10000) NOT NULL,
    assistant_answer TEXT NOT NULL,
    provider_name VARCHAR(60) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    referenced_materials VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_chat_messages_block_created
    ON chat_messages (study_block_id, created_at DESC);
