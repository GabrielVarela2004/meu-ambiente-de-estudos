-- Etapa 7: vídeos do YouTube como fonte (prévia, player e análise por IA com transcrição como plano B).
-- O id do vídeo já existe em external_video_id.
ALTER TABLE study_materials
    ADD COLUMN video_channel VARCHAR(255),
    ADD COLUMN video_thumbnail_url VARCHAR(1000),
    ADD COLUMN video_duration_seconds INTEGER,
    ADD COLUMN analysis_method VARCHAR(20),
    ADD CONSTRAINT study_materials_analysis_method_check
        CHECK (analysis_method IS NULL OR analysis_method IN ('ANALISE_IA', 'TRANSCRICAO')),
    ADD CONSTRAINT study_materials_video_duration_check
        CHECK (video_duration_seconds IS NULL OR video_duration_seconds >= 0);

-- Vídeos já importados vieram da transcrição.
UPDATE study_materials SET analysis_method = 'TRANSCRICAO' WHERE source_type = 'YOUTUBE';
