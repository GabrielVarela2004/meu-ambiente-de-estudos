package br.com.estudos.estudos_backend.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regra pura (sem Spring nem banco) que combina teoria e prática no desempenho de um tópico.
 *
 * Critérios:
 * 1. Teoria: acerto recente nas questões do tópico (ver {@link TopicLearningStateEvaluator}).
 * 2. Prática: média da melhor nota em cada um dos {@value #RECENT_PRACTICAL_EXERCISES} exercícios
 *    mais recentes do tópico. Tentativas com a solução revelada não contam.
 * 3. Desempenho = {@value #PRACTICAL_WEIGHT}% prática + {@value #THEORY_WEIGHT}% teoria; o lado sem
 *    evidência conta como zero, então 100% exige ir bem nos dois.
 * 4. Dominado: prática ≥ {@value #MASTERED_PRACTICAL_PERCENT}% em pelo menos
 *    {@value #MIN_PRACTICAL_EXERCISES} exercícios diferentes, e teoria ≥ {@value #MASTERED_THEORY_PERCENT}%
 *    com estado {@link TopicLearningState#CONSISTENT}.
 */
public class TopicMasteryCalculator {

	public static final int PRACTICAL_WEIGHT = 70;
	public static final int THEORY_WEIGHT = 30;
	public static final int RECENT_PRACTICAL_EXERCISES = 5;
	public static final int MIN_PRACTICAL_EXERCISES = 3;
	public static final int MASTERED_PRACTICAL_PERCENT = 85;
	public static final int MASTERED_THEORY_PERCENT = 80;

	/**
	 * @param theory             avaliação teórica do tópico
	 * @param practicalNewestFirst tentativas práticas do tópico, da mais recente para a mais antiga
	 */
	public TopicMastery calculate(TopicLearningAssessment theory, List<PracticalEvidence> practicalNewestFirst) {
		Map<UUID, Integer> bestByExercise = new LinkedHashMap<>();
		for (PracticalEvidence attempt : practicalNewestFirst) {
			if (attempt.solutionRevealed()) {
				continue;
			}
			if (bestByExercise.containsKey(attempt.exerciseId()) || bestByExercise.size() < RECENT_PRACTICAL_EXERCISES) {
				bestByExercise.merge(attempt.exerciseId(), attempt.scorePercent(), Math::max);
			}
		}
		int exercises = bestByExercise.size();
		Integer practical = exercises == 0 ? null
				: (int) Math.round(bestByExercise.values().stream().mapToInt(Integer::intValue).average().orElse(0));
		Integer theoryPercent = theory == null ? null : theory.recentAccuracyPercent();

		Integer combined = practical == null && theoryPercent == null ? null
				: (int) Math.round(((practical == null ? 0 : practical) * PRACTICAL_WEIGHT
						+ (theoryPercent == null ? 0 : theoryPercent) * THEORY_WEIGHT) / 100.0);

		boolean practicalOk = practical != null && practical >= MASTERED_PRACTICAL_PERCENT && exercises >= MIN_PRACTICAL_EXERCISES;
		boolean theoryOk = theoryPercent != null && theoryPercent >= MASTERED_THEORY_PERCENT
				&& theory.state() == TopicLearningState.CONSISTENT;

		List<String> missing = new ArrayList<>();
		if (practical == null) {
			missing.add("resolver exercícios práticos deste tópico");
		} else if (!practicalOk) {
			if (exercises < MIN_PRACTICAL_EXERCISES) {
				missing.add("prática em pelo menos " + MIN_PRACTICAL_EXERCISES + " exercícios diferentes (há " + exercises + ")");
			}
			if (practical < MASTERED_PRACTICAL_PERCENT) {
				missing.add("nota prática de pelo menos " + MASTERED_PRACTICAL_PERCENT + "% (está em " + practical + "%)");
			}
		}
		if (!theoryOk) {
			missing.add(theoryPercent == null
					? "responder questões teóricas deste tópico"
					: "teoria consistente com pelo menos " + MASTERED_THEORY_PERCENT + "% de acerto recente (está em " + theoryPercent + "%)");
		}
		String reason = missing.isEmpty()
				? "Domínio demonstrado na prática e na teoria."
				: "Para dominar, falta: " + String.join("; ", missing) + ".";
		return new TopicMastery(combined, theoryPercent, practical, exercises, practicalOk && theoryOk, reason);
	}

	public record PracticalEvidence(UUID exerciseId, int scorePercent, boolean solutionRevealed) {}

	/**
	 * @param combinedPercent {@code null} quando não há evidência teórica nem prática
	 */
	public record TopicMastery(Integer combinedPercent, Integer theoryPercent, Integer practicalPercent,
			int practicalExercises, boolean mastered, String reason) {}
}
