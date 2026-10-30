-- Migração V23: camada de IA com múltiplos provedores
-- As chaves de API NÃO são armazenadas aqui; ficam apenas nas variáveis de ambiente.

-- 1. Preferências por provedor (habilitar/desabilitar, modelo e limite diário de chamadas)
CREATE TABLE ai_provider_settings (
    provider_id VARCHAR(30) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT true,
    model VARCHAR(120),
    daily_request_limit INT CHECK (daily_request_limit IS NULL OR daily_request_limit > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Provedor escolhido para cada operação, com fallback opcional para os demais
CREATE TABLE ai_operation_routes (
    operation VARCHAR(40) PRIMARY KEY,
    provider_id VARCHAR(30) NOT NULL,
    fallback_enabled BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Registro de cada chamada (controle de consumo)
CREATE TABLE ai_usage_records (
    id UUID PRIMARY KEY,
    provider_id VARCHAR(30) NOT NULL,
    model VARCHAR(120),
    operation VARCHAR(40) NOT NULL,
    success BOOLEAN NOT NULL,
    http_status INT,
    input_tokens INT,
    output_tokens INT,
    latency_ms INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_usage_provider_created ON ai_usage_records (provider_id, created_at);
CREATE INDEX idx_ai_usage_created ON ai_usage_records (created_at);
