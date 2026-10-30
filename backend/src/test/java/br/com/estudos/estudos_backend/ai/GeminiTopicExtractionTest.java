package br.com.estudos.estudos_backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.knowledge.SourceTopicExtractor;
import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;

/**
 * Extração de tópicos de ponta a ponta com o Gemini como provedor: extrator → roteador → GeminiModelProvider,
 * com só o HTTP simulado no formato real da API (candidates/content/parts/text).
 */
class GeminiTopicExtractionTest {

	private static final String TOPICS = """
			[{"title":"Polimorfismo","description":"Subclasses tratadas como a superclasse",
			  "supportingExcerpt":"Polimorfismo permite tratar objetos de subclasses",
			  "subtopics":[{"title":"Sobrescrita de métodos","description":"Mesma assinatura na subclasse"}]},
			 {"title":"Encapsulamento","description":"Proteção do estado interno"}]
			""";

	@SuppressWarnings("unchecked")
	private SourceTopicExtractor extractorWithGeminiReplying(String modelText) throws Exception {
		String body = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":"
				+ new ObjectMapper().writeValueAsString(modelText) + "}]},\"finishReason\":\"STOP\"}]}";
		HttpResponse<String> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn(body);
		HttpClient http = mock(HttpClient.class);
		when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

		AiProviderSettingsRepository settingsRepository = mock(AiProviderSettingsRepository.class);
		AiOperationRouteRepository routeRepository = mock(AiOperationRouteRepository.class);
		AiUsageRecordRepository usageRepository = mock(AiUsageRecordRepository.class);
		when(settingsRepository.findById(anyString())).thenReturn(Optional.empty());
		when(routeRepository.findById(any())).thenReturn(Optional.empty());
		when(usageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		GeminiModelProvider gemini = new GeminiModelProvider(GeminiProperties.withKey("chave-falsa"), http);
		AiSettingsService settings = new AiSettingsService(List.of(gemini), settingsRepository, routeRepository,
				usageRepository, "gemini");
		return new SourceTopicExtractor(new AiProviderRouter(settings, new AiUsageService(usageRepository)));
	}

	private static List<StudyMaterial> materials() {
		StudyBlock block = new StudyBlock("POO", "Orientação a objetos", "Aprender");
		return List.of(new StudyMaterial(block, "Apostila",
				"Polimorfismo permite tratar objetos de subclasses como da superclasse. "
						+ "Encapsulamento protege o estado interno expondo apenas métodos públicos."));
	}

	@ParameterizedTest
	@ValueSource(strings = {"lista pura", "objeto com topics", "bloco markdown"})
	void extractsTopicsAndSubtopicsFromGeminiReply(String shape) throws Exception {
		String modelText = switch (shape) {
			case "objeto com topics" -> "{\"topics\":" + TOPICS + "}";
			case "bloco markdown" -> "```json\n" + TOPICS + "\n```";
			default -> TOPICS;
		};
		List<StudyMaterial> materials = materials();

		List<SourceTopicExtractor.ExtractedTopic> topics = extractorWithGeminiReplying(modelText)
				.extractFromMaterials(materials.get(0).getStudyBlock(), materials);

		assertEquals(List.of("Polimorfismo", "Encapsulamento"),
				topics.stream().map(SourceTopicExtractor.ExtractedTopic::title).toList());
		assertTrue(topics.get(0).subtopics().stream().anyMatch(s -> s.title().equals("Sobrescrita de métodos")));
	}

	@Test
	void invalidJsonFromGeminiFallsBackToDeterministicExtraction() throws Exception {
		List<StudyMaterial> materials = materials();

		List<SourceTopicExtractor.ExtractedTopic> topics = extractorWithGeminiReplying("não é JSON")
				.extractFromMaterials(materials.get(0).getStudyBlock(), materials);

		assertTrue(!topics.isEmpty(), "sem JSON válido, a extração determinística a partir do texto assume");
	}
}
