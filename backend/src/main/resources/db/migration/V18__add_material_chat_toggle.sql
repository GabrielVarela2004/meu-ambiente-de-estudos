-- Etapa 2: Blocos de Estudo e Gestão Avançada de Fontes (Estilo NotebookLM)
-- Adiciona a coluna enabled_for_chat para controle de inclusão no chat/contexto da IA

ALTER TABLE study_materials
    ADD COLUMN IF NOT EXISTS enabled_for_chat BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_study_materials_chat_active
    ON study_materials (study_block_id, enabled_for_chat, status);
