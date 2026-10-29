-- Migração V21: Módulo Carreira - Vagas, Fontes, Preferências e Execuções do Agente

-- 1. Preferências de carreira e busca do usuário
CREATE TABLE career_preferences (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(120) NOT NULL UNIQUE,
    target_roles TEXT NOT NULL DEFAULT 'Desenvolvedor Java, Backend Developer, Fullstack, Engenheiro de Software',
    experience_levels TEXT NOT NULL DEFAULT 'JUNIOR,INTERNSHIP,TRAINEE',
    technologies TEXT NOT NULL DEFAULT 'Java, Spring Boot, PostgreSQL, React, TypeScript, Docker, Git',
    mandatory_keywords TEXT,
    preferred_keywords TEXT,
    location VARCHAR(200) DEFAULT 'Brasil / Remoto',
    modality VARCHAR(50) NOT NULL DEFAULT 'ANY',
    min_salary NUMERIC(12, 2),
    search_days VARCHAR(100) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI',
    search_time_hour INT NOT NULL DEFAULT 9,
    search_time_minute INT NOT NULL DEFAULT 0,
    max_round_minutes INT NOT NULL DEFAULT 30,
    agent_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_career_preferences_owner ON career_preferences (owner_id);

-- 2. Fontes estruturadas de vagas
CREATE TABLE career_job_sources (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    integration_type VARCHAR(50) NOT NULL DEFAULT 'API_REST',
    base_url VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    last_checked_at TIMESTAMPTZ,
    last_status VARCHAR(50) NOT NULL DEFAULT 'UNTESTED',
    last_error TEXT,
    rate_limit_delay_ms BIGINT NOT NULL DEFAULT 1000,
    requires_auth BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed das fontes estruturadas e públicas para tecnologia
INSERT INTO career_job_sources (id, name, integration_type, base_url, enabled, last_status, rate_limit_delay_ms, requires_auth)
VALUES 
    (gen_random_uuid(), 'Remotive Tech Jobs API', 'API_REST', 'https://remotive.com/api/remote-jobs', true, 'UNTESTED', 1500, false),
    (gen_random_uuid(), 'Arbeitnow Tech Board API', 'API_REST', 'https://www.arbeitnow.com/api/job-board-api', true, 'UNTESTED', 1000, false)
ON CONFLICT (name) DO NOTHING;

-- 3. Vagas normalizadas
CREATE TABLE career_jobs (
    id UUID PRIMARY KEY,
    source_id UUID REFERENCES career_job_sources (id) ON DELETE SET NULL,
    source_name VARCHAR(150) NOT NULL,
    external_id VARCHAR(250),
    url VARCHAR(1000) NOT NULL UNIQUE,
    title VARCHAR(300) NOT NULL,
    company VARCHAR(200) NOT NULL,
    location VARCHAR(250),
    modality VARCHAR(50) NOT NULL DEFAULT 'REMOTE',
    experience_level VARCHAR(50) NOT NULL DEFAULT 'JUNIOR',
    salary_min NUMERIC(12, 2),
    salary_max NUMERIC(12, 2),
    salary_currency VARCHAR(10) NOT NULL DEFAULT 'BRL',
    description TEXT NOT NULL,
    requirements_summary TEXT,
    tags TEXT,
    published_at TIMESTAMPTZ,
    discovered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_career_jobs_published ON career_jobs (published_at DESC);
CREATE INDEX idx_career_jobs_source ON career_jobs (source_id);
CREATE INDEX idx_career_jobs_modality ON career_jobs (modality);
CREATE INDEX idx_career_jobs_exp ON career_jobs (experience_level);

-- 4. Execuções do agente de vagas (agendadas, manuais ou recuperadas)
CREATE TABLE career_agent_executions (
    id UUID PRIMARY KEY,
    trigger_type VARCHAR(50) NOT NULL, -- 'SCHEDULED', 'MANUAL', 'RECOVERED'
    status VARCHAR(50) NOT NULL,       -- 'RUNNING', 'COMPLETED', 'TIMED_OUT', 'FAILED'
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    jobs_found INT NOT NULL DEFAULT 0,
    new_jobs_count INT NOT NULL DEFAULT 0,
    duration_seconds BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    execution_details TEXT
);

CREATE INDEX idx_career_agent_exec_status ON career_agent_executions (status, started_at DESC);
