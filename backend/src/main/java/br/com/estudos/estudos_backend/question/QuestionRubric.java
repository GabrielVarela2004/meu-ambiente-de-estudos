package br.com.estudos.estudos_backend.question;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Rubrica de uma questão aberta, gerada junto com a pergunta e guardada antes de exibi-la.
 * Nunca é enviada ao frontend antes da resposta.
 */
public record QuestionRubric(
		List<String> essentialConcepts,
		List<String> complementaryConcepts,
		List<String> materialTerms,
		List<String> commonMistakes) {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public QuestionRubric {
		essentialConcepts = essentialConcepts == null ? List.of() : List.copyOf(essentialConcepts);
		complementaryConcepts = complementaryConcepts == null ? List.of() : List.copyOf(complementaryConcepts);
		materialTerms = materialTerms == null ? List.of() : List.copyOf(materialTerms);
		commonMistakes = commonMistakes == null ? List.of() : List.copyOf(commonMistakes);
	}

	public static QuestionRubric empty() {
		return new QuestionRubric(List.of(), List.of(), List.of(), List.of());
	}

	/** Lê a rubrica de um nó JSON (da IA ou do banco); listas ausentes viram vazias. */
	public static QuestionRubric fromJson(JsonNode node) {
		if (node == null || !node.isObject()) {
			return empty();
		}
		return new QuestionRubric(texts(node.path("essentialConcepts")), texts(node.path("complementaryConcepts")),
				texts(node.path("materialTerms")), texts(node.path("commonMistakes")));
	}

	public static QuestionRubric parse(String json) {
		if (json == null || json.isBlank()) {
			return empty();
		}
		try {
			return fromJson(MAPPER.readTree(json));
		} catch (JsonProcessingException e) {
			return empty();
		}
	}

	public String toJson() {
		ObjectNode node = MAPPER.createObjectNode();
		node.set("essentialConcepts", MAPPER.valueToTree(essentialConcepts));
		node.set("complementaryConcepts", MAPPER.valueToTree(complementaryConcepts));
		node.set("materialTerms", MAPPER.valueToTree(materialTerms));
		node.set("commonMistakes", MAPPER.valueToTree(commonMistakes));
		return node.toString();
	}

	static List<String> texts(JsonNode array) {
		List<String> values = new ArrayList<>();
		if (array != null && array.isArray()) {
			for (JsonNode item : array) {
				String text = item.asText("").strip();
				if (!text.isEmpty()) {
					values.add(text.length() <= 300 ? text : text.substring(0, 300));
				}
			}
		}
		return values;
	}
}
