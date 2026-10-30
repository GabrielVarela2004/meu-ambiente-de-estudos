-- V29: correção automática dos exercícios práticos (testes em contêiner, conceito e boas práticas)

ALTER TABLE practical_exercises
    ADD COLUMN language VARCHAR(20),
    ADD COLUMN test_code TEXT,
    ADD COLUMN concept_checks TEXT,
    -- NULL: ainda não validado; TRUE: a solução de referência passou nos próprios testes.
    ADD COLUMN auto_evaluable BOOLEAN;

ALTER TABLE practical_exercise_attempts
    ADD COLUMN correctness_percent INT,
    ADD COLUMN concept_percent INT,
    ADD COLUMN quality_percent INT,
    ADD CONSTRAINT exercise_attempts_breakdown_check CHECK (
        (correctness_percent IS NULL OR correctness_percent BETWEEN 0 AND 100)
        AND (concept_percent IS NULL OR concept_percent BETWEEN 0 AND 100)
        AND (quality_percent IS NULL OR quality_percent BETWEEN 0 AND 100));
