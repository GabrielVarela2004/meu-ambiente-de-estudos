package br.com.estudos.estudos_backend.exercise;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regra pura (sem Spring nem banco) que calcula a autonomia de uma tentativa prática
 * a partir das ajudas recebidas no chat do exercício.
 *
 * Critérios:
 * 1. Cada ajuda desconta o valor do seu nível ({@link ExerciseHelpLevel#penaltyPercent()}).
 * 2. Solução de uma etapa desconta a fatia daquela etapa (100 / número de etapas), uma vez por etapa.
 * 3. O desconto total fica limitado a {@value #MAX_PENALTY_PERCENT}%: pedir ajuda nunca vale menos que desistir.
 * 4. Solução completa revelada limita a autonomia a {@value #SOLUTION_REVEALED_CAP_PERCENT}%
 *    e a tentativa não conta para domínio.
 */
public class ExerciseAutonomyCalculator {

	public static final int MAX_PENALTY_PERCENT = 50;
	public static final int SOLUTION_REVEALED_CAP_PERCENT = 30;

	/**
	 * @param helps      ajudas recebidas desde a tentativa anterior, em qualquer ordem
	 * @param stepsCount quantidade de etapas do exercício (mínimo 1)
	 */
	public AutonomyResult calculate(List<HelpEvidence> helps, int stepsCount) {
		int steps = Math.max(1, stepsCount);
		double stepShare = 100.0 / steps;

		boolean solutionRevealed = helps.stream().anyMatch(h -> h.level() == ExerciseHelpLevel.FULL_SOLUTION);
		int hintsUsed = (int) helps.stream().filter(h -> h.level().countsAsHint()).count();

		Map<Integer, Integer> penaltyByStep = new LinkedHashMap<>();
		List<Integer> solvedSteps = new ArrayList<>();
		double penalty = 0;
		for (HelpEvidence help : helps) {
			int step = help.stepIndex() == null ? -1 : help.stepIndex();
			int value;
			if (help.level() == ExerciseHelpLevel.STEP_SOLUTION) {
				if (solvedSteps.contains(step)) {
					continue;
				}
				solvedSteps.add(step);
				value = (int) Math.round(stepShare);
			} else {
				value = help.level().penaltyPercent();
			}
			penalty += value;
			if (value > 0) {
				penaltyByStep.merge(step, value, Integer::sum);
			}
		}

		int cappedPenalty = (int) Math.min(MAX_PENALTY_PERCENT, Math.round(penalty));
		int autonomy = 100 - cappedPenalty;
		if (solutionRevealed) {
			autonomy = Math.min(autonomy, SOLUTION_REVEALED_CAP_PERCENT);
		}
		return new AutonomyResult(autonomy, hintsUsed, solutionRevealed, penaltyByStep);
	}

	public record HelpEvidence(ExerciseHelpLevel level, Integer stepIndex) {}

	/**
	 * @param penaltyByStep desconto acumulado por etapa (índice -1 = ajuda sem etapa definida), antes do limite total
	 */
	public record AutonomyResult(int autonomyPercent, int hintsUsed, boolean solutionRevealed,
			Map<Integer, Integer> penaltyByStep) {}
}
