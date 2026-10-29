package br.com.estudos.estudos_backend.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.estudos.estudos_backend.knowledge.TopicLearningStateEvaluator.AttemptEvidence;

/** Testes unitários da regra de estado de aprendizagem: não usam Spring nem banco. */
class TopicLearningStateEvaluatorTest {

	private final TopicLearningStateEvaluator evaluator = new TopicLearningStateEvaluator();
	private final UUID questionA = UUID.randomUUID();
	private final UUID questionB = UUID.randomUUID();

	@Test
	void topicWithoutLinkedQuestionsHasInsufficientData() {
		TopicLearningAssessment result = evaluator.evaluate(List.of(), 0);
		assertEquals(TopicLearningState.INSUFFICIENT_DATA, result.state());
		assertNull(result.recentAccuracyPercent());
	}

	@Test
	void linkedQuestionsWithoutAnswersAreNotStudied() {
		assertEquals(TopicLearningState.NOT_STUDIED, evaluator.evaluate(List.of(), 2).state());
	}

	@Test
	void singleCorrectAnswerIsNeverTreatedAsMastery() {
		TopicLearningAssessment result = evaluator.evaluate(List.of(new AttemptEvidence(questionA, true)), 1);
		assertEquals(TopicLearningState.INSUFFICIENT_DATA, result.state());
		assertNull(result.recentAccuracyPercent(), "Não deve haver percentual com amostra insuficiente");
	}

	@Test
	void lowRecentAccuracyNeedsReview() {
		TopicLearningAssessment result = evaluator.evaluate(List.of(
				new AttemptEvidence(questionA, false),
				new AttemptEvidence(questionB, true),
				new AttemptEvidence(questionA, false)), 2);
		assertEquals(TopicLearningState.NEEDS_REVIEW, result.state());
		assertEquals(33, result.recentAccuracyPercent());
	}

	@Test
	void exactlySixtyPercentIsLearningNotNeedsReview() {
		TopicLearningAssessment result = evaluator.evaluate(List.of(
				new AttemptEvidence(questionA, true),
				new AttemptEvidence(questionB, true),
				new AttemptEvidence(questionA, true),
				new AttemptEvidence(questionB, false),
				new AttemptEvidence(questionA, false)), 2);
		assertEquals(TopicLearningState.LEARNING, result.state());
		assertEquals(60, result.recentAccuracyPercent());
	}

	@Test
	void repeatedCorrectAnswersToTheSameQuestionAreNotConsistent() {
		List<AttemptEvidence> attempts = new ArrayList<>();
		for (int i = 0; i < 5; i++) attempts.add(new AttemptEvidence(questionA, true));
		TopicLearningAssessment result = evaluator.evaluate(attempts, 1);
		assertEquals(TopicLearningState.LEARNING, result.state());
		assertTrue(result.reason().contains("questões diferentes"));
	}

	@Test
	void enoughVariedAndRecentCorrectAnswersAreConsistent() {
		TopicLearningAssessment result = evaluator.evaluate(List.of(
				new AttemptEvidence(questionA, true),
				new AttemptEvidence(questionB, true),
				new AttemptEvidence(questionA, true),
				new AttemptEvidence(questionB, false),
				new AttemptEvidence(questionA, true)), 2);
		assertEquals(TopicLearningState.CONSISTENT, result.state());
		assertEquals(80, result.recentAccuracyPercent());
	}

	@Test
	void mostRecentWrongAnswerPreventsConsistentState() {
		TopicLearningAssessment result = evaluator.evaluate(List.of(
				new AttemptEvidence(questionA, false),
				new AttemptEvidence(questionB, true),
				new AttemptEvidence(questionA, true),
				new AttemptEvidence(questionB, true),
				new AttemptEvidence(questionA, true)), 2);
		assertEquals(TopicLearningState.LEARNING, result.state());
		assertTrue(result.reason().contains("resposta mais recente"));
	}

	@Test
	void onlyRecentWindowDefinesCurrentStateNotOldHistory() {
		List<AttemptEvidence> attempts = new ArrayList<>();
		for (int i = 0; i < 5; i++) attempts.add(new AttemptEvidence(i % 2 == 0 ? questionA : questionB, true));
		for (int i = 0; i < 5; i++) attempts.add(new AttemptEvidence(questionA, false));
		TopicLearningAssessment result = evaluator.evaluate(attempts, 2);
		assertEquals(TopicLearningState.CONSISTENT, result.state());
		assertEquals(10, result.attempts());
		assertEquals(100, result.recentAccuracyPercent());
	}
}
