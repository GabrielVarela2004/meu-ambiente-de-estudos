package br.com.estudos.estudos_backend.exercise;

import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.QualityIssue;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.RunStatus;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.TestResult;

/**
 * Regra pura (sem Spring nem banco) que transforma o relatório do avaliador e a autonomia
 * na nota de uma tentativa prática.
 *
 * Critérios:
 * 1. Corretude ({@value #CORRECTNESS_WEIGHT}%): testes passados, com teste obrigatório valendo o dobro.
 *    Se algum teste obrigatório falhar, a corretude fica limitada a {@value #MANDATORY_FAILURE_CAP_PERCENT}%.
 * 2. Conceito ({@value #CONCEPT_WEIGHT}%): checagens estruturais do assunto (ex.: uso de @Override).
 *    Sem checagens, esse peso passa para a corretude.
 * 3. Boas práticas ({@value #QUALITY_WEIGHT}%): começa em 100 e perde {@value #MAJOR_ISSUE_PENALTY}
 *    por problema grave e {@value #MINOR_ISSUE_PENALTY} por problema leve.
 * 4. Erro de compilação, tempo esgotado ou erro de execução zeram a nota.
 * 5. Nota final = soma ponderada × autonomia. Aprovada com nota ≥ {@value #PASSING_SCORE_PERCENT}%,
 *    todos os obrigatórios passando e sem solução revelada.
 */
public class PracticalScoreCalculator {

	public static final int CORRECTNESS_WEIGHT = 50;
	public static final int CONCEPT_WEIGHT = 25;
	public static final int QUALITY_WEIGHT = 25;
	public static final int MANDATORY_FAILURE_CAP_PERCENT = 40;
	public static final int MAJOR_ISSUE_PENALTY = 15;
	public static final int MINOR_ISSUE_PENALTY = 5;
	public static final int PASSING_SCORE_PERCENT = 70;

	public ScoreBreakdown calculate(CodeEvaluationReport report, ExerciseAutonomyCalculator.AutonomyResult autonomy) {
		if (report.status() != RunStatus.OK) {
			ExerciseEvaluationStatus status = switch (report.status()) {
				case COMPILATION_ERROR -> ExerciseEvaluationStatus.COMPILATION_ERROR;
				case TIMEOUT -> ExerciseEvaluationStatus.TIMEOUT;
				case RUNTIME_ERROR -> ExerciseEvaluationStatus.RUNTIME_ERROR;
				default -> ExerciseEvaluationStatus.UNGRADABLE;
			};
			return new ScoreBreakdown(0, 0, 0, 0, 0, false, false, status);
		}

		int weightTotal = 0;
		int weightPassed = 0;
		boolean mandatoryPassed = true;
		for (TestResult test : report.tests()) {
			int weight = test.mandatory() ? 2 : 1;
			weightTotal += weight;
			if (test.passed()) {
				weightPassed += weight;
			} else if (test.mandatory()) {
				mandatoryPassed = false;
			}
		}
		int correctness = weightTotal == 0 ? 0 : (int) Math.round(weightPassed * 100.0 / weightTotal);
		if (!mandatoryPassed) {
			correctness = Math.min(correctness, MANDATORY_FAILURE_CAP_PERCENT);
		}

		boolean hasChecks = !report.checks().isEmpty();
		int concept = hasChecks
				? (int) Math.round(report.checks().stream().filter(CodeEvaluationReport.CheckResult::passed).count() * 100.0
						/ report.checks().size())
				: 0;

		long major = report.quality().stream().filter(QualityIssue::major).count();
		long minor = report.quality().size() - major;
		int quality = (int) Math.max(0, 100 - major * MAJOR_ISSUE_PENALTY - minor * MINOR_ISSUE_PENALTY);

		double raw = hasChecks
				? (correctness * CORRECTNESS_WEIGHT + concept * CONCEPT_WEIGHT + quality * QUALITY_WEIGHT) / 100.0
				: (correctness * (CORRECTNESS_WEIGHT + CONCEPT_WEIGHT) + quality * QUALITY_WEIGHT) / 100.0;
		int rawScore = (int) Math.round(raw);
		int finalScore = (int) Math.round(raw * autonomy.autonomyPercent() / 100.0);
		boolean passed = finalScore >= PASSING_SCORE_PERCENT && mandatoryPassed && !autonomy.solutionRevealed();

		ExerciseEvaluationStatus status = passed
				? ExerciseEvaluationStatus.PASSED
				: correctness > 0 ? ExerciseEvaluationStatus.PARTIAL : ExerciseEvaluationStatus.FAILED;
		return new ScoreBreakdown(correctness, hasChecks ? concept : null, quality, rawScore, finalScore, mandatoryPassed,
				passed, status);
	}

	/**
	 * @param conceptPercent {@code null} quando o exercício não tem checagens de conceito
	 * @param rawScorePercent nota antes do desconto de autonomia
	 */
	public record ScoreBreakdown(int correctnessPercent, Integer conceptPercent, int qualityPercent, int rawScorePercent,
			int finalScorePercent, boolean mandatoryTestsPassed, boolean passed, ExerciseEvaluationStatus status) {}
}
