package br.com.estudos.estudos_backend.question;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Regras de rigor da correção das questões abertas. A IA só dá as notas por critério e aponta faltas e erros;
 * a nota geral e a classificação são calculadas aqui, no backend, de forma determinística.
 */
public final class DiscursiveEvaluationRules {

	/**
	 * Pesos da nota geral (métrica de rigorosidade, somam 100): precisão conceitual e lógica (rigor alto,
	 * inegociável), comportamento prático e casos de borda (rigor médio) e vocabulário técnico (rigor baixo,
	 * flexível — sinônimos equivalentes contam).
	 */
	static final Map<String, Integer> WEIGHTS = Map.of(
			"conceptualPrecision", 50,
			"practicalBehavior", 30,
			"technicalVocabulary", 20);

	static final List<String> CRITERIA = List.of("conceptualPrecision", "practicalBehavior", "technicalVocabulary");

	/** Penalização da nota geral quando há afirmação conceitualmente errada. */
	static final int CONCEPTUAL_ERROR_PENALTY = 30;
	static final int PARTIAL_THRESHOLD = 50;
	static final String MANIPULATION_NOTE = "A resposta contém instruções para a correção (ex.: pedir nota); elas foram "
			+ "ignoradas e a resposta foi penalizada.";

	// Pedidos de nota ou ordens ao corretor dentro da resposta do aluno.
	private static final Pattern MANIPULATION = Pattern.compile(
			"(?iu)(ignore|ignorar|desconsidere|desconsiderar|esque[cç]a)\\s.{0,40}(regra|instru[cç]|crit[eé]rio|rubrica|prompt)"
					+ "|(me\\s+)?d[eêá]\\s+(a\\s+)?(nota|pontua[cç][aã]o)\\s+(m[aá]xima|100|10|cem|total)"
					+ "|nota\\s+(100|m[aá]xima|cem)"
					+ "|classifi(que|car)\\s+(como\\s+)?(correta|correct)"
					+ "|(you are|voc[eê] [eé]) (now|agora)");

	private DiscursiveEvaluationRules() {
	}

	/** Avaliação da IA (notas 0–100 por critério e observações), antes das regras de rigor. */
	public record AiAssessment(
			Map<String, Integer> scores,
			List<String> correctPoints,
			List<String> missingEssentialConcepts,
			List<String> missingTerms,
			List<String> conceptualErrors,
			boolean superficial,
			boolean keywordList,
			boolean usesMaterialTerminology,
			String improvementTip) {}

	/** Resultado final calculado no backend. */
	public record Result(
			QuestionEvaluationStatus classification,
			int overallScore,
			Map<String, Integer> criteriaScores,
			List<String> correctPoints,
			List<String> missingEssentialConcepts,
			List<String> missingTerms,
			List<String> errors,
			String improvementTip,
			boolean manipulationDetected) {}

	public static boolean looksLikeManipulation(String answer) {
		return answer != null && MANIPULATION.matcher(answer).find();
	}

	public static Result apply(AiAssessment ai, EvaluationRigor rigor, String userAnswer) {
		Map<String, Integer> scores = new LinkedHashMap<>();
		for (String criterion : CRITERIA) {
			Integer value = ai.scores() == null ? null : ai.scores().get(criterion);
			scores.put(criterion, value == null ? 0 : Math.max(0, Math.min(100, value)));
		}
		int overall = (int) Math.round(CRITERIA.stream()
				.mapToDouble(c -> scores.get(c) * WEIGHTS.get(c) / 100.0)
				.sum());

		List<String> errors = new ArrayList<>(ai.conceptualErrors());
		boolean hasConceptualError = !ai.conceptualErrors().isEmpty();
		boolean manipulation = looksLikeManipulation(userAnswer);
		boolean capAtPartial = false;

		if (hasConceptualError) {
			overall = Math.max(0, overall - CONCEPTUAL_ERROR_PENALTY);
			capAtPartial = true;
		}
		if (manipulation) {
			overall = Math.min(overall, PARTIAL_THRESHOLD);
			capAtPartial = true;
			errors.add(MANIPULATION_NOTE);
		}
		if (!ai.missingEssentialConcepts().isEmpty() || ai.superficial()) {
			capAtPartial = true;
		}
		if (rigor.requiresMaterialTerminology() && !ai.usesMaterialTerminology()) {
			capAtPartial = true;
		}

		QuestionEvaluationStatus classification;
		if (ai.keywordList()) {
			// Lista de palavras-chave sem explicação nunca vale como resposta.
			classification = QuestionEvaluationStatus.INCORRECT;
			overall = Math.min(overall, 30);
		} else if (hasConceptualError && scores.get("conceptualPrecision") < PARTIAL_THRESHOLD) {
			classification = QuestionEvaluationStatus.INCORRECT;
		} else if (overall >= rigor.correctThreshold() && !capAtPartial) {
			classification = QuestionEvaluationStatus.CORRECT;
		} else if (overall >= PARTIAL_THRESHOLD) {
			classification = QuestionEvaluationStatus.PARTIAL;
		} else {
			classification = QuestionEvaluationStatus.INCORRECT;
		}

		return new Result(classification, overall, scores, ai.correctPoints(), ai.missingEssentialConcepts(),
				ai.missingTerms(), errors, ai.improvementTip(), manipulation);
	}
}
