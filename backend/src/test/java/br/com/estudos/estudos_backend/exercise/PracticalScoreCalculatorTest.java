package br.com.estudos.estudos_backend.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.CheckResult;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.QualityIssue;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.RunStatus;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.TestResult;
import br.com.estudos.estudos_backend.exercise.ExerciseAutonomyCalculator.AutonomyResult;

class PracticalScoreCalculatorTest {

	private static final AutonomyResult FULL_AUTONOMY = new AutonomyResult(100, 0, false, Map.of());

	private final PracticalScoreCalculator calculator = new PracticalScoreCalculator();

	@Test
	void perfectSolutionScoresFull() {
		var score = calculator.calculate(report(
				List.of(test(true, true), test(true, false)),
				List.of(check(true), check(true)),
				List.of()), FULL_AUTONOMY);

		assertThat(score.finalScorePercent()).isEqualTo(100);
		assertThat(score.passed()).isTrue();
		assertThat(score.status()).isEqualTo(ExerciseEvaluationStatus.PASSED);
	}

	@Test
	void conceptAndQualityProblemsLowerTheScoreEvenWhenTestsPass() {
		var score = calculator.calculate(report(
				List.of(test(true, true), test(true, false)),
				List.of(check(true), check(false)),
				List.of(issue("MAJOR"), issue("MINOR"))), FULL_AUTONOMY);

		// 100*0.5 + 50*0.25 + 80*0.25 = 82.5
		assertThat(score.correctnessPercent()).isEqualTo(100);
		assertThat(score.conceptPercent()).isEqualTo(50);
		assertThat(score.qualityPercent()).isEqualTo(80);
		assertThat(score.finalScorePercent()).isEqualTo(83);
		assertThat(score.passed()).isTrue();
	}

	@Test
	void failedMandatoryTestCapsCorrectnessAndFails() {
		var score = calculator.calculate(report(
				List.of(test(false, true), test(true, false), test(true, false), test(true, false)),
				List.of(),
				List.of()), FULL_AUTONOMY);

		// 3 de 5 pontos de peso = 60%, limitado a 40% por causa do obrigatório
		assertThat(score.correctnessPercent()).isEqualTo(PracticalScoreCalculator.MANDATORY_FAILURE_CAP_PERCENT);
		assertThat(score.conceptPercent()).isNull();
		assertThat(score.mandatoryTestsPassed()).isFalse();
		assertThat(score.passed()).isFalse();
		assertThat(score.status()).isEqualTo(ExerciseEvaluationStatus.PARTIAL);
	}

	@Test
	void autonomyMultipliesTheScore() {
		var score = calculator.calculate(report(List.of(test(true, true)), List.of(check(true)), List.of()),
				new AutonomyResult(60, 3, false, Map.of()));

		assertThat(score.rawScorePercent()).isEqualTo(100);
		assertThat(score.finalScorePercent()).isEqualTo(60);
		assertThat(score.passed()).isFalse();
	}

	@Test
	void revealedSolutionNeverPasses() {
		var score = calculator.calculate(report(List.of(test(true, true)), List.of(), List.of()),
				new AutonomyResult(100, 0, true, Map.of()));

		assertThat(score.passed()).isFalse();
	}

	@Test
	void compilationErrorScoresZero() {
		var score = calculator.calculate(CodeEvaluationReport.failure(RunStatus.COMPILATION_ERROR, "erro"), FULL_AUTONOMY);

		assertThat(score.finalScorePercent()).isZero();
		assertThat(score.status()).isEqualTo(ExerciseEvaluationStatus.COMPILATION_ERROR);
	}

	private static CodeEvaluationReport report(List<TestResult> tests, List<CheckResult> checks, List<QualityIssue> quality) {
		return new CodeEvaluationReport(RunStatus.OK, null, List.of(), tests, checks, quality, "{}");
	}

	private static TestResult test(boolean passed, boolean mandatory) {
		return new TestResult("t", passed, mandatory, null);
	}

	private static CheckResult check(boolean passed) {
		return new CheckResult("X", "x", passed, null);
	}

	private static QualityIssue issue(String severity) {
		return new QualityIssue("R", severity, "m", 1);
	}
}
