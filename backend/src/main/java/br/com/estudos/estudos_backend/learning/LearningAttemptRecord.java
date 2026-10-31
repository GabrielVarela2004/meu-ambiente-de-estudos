package br.com.estudos.estudos_backend.learning;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro consolidado e imutável de uma tentativa individual de aprendizagem
 * (teórica ou prática), utilizado como base única para cálculo de desempenho,
 * domínio e revisão espaçada.
 */
public record LearningAttemptRecord(
		UUID attemptId,
		String ownerId,
		OffsetDateTime timestamp,
		UUID studyBlockId,
		UUID studySessionId,
		LearningModality modality,
		UUID activityId,
		UUID sourceMaterialId,
		ActivityOriginType originType,
		boolean evaluable,
		boolean passedOrCorrect,
		int scorePercent,
		Integer timeSpentSeconds,
		int attemptNumber,
		AssistanceLevel assistanceLevel,
		int hintsUsedCount,
		boolean solutionRevealed,
		int testsTotal,
		int testsPassed,
		boolean mandatoryTestsPassed,
		String evaluationStatus) {
}
