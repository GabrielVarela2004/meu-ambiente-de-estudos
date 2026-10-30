package br.com.estudos.estudos_backend.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.chat.AiTutorProvider;
import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;

/** Extração e revisão de tópicos com a IA simulada: filtros do backend e memória das decisões do usuário. */
class SourceTopicExtractorReviewTest {

	private static final String CONTENT = "Tipos primitivos guardam valores simples como int, double e boolean. "
			+ "Classes abstratas não podem ser instanciadas e declaram métodos abstratos para as subclasses.";

	private final StudyBlock block = new StudyBlock("Java", "Fundamentos", "Aprender");
	private final StudyMaterial material = new StudyMaterial(block, "Aula 1", CONTENT);
	private AiTutorProvider.AiTutorRequest lastRequest;

	private SourceTopicExtractor extractorReplying(String json) {
		ReflectionTestUtils.setField(material, "id", UUID.randomUUID());
		return new SourceTopicExtractor(new AiTutorProvider() {
			@Override public String providerName() { return "Fake"; }
			@Override public boolean isConfigured() { return true; }
			@Override public AiTutorReply generateReply(String systemInstruction, String userPrompt) {
				return generateReply(AiTutorRequest.ofText(systemInstruction, userPrompt));
			}
			@Override public AiTutorReply generateReply(AiTutorRequest request) {
				lastRequest = request;
				return new AiTutorReply(json, "Fake", "fake-model");
			}
		});
	}

	@Test
	void keepsOnlyTechnicalGroundedAndRelevantTopicsAndSendsTheReviewMemory() {
		String reply = """
				[{"title":"Tipos primitivos","supportingExcerpt":"Tipos primitivos guardam valores simples","relevance":92,
				  "relevanceReason":"Base da linguagem",
				  "subtopics":[{"title":"int e double","relevance":80},{"title":"Curiosidades","relevance":20}]},
				 {"title":"Bem-vindos ao nosso canal","relevance":70},
				 {"title":"Generics","supportingExcerpt":"Generics permitem parametrizar tipos em tempo de compilação","relevance":85},
				 {"title":"Ponteiros","supportingExcerpt":"Tipos primitivos guardam valores simples","relevance":75},
				 {"title":"Classes abstratas","supportingExcerpt":"Classes abstratas não podem ser instanciadas"}]
				""";
		SourceTopicExtractor.ReviewMemory memory = new SourceTopicExtractor.ReviewMemory(
				List.of("Ponteiros"), List.of("\"Herança\": separe interfaces de classes abstratas"));

		List<SourceTopicExtractor.ExtractedTopic> topics = extractorReplying(reply)
				.extractFromMaterials(block, List.of(material), memory);

		// Saudação (ruído), trecho inventado (Generics) e tópico já rejeitado (Ponteiros) ficam de fora.
		assertEquals(List.of("Tipos primitivos", "Classes abstratas"),
				topics.stream().map(SourceTopicExtractor.ExtractedTopic::title).toList());
		assertEquals(92, topics.get(0).relevance());
		assertEquals("Base da linguagem", topics.get(0).relevanceReason());
		// Subtópico abaixo da relevância mínima é descartado.
		assertEquals(List.of("int e double"),
				topics.get(0).subtopics().stream().map(SourceTopicExtractor.ExtractedSubtopic::title).toList());
		// Sem relevância informada, assume o padrão.
		assertEquals(60, topics.get(1).relevance());

		assertTrue(lastRequest.jsonObjectMode());
		assertTrue(lastRequest.maxOutputTokens() >= 4_000, "limite explícito para o JSON não ser cortado");
		assertTrue(lastRequest.userPrompt().contains("JÁ REJEITOU"));
		assertTrue(lastRequest.userPrompt().contains("- Ponteiros"));
		assertTrue(lastRequest.userPrompt().contains("separe interfaces de classes abstratas"));
		assertTrue(lastRequest.systemInstruction().contains("Ignore saudações"));
	}

	@Test
	void refineAppliesTheRevisionAndOnlyAcceptsGroundedNewSubtopics() {
		UUID id = UUID.randomUUID();
		String reply = """
				[{"id":"%s","title":"Tipos primitivos em Java","description":"int, double, boolean e char",
				  "supportingExcerpt":"trecho que não existe na fonte","relevance":95,
				  "newSubtopics":[{"title":"boolean","supportingExcerpt":"valores simples como int, double e boolean","relevance":80},
				                  {"title":"Inventado","supportingExcerpt":"nada disso está no material da aula","relevance":90}]},
				 {"id":"%s","title":"Intruso"}]
				""".formatted(id, UUID.randomUUID());

		List<SourceTopicExtractor.RevisedTopic> revised = extractorReplying(reply).refine(block, List.of(material),
				List.of(new SourceTopicExtractor.RefineRequest(id, "Tipos primitivos", null, null, "Inclua char")));

		assertEquals(1, revised.size(), "só os tópicos enviados para revisão são aceitos");
		assertEquals("Tipos primitivos em Java", revised.get(0).title());
		assertEquals(95, revised.get(0).relevance());
		assertNull(revised.get(0).supportingExcerpt(), "trecho fora da fonte mantém o anterior");
		assertEquals(List.of("boolean"),
				revised.get(0).newSubtopics().stream().map(SourceTopicExtractor.ExtractedSubtopic::title).toList());
		assertTrue(lastRequest.userPrompt().contains("comentário do usuário: Inclua char"));
	}

	@Test
	void refineWithoutAiIsUnavailable() {
		SourceTopicExtractor withoutAi = new SourceTopicExtractor(null);
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> withoutAi.refine(block,
				List.of(material), List.of(new SourceTopicExtractor.RefineRequest(UUID.randomUUID(), "t", null, null, "f"))));
		assertEquals(503, ex.getStatusCode().value());
	}

	@Test
	void noiseDetectionIgnoresAccentsAndCase() {
		assertTrue(SourceTopicExtractor.isNoise("Olá Pessoal, tudo bem?"));
		assertTrue(SourceTopicExtractor.isNoise("Nesta aula vamos ver herança"));
		assertTrue(!SourceTopicExtractor.isNoise("Herança e polimorfismo"));
	}
}
