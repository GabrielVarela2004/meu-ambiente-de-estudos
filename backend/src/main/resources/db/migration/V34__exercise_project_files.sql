-- Exercícios práticos com vários arquivos (editor estilo IntelliJ).
-- Arquivos guardados como JSON: [{"path": "loja/Pedido.java", "content": "..."}], caminhos relativos a src/.

-- Arquivos iniciais do exercício (quando a IA gera um projeto com vários tipos); vazio usa o starter_code.
ALTER TABLE practical_exercises
    ADD COLUMN starter_files_json TEXT;

-- Arquivos enviados em cada tentativa (a coluna answer continua com o texto único, para histórico e telas antigas).
ALTER TABLE practical_exercise_attempts
    ADD COLUMN submitted_files_json TEXT;

-- Rascunho salvo automaticamente: um por usuário e exercício.
CREATE TABLE practical_exercise_drafts (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(120) NOT NULL,
    exercise_id UUID NOT NULL REFERENCES practical_exercises (id) ON DELETE CASCADE,
    files_json TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_practical_exercise_drafts_owner_exercise UNIQUE (owner_id, exercise_id)
);
