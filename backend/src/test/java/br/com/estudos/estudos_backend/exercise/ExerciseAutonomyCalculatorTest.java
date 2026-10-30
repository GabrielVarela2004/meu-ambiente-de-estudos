package br.com.estudos.estudos_backend.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.estudos.estudos_backend.exercise.ExerciseAutonomyCalculator.AutonomyResult;
import br.com.estudos.estudos_backend.exercise.ExerciseAutonomyCalculator.HelpEvidence;

class ExerciseAutonomyCalculatorTest {

	private final ExerciseAutonomyCalculator calculator = new ExerciseAutonomyCalculator();

	@Test
	void withoutHelpAutonomyIsFull() {
		AutonomyResult result = calculator.calculate(List.of(), 4);

		assertThat(result.autonomyPercent()).isEqualTo(100);
		assertThat(result.hintsUsed()).isZero();
		assertThat(result.solutionRevealed()).isFalse();
	}

	@Test
	void clarifyingTheStatementCostsNothing() {
		AutonomyResult result = calculator.calculate(List.of(
				new HelpEvidence(ExerciseHelpLevel.CLARIFICATION, 0),
				new HelpEvidence(ExerciseHelpLevel.CLARIFICATION, 1)), 4);

		assertThat(result.autonomyPercent()).isEqualTo(100);
		assertThat(result.hintsUsed()).isZero();
	}

	@Test
	void discountsByHelpLevelAndGroupsByStep() {
		AutonomyResult result = calculator.calculate(List.of(
				new HelpEvidence(ExerciseHelpLevel.CONCEPT, 0),
				new HelpEvidence(ExerciseHelpLevel.DIRECTION, 2),
				new HelpEvidence(ExerciseHelpLevel.SPECIFIC_HINT, 2)), 4);

		assertThat(result.autonomyPercent()).isEqualTo(83);
		assertThat(result.hintsUsed()).isEqualTo(3);
		assertThat(result.penaltyByStep()).containsEntry(0, 2).containsEntry(2, 15);
	}

	@Test
	void stepSolutionCostsTheStepShareOnlyOnce() {
		AutonomyResult result = calculator.calculate(List.of(
				new HelpEvidence(ExerciseHelpLevel.STEP_SOLUTION, 1),
				new HelpEvidence(ExerciseHelpLevel.STEP_SOLUTION, 1)), 4);

		assertThat(result.autonomyPercent()).isEqualTo(75);
	}

	@Test
	void totalPenaltyIsCapped() {
		AutonomyResult result = calculator.calculate(List.of(
				new HelpEvidence(ExerciseHelpLevel.PARTIAL_CODE, 0),
				new HelpEvidence(ExerciseHelpLevel.PARTIAL_CODE, 1),
				new HelpEvidence(ExerciseHelpLevel.PARTIAL_CODE, 2)), 3);

		assertThat(result.autonomyPercent()).isEqualTo(100 - ExerciseAutonomyCalculator.MAX_PENALTY_PERCENT);
	}

	@Test
	void revealedSolutionCapsAutonomy() {
		AutonomyResult result = calculator.calculate(List.of(
				new HelpEvidence(ExerciseHelpLevel.FULL_SOLUTION, null)), 3);

		assertThat(result.autonomyPercent()).isEqualTo(ExerciseAutonomyCalculator.SOLUTION_REVEALED_CAP_PERCENT);
		assertThat(result.solutionRevealed()).isTrue();
	}
}
