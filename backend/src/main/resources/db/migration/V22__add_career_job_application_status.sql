-- Migração V22: andamento da candidatura em cada vaga (Feed de Vagas › Mudar status)
ALTER TABLE career_jobs
    ADD COLUMN application_status VARCHAR(30) NOT NULL DEFAULT 'DISCOVERED';

ALTER TABLE career_jobs
    ADD CONSTRAINT chk_career_jobs_application_status
    CHECK (application_status IN ('DISCOVERED', 'APPLIED', 'INTERVIEWING', 'DISCARDED'));
