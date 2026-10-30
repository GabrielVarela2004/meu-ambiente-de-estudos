package br.com.estudos.estudos_backend.question;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Serialização do snapshot da correção guardado na tentativa (notas por critério e feedback detalhado). */
final class EvaluationJson {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private EvaluationJson() {
	}

	static String criteria(DiscursiveEvaluationRules.Result result) {
		return write(result.criteriaScores());
	}

	static String details(DiscursiveEvaluationRules.Result result) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("correctPoints", result.correctPoints());
		details.put("missingEssentialConcepts", result.missingEssentialConcepts());
		details.put("missingTerms", result.missingTerms());
		details.put("errors", result.errors());
		details.put("improvementTip", result.improvementTip());
		details.put("manipulationDetected", result.manipulationDetected());
		return write(details);
	}

	/** Resumo legível guardado em evaluator_feedback (aparece no histórico). */
	static String summary(DiscursiveEvaluationRules.Result result) {
		StringBuilder sb = new StringBuilder(label(result.classification())).append(" — nota ")
				.append(result.overallScore()).append("/100.");
		if (!result.missingEssentialConcepts().isEmpty()) {
			sb.append(" Faltou: ").append(String.join("; ", result.missingEssentialConcepts())).append('.');
		}
		if (!result.errors().isEmpty()) {
			sb.append(" Erros: ").append(String.join("; ", result.errors())).append('.');
		}
		if (result.improvementTip() != null && !result.improvementTip().isBlank()) {
			sb.append(" Como melhorar: ").append(result.improvementTip());
		}
		return sb.toString();
	}

	static String label(QuestionEvaluationStatus status) {
		return switch (status) {
			case CORRECT -> "CORRETA";
			case PARTIAL -> "PARCIAL";
			case INCORRECT -> "INCORRETA";
			case PENDING_EVALUATION -> "PENDENTE DE AVALIAÇÃO";
			case UNGRADABLE -> "NÃO AVALIÁVEL";
		};
	}

	static Map<String, Integer> readCriteria(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Integer>>() {});
		} catch (JsonProcessingException e) {
			return Map.of();
		}
	}

	static Map<String, Object> readDetails(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
		} catch (JsonProcessingException e) {
			return Map.of();
		}
	}

	@SuppressWarnings("unchecked")
	static List<String> list(Map<String, Object> details, String key) {
		Object value = details.get(key);
		return value instanceof List<?> items ? (List<String>) items : List.of();
	}

	private static String write(Object value) {
		try {
			return MAPPER.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			return null;
		}
	}
}
