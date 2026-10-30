-- Migração V25 (Etapa G): perfil profissional, acompanhamento da candidatura e ligação vaga → bloco de estudo

CREATE TABLE career_profiles (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(120) NOT NULL UNIQUE,
    headline VARCHAR(200),
    summary TEXT,
    education TEXT,
    experience TEXT,
    projects TEXT,
    certifications TEXT,
    languages TEXT,
    links TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE career_jobs DROP CONSTRAINT chk_career_jobs_application_status;
ALTER TABLE career_jobs
    ADD CONSTRAINT chk_career_jobs_application_status
    CHECK (application_status IN ('DISCOVERED', 'APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'DISCARDED'));

ALTER TABLE career_jobs
    ADD COLUMN application_notes TEXT,
    ADD COLUMN status_updated_at TIMESTAMPTZ,
    ADD COLUMN study_block_id UUID REFERENCES study_blocks (id) ON DELETE SET NULL;
