-- Migração V24: novos tipos de fonte (PPTX, código-fonte, página web) e resumo por material

ALTER TABLE study_materials DROP CONSTRAINT study_materials_source_type_check;
ALTER TABLE study_materials
    ADD CONSTRAINT study_materials_source_type_check CHECK (
        source_type IN ('TEXT', 'FILE_TXT', 'FILE_MD', 'FILE_PDF', 'FILE_DOCX', 'FILE_PPTX', 'FILE_CODE', 'WEB', 'YOUTUBE')
    );

ALTER TABLE study_materials
    ADD COLUMN summary TEXT,
    ADD COLUMN summary_source VARCHAR(160),
    ADD COLUMN summary_generated_at TIMESTAMPTZ;

CREATE INDEX idx_study_materials_block_source_url ON study_materials (study_block_id, source_url);
