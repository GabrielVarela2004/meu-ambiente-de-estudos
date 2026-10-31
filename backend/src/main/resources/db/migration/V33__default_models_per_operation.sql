-- Migração V33: modelos padrão por operação para o nível gratuito do Gemini (2026-10-31).
-- Volume alto vai para o 3.5 Flash Lite (15 RPM / 500 por dia); onde a qualidade pesa mais e o
-- volume é baixo (correção e análise de vídeo), o 3.8 Flash (5 RPM / 20 por dia).
-- Não sobrescreve escolhas do usuário: só cria rota inexistente e só preenche modelo vazio
-- em rotas já apontadas para o Gemini. O modelo continua editável em Configurações › Provedores de IA.

INSERT INTO ai_operation_routes (operation, provider_id, fallback_enabled, model, updated_at)
VALUES
    ('CHAT', 'gemini', true, 'gemini-3.5-flash-lite', NOW()),
    ('QUESTION_GENERATION', 'gemini', true, 'gemini-3.5-flash-lite', NOW()),
    ('EXERCISE_GENERATION', 'gemini', true, 'gemini-3.5-flash-lite', NOW()),
    ('EXERCISE_TUTOR', 'gemini', true, 'gemini-3.5-flash-lite', NOW()),
    ('TOPIC_EXTRACTION', 'gemini', true, 'gemini-3.5-flash-lite', NOW()),
    ('MATERIAL_SUMMARY', 'gemini', true, 'gemini-3.5-flash-lite', NOW()),
    ('PERFORMANCE_ANALYSIS', 'gemini', true, 'gemini-3.5-flash-lite', NOW()),
    ('CAREER_ANALYSIS', 'gemini', true, 'gemini-3.5-flash-lite', NOW()),
    ('ANSWER_EVALUATION', 'gemini', true, 'gemini-3.8-flash', NOW()),
    ('VIDEO_ANALYSIS', 'gemini', true, 'gemini-3.8-flash', NOW())
ON CONFLICT (operation) DO NOTHING;

UPDATE ai_operation_routes
SET model = CASE operation
        WHEN 'CHAT' THEN 'gemini-3.5-flash-lite'
        WHEN 'QUESTION_GENERATION' THEN 'gemini-3.5-flash-lite'
        WHEN 'EXERCISE_GENERATION' THEN 'gemini-3.5-flash-lite'
        WHEN 'EXERCISE_TUTOR' THEN 'gemini-3.5-flash-lite'
        WHEN 'TOPIC_EXTRACTION' THEN 'gemini-3.5-flash-lite'
        WHEN 'MATERIAL_SUMMARY' THEN 'gemini-3.5-flash-lite'
        WHEN 'PERFORMANCE_ANALYSIS' THEN 'gemini-3.5-flash-lite'
        WHEN 'CAREER_ANALYSIS' THEN 'gemini-3.5-flash-lite'
        WHEN 'ANSWER_EVALUATION' THEN 'gemini-3.8-flash'
        WHEN 'VIDEO_ANALYSIS' THEN 'gemini-3.8-flash'
    END,
    updated_at = NOW()
WHERE provider_id = 'gemini'
  AND model IS NULL
  AND operation IN ('CHAT', 'QUESTION_GENERATION', 'EXERCISE_GENERATION', 'EXERCISE_TUTOR', 'TOPIC_EXTRACTION',
                    'MATERIAL_SUMMARY', 'PERFORMANCE_ANALYSIS', 'CAREER_ANALYSIS', 'ANSWER_EVALUATION',
                    'VIDEO_ANALYSIS');
