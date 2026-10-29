-- V20: Alinhamento da biblioteca única de materiais para exercícios práticos
ALTER TABLE practical_exercises
    ADD COLUMN source_excerpt VARCHAR(2000);

CREATE INDEX idx_practical_exercises_material
    ON practical_exercises (study_material_id);
