-- Migração V31: modelo por operação (ex.: Gemini 3.5 Flash Lite para gerar questões e 3.8 Flash para corrigir).
-- Vazio = modelo configurado no provedor.
ALTER TABLE ai_operation_routes ADD COLUMN model VARCHAR(120);
