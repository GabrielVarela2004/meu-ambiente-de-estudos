-- Migração V32: validação manual dos tópicos extraídos (aba Mapeamento de Fontes)
-- Tópicos sugeridos ficam em PENDING_REVIEW (fora de questões e desempenho) até o usuário aprovar e oficializar.

ALTER TABLE knowledge_topics DROP CONSTRAINT knowledge_topics_status_check;
ALTER TABLE knowledge_topics
    ADD CONSTRAINT knowledge_topics_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED', 'PENDING_REVIEW'));

ALTER TABLE knowledge_topics
    ADD COLUMN relevance_score INT CHECK (relevance_score IS NULL OR relevance_score BETWEEN 0 AND 100),
    ADD COLUMN relevance_reason VARCHAR(500),
    ADD COLUMN review_round INT NOT NULL DEFAULT 0,
    ADD COLUMN review_state VARCHAR(20)
        CHECK (review_state IS NULL OR review_state IN ('PENDING', 'APPROVED', 'REJECTED', 'FEEDBACK')),
    ADD COLUMN user_feedback VARCHAR(1000),
    ADD COLUMN previous_title VARCHAR(160);

-- Memória das decisões do usuário: alimenta as próximas extrações (não sugerir rejeitados, seguir preferências)
-- e a taxa de aprovação na primeira rodada.
CREATE TABLE topic_review_decisions (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(120) NOT NULL,
    study_block_id UUID NOT NULL REFERENCES study_blocks (id) ON DELETE CASCADE,
    topic_title VARCHAR(160) NOT NULL,
    action VARCHAR(20) NOT NULL CHECK (action IN ('APPROVED', 'REJECTED', 'FEEDBACK')),
    feedback VARCHAR(1000),
    review_round INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_topic_review_decisions_owner ON topic_review_decisions (owner_id, created_at);
