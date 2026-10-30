-- Etapa 8: questões teóricas abertas com resposta-modelo, rubrica e correção rigorosa por IA.

-- Resposta-modelo, rubrica e minuto do trecho (se a fonte for vídeo). Nunca expostos antes da resposta.
ALTER TABLE study_questions
    ADD COLUMN model_answer TEXT,
    ADD COLUMN rubric_json TEXT,
    ADD COLUMN source_timestamp VARCHAR(12);

-- Classificações novas: PARTIAL (parcial) e PENDING_EVALUATION (aguardando a IA). Snapshot da avaliação.
ALTER TABLE study_question_attempts
    DROP CONSTRAINT question_attempts_eval_status_check,
    ADD CONSTRAINT question_attempts_eval_status_check
        CHECK (evaluation_status IN ('CORRECT', 'PARTIAL', 'INCORRECT', 'PENDING_EVALUATION', 'UNGRADABLE')),
    ADD COLUMN criteria_scores_json TEXT,
    ADD COLUMN evaluation_details_json TEXT,
    ADD COLUMN rigor_level VARCHAR(20),
    ADD COLUMN evaluated_at TIMESTAMP WITH TIME ZONE;

-- Nível de rigor da correção, por usuário (padrão RIGOROSO quando não há linha).
CREATE TABLE evaluation_preferences (
    owner_id VARCHAR(120) PRIMARY KEY,
    rigor_level VARCHAR(20) NOT NULL DEFAULT 'RIGOROSO',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT evaluation_preferences_rigor_check CHECK (rigor_level IN ('RIGOROSO', 'MODERADO'))
);
