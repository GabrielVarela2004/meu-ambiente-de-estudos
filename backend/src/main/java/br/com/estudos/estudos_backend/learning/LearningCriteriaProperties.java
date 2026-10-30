package br.com.estudos.estudos_backend.learning;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parâmetros centralizados e ajustáveis para cálculo de desempenho,
 * domínio teórico/prático, revisão espaçada e limites de execução.
 */
@Component
public class LearningCriteriaProperties {

	private final int theoreticalMinAccuracyPercent;
	private final int theoreticalMinQuestionsEvaluated;
	private final int theoreticalMinDistinctSessions;
	private final int theoreticalRetentionAccuracyPercent;
	private final int recentWindowSize;

	private final int practicalMinDistinctExercises;
	private final int practicalMinDistinctSessions;
	private final boolean practicalRequireMandatoryTests;

	private final int reviewLowAccuracyThresholdPercent;
	private final int reviewLowAccuracyIntervalDays;
	private final int reviewMediumAccuracyIntervalDays;
	private final int reviewHighAccuracyUnconfirmedIntervalDays;
	private final int reviewMasteredInitialIntervalDays;
	private final int executionTimeoutMillis;

	public LearningCriteriaProperties(
			@Value("${estudos.learning.theoretical.min-accuracy-percent:85}") int theoreticalMinAccuracyPercent,
			@Value("${estudos.learning.theoretical.min-questions-evaluated:20}") int theoreticalMinQuestionsEvaluated,
			@Value("${estudos.learning.theoretical.min-distinct-sessions:2}") int theoreticalMinDistinctSessions,
			@Value("${estudos.learning.theoretical.retention-accuracy-percent:85}") int theoreticalRetentionAccuracyPercent,
			@Value("${estudos.learning.recent-window-size:10}") int recentWindowSize,
			@Value("${estudos.learning.practical.min-distinct-exercises:5}") int practicalMinDistinctExercises,
			@Value("${estudos.learning.practical.min-distinct-sessions:2}") int practicalMinDistinctSessions,
			@Value("${estudos.learning.practical.require-mandatory-tests:true}") boolean practicalRequireMandatoryTests,
			@Value("${estudos.learning.review.low-threshold-percent:60}") int reviewLowAccuracyThresholdPercent,
			@Value("${estudos.learning.review.low-interval-days:1}") int reviewLowAccuracyIntervalDays,
			@Value("${estudos.learning.review.medium-interval-days:3}") int reviewMediumAccuracyIntervalDays,
			@Value("${estudos.learning.review.high-unconfirmed-interval-days:7}") int reviewHighAccuracyUnconfirmedIntervalDays,
			@Value("${estudos.learning.review.mastered-initial-interval-days:14}") int reviewMasteredInitialIntervalDays,
			@Value("${estudos.learning.execution.timeout-millis:20000}") int executionTimeoutMillis) {
		this.theoreticalMinAccuracyPercent = theoreticalMinAccuracyPercent;
		this.theoreticalMinQuestionsEvaluated = theoreticalMinQuestionsEvaluated;
		this.theoreticalMinDistinctSessions = theoreticalMinDistinctSessions;
		this.theoreticalRetentionAccuracyPercent = theoreticalRetentionAccuracyPercent;
		this.recentWindowSize = recentWindowSize;
		this.practicalMinDistinctExercises = practicalMinDistinctExercises;
		this.practicalMinDistinctSessions = practicalMinDistinctSessions;
		this.practicalRequireMandatoryTests = practicalRequireMandatoryTests;
		this.reviewLowAccuracyThresholdPercent = reviewLowAccuracyThresholdPercent;
		this.reviewLowAccuracyIntervalDays = reviewLowAccuracyIntervalDays;
		this.reviewMediumAccuracyIntervalDays = reviewMediumAccuracyIntervalDays;
		this.reviewHighAccuracyUnconfirmedIntervalDays = reviewHighAccuracyUnconfirmedIntervalDays;
		this.reviewMasteredInitialIntervalDays = reviewMasteredInitialIntervalDays;
		this.executionTimeoutMillis = executionTimeoutMillis;
	}

	public static LearningCriteriaProperties defaults() {
		return new LearningCriteriaProperties(
				85, 20, 2, 85, 10,
				5, 2, true,
				60, 1, 3, 7, 14, 20000);
	}

	public int getTheoreticalMinAccuracyPercent() { return theoreticalMinAccuracyPercent; }
	public int getTheoreticalMinQuestionsEvaluated() { return theoreticalMinQuestionsEvaluated; }
	public int getTheoreticalMinDistinctSessions() { return theoreticalMinDistinctSessions; }
	public int getTheoreticalRetentionAccuracyPercent() { return theoreticalRetentionAccuracyPercent; }
	public int getRecentWindowSize() { return recentWindowSize; }
	public int getPracticalMinDistinctExercises() { return practicalMinDistinctExercises; }
	public int getPracticalMinDistinctSessions() { return practicalMinDistinctSessions; }
	public boolean isPracticalRequireMandatoryTests() { return practicalRequireMandatoryTests; }
	public int getReviewLowAccuracyThresholdPercent() { return reviewLowAccuracyThresholdPercent; }
	public int getReviewLowAccuracyIntervalDays() { return reviewLowAccuracyIntervalDays; }
	public int getReviewMediumAccuracyIntervalDays() { return reviewMediumAccuracyIntervalDays; }
	public int getReviewHighAccuracyUnconfirmedIntervalDays() { return reviewHighAccuracyUnconfirmedIntervalDays; }
	public int getReviewMasteredInitialIntervalDays() { return reviewMasteredInitialIntervalDays; }
	public int getExecutionTimeoutMillis() { return executionTimeoutMillis; }
}
