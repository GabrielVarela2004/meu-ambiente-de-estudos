package br.com.estudos.estudos_backend.exercise;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Conversão entre a lista de etapas do exercício e o JSON armazenado em {@code steps_json}. */
final class PracticalExerciseSteps {

	static final int MAX_STEPS = 8;
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private PracticalExerciseSteps() {}

	static List<String> parse(String stepsJson) {
		if (stepsJson == null || stepsJson.isBlank()) {
			return List.of();
		}
		try {
			return fromNode(MAPPER.readTree(stepsJson));
		} catch (Exception e) {
			return List.of();
		}
	}

	/** Aceita um array JSON de textos (ou um texto contendo esse array, como alguns modelos devolvem). */
	static List<String> fromNode(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return List.of();
		}
		if (node.isTextual()) {
			return parse(node.asText());
		}
		List<String> steps = new ArrayList<>();
		if (node.isArray()) {
			for (JsonNode item : node) {
				String text = item.isTextual() ? item.asText() : item.path("description").asText(item.path("title").asText(""));
				if (!text.isBlank() && steps.size() < MAX_STEPS) {
					steps.add(text.strip());
				}
			}
		}
		return steps;
	}

	static String toJson(List<String> steps) {
		try {
			return MAPPER.writeValueAsString(steps == null ? List.of() : steps);
		} catch (Exception e) {
			return "[]";
		}
	}
}
