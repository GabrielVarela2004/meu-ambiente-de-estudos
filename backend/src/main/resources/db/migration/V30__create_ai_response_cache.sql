-- Migração V30: cache de respostas de IA
-- Só operações em que a mesma entrada deve dar a mesma saída (resumo, tópicos, correção, análises).
-- A chave é o SHA-256 de usuário + operação + provedor + modelo + pedido completo, então nada vaza entre usuários.

CREATE TABLE ai_response_cache (
    cache_key VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(120) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    provider_name VARCHAR(60) NOT NULL,
    model VARCHAR(120),
    response TEXT NOT NULL,
    input_tokens INT,
    output_tokens INT,
    hit_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ai_response_cache_owner ON ai_response_cache (owner_id);
CREATE INDEX idx_ai_response_cache_expires ON ai_response_cache (expires_at);
