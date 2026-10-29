ALTER TABLE study_materials
    ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN source_url VARCHAR(1000),
    ADD COLUMN external_video_id VARCHAR(64),
    ADD COLUMN language VARCHAR(24),
    ADD COLUMN processing_status VARCHAR(25) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN error_message VARCHAR(1000),
    ADD CONSTRAINT study_materials_source_type_check CHECK (
        source_type IN ('TEXT', 'FILE_TXT', 'FILE_MD', 'FILE_PDF', 'FILE_DOCX', 'YOUTUBE')
    ),
    ADD CONSTRAINT study_materials_processing_status_check CHECK (
        processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
    );

CREATE INDEX idx_study_materials_block_processing ON study_materials (study_block_id, status, processing_status);
