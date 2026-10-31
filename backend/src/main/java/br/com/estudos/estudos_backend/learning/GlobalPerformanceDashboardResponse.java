package br.com.estudos.estudos_backend.learning;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record GlobalPerformanceDashboardResponse(
		OverallPerformanceSummary overall,
		List<BlockPerformanceSummary> blocks,
		List<TimelinePoint> timeline,
		List<HistoricalAttemptDetail> recentAttempts) {

	public record OverallPerformanceSummary(
			long totalAttempts,
			long uniqueQuestionsAnswered,
			long evaluatedAttempts,
			long correctAttempts,
			long incorrectAttempts,
			Integer accuracyPercent,
			Integer errorPercent) {}

	public record BlockPerformanceSummary(
			UUID studyBlockId,
			String blockTitle,
			long totalAttempts,
			long uniqueQuestions,
			long evaluatedAttempts,
			long correctAttempts,
			long incorrectAttempts,
			Integer accuracyPercent,
			Integer errorPercent,
			/** Respostas PARCIAIS (contam como avaliadas, não como acerto). */
			long partialAttempts,
			/** Nota média (0–100) das tentativas corrigidas; pendentes ficam fora. */
			Integer averageScorePercent) {}

	public record TimelinePoint(
			String date,
			long totalAttempts,
			long correctAttempts,
			long incorrectAttempts,
			Integer accuracyPercent) {}

	public record HistoricalAttemptDetail(
			UUID attemptId,
			OffsetDateTime answeredAt,
			UUID studyBlockId,
			String studyBlockTitle,
			UUID questionId,
			String difficulty,
			String questionFormat,
			String prompt,
			String userAnswer,
			String expectedAnswer,
			String explanation,
			boolean correct,
			String evaluationStatus,
			int scorePercent,
			String sourceTitle,
			String sourceExcerpt) {}
}
