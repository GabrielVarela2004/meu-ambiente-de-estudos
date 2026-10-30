package br.com.estudos.estudos_backend.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.estudos.estudos_backend.knowledge.TopicMasteryCalculator.PracticalEvidence;
import br.com.estudos.estudos_backend.knowledge.TopicMasteryCalculator.TopicMastery;

class TopicMasteryCalculatorTest {

	private static final TopicLearningAssessment STRONG_THEORY =
			new TopicLearningAssessment(TopicLearningState.CONSISTENT, "ok", 10, 100);

	private final TopicMasteryCalculator calculator = new TopicMasteryCalculator();

	@Test
	void practiceWeighsMoreThanTheory() {
		TopicMastery onlyTheory = calculator.calculate(STRONG_THEORY, List.of());
		TopicMastery onlyPractice = calculator.calculate(null, exercises(100, 100, 100));

		assertThat(onlyTheory.combinedPercent()).isEqualTo(30);
		assertThat(onlyPractice.combinedPercent()).isEqualTo(70);
		assertThat(onlyTheory.mastered()).isFalse();
		assertThat(onlyPractice.mastered()).isFalse();
	}

	@Test
	void masteryRequiresBothSides() {
		TopicMastery mastery = calculator.calculate(STRONG_THEORY, exercises(90, 85, 100));

		assertThat(mastery.practicalPercent()).isEqualTo(92);
		assertThat(mastery.combinedPercent()).isEqualTo(94);
		assertThat(mastery.mastered()).isTrue();
	}

	@Test
	void usesTheBestScorePerExerciseAndIgnoresRevealedSolutions() {
		UUID exercise = UUID.randomUUID();
		TopicMastery mastery = calculator.calculate(STRONG_THEORY, List.of(
				new PracticalEvidence(exercise, 30, true),
				new PracticalEvidence(exercise, 60, false),
				new PracticalEvidence(exercise, 90, false)));

		assertThat(mastery.practicalPercent()).isEqualTo(90);
		assertThat(mastery.practicalExercises()).isEqualTo(1);
		assertThat(mastery.mastered()).isFalse();
		assertThat(mastery.reason()).contains("pelo menos 3 exercícios");
	}

	@Test
	void noEvidenceHasNoScore() {
		assertThat(calculator.calculate(null, List.of()).combinedPercent()).isNull();
	}

	private static List<PracticalEvidence> exercises(int... scores) {
		return java.util.Arrays.stream(scores).mapToObj(s -> new PracticalEvidence(UUID.randomUUID(), s, false)).toList();
	}
}
