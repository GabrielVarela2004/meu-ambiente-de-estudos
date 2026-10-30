package br.com.estudos.estudos_backend.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.chat.AiTutorProvider;
import br.com.estudos.estudos_backend.chat.SourceContextSelector;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopic;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicRepository;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicStatus;
import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;

/** Geração variada (perguntas existentes no prompt) e vínculo de toda questão gerada a um tópico. */
class AiQuestionGenerationVarietyTest {

	private final UUID blockId = UUID.randomUUID();
	private final StudyBlockService blockService = mock(StudyBlockService.class);
	private final StudyMaterialRepository materialRepository = mock(StudyMaterialRepository.class);
	private final StudyQuestionService questionService = mock(StudyQuestionService.class);
	private final KnowledgeTopicRepository topicRepository = mock(KnowledgeTopicRepository.class);
	private KnowledgeTopic polymorphism;
	private KnowledgeTopic encapsulation;
	private String capturedPrompt;

	@BeforeEach
	void setUp() {
		StudyBlock block = new StudyBlock("POO", "Orientação a objetos", "Aprender");
		StudyMaterial material = new StudyMaterial(block, "Apostila de POO",
				"Polimorfismo permite tratar objetos de subclasses como da superclasse. "
						+ "Encapsulamento protege o estado interno expondo apenas métodos públicos.");
		when(blockService.get(blockId)).thenReturn(block);
		when(materialRepository.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyMaterialStatus.ACTIVE))
				.thenReturn(List.of(material));

		polymorphism = topic(block, "Polimorfismo", "Objetos de subclasses tratados como da superclasse");
		encapsulation = topic(block, "Encapsulamento", "Proteção do estado interno do objeto");
		when(topicRepository.findAllByStudyBlockIdAndStatusOrderByCreatedAtAsc(blockId, KnowledgeTopicStatus.ACTIVE))
				.thenReturn(List.of(polymorphism, encapsulation));
		when(questionService.recentPrompts(eq(blockId), isNull(), anyInt()))
				.thenReturn(List.of("Explique o que é herança em Java."));
	}

	private static KnowledgeTopic topic(StudyBlock block, String title, String description) {
		KnowledgeTopic topic = new KnowledgeTopic(block, null, title, description);
		ReflectionTestUtils.setField(topic, "id", UUID.randomUUID());
		return topic;
	}

	private AiQuestionGeneratorService generatorReplying(String json) {
		AiTutorProvider provider = new AiTutorProvider() {
			@Override public String providerName() { return "Fake"; }
			@Override public boolean isConfigured() { return true; }
			@Override
			public AiTutorReply generateReply(String systemInstruction, String userPrompt) {
				capturedPrompt = userPrompt;
				return new AiTutorReply(json, "Fake", "fake-model");
			}
		};
		return new AiQuestionGeneratorService(provider, blockService, materialRepository, questionService,
				topicRepository, new SourceContextSelector());
	}

	@Test
	void promptCarriesExistingQuestionsAndBlockTopics() {
		generatorReplying("""
				{"topic":"Encapsulamento","prompt":"Por que o encapsulamento protege o estado do objeto?",
				 "explanation":"Expõe só métodos públicos.","sourceExcerpt":"Encapsulamento protege o estado interno",
				 "options":[{"text":"Expõe só métodos públicos","correct":true},{"text":"Duplica atributos","correct":false}]}
				""").generateSimulado(blockId, 1, StudyQuestionDifficulty.INTERMEDIATE, StudyQuestionFormat.MULTIPLE_CHOICE, null);

		assertTrue(capturedPrompt.contains("[PERGUNTAS JÁ EXISTENTES"));
		assertTrue(capturedPrompt.contains("Explique o que é herança em Java."));
		assertTrue(capturedPrompt.contains("- Polimorfismo"));
		assertTrue(capturedPrompt.contains("- Encapsulamento"));
		assertTrue(capturedPrompt.contains("\"topic\""));
	}

	@Test
	void generatedQuestionIsLinkedToTheTopicNamedByTheAi() {
		generatorReplying("""
				{"topic":"encapsulamento","prompt":"Por que o encapsulamento protege o estado do objeto?",
				 "explanation":"Expõe só métodos públicos.","sourceExcerpt":"Encapsulamento protege o estado interno",
				 "options":[{"text":"Expõe só métodos públicos","correct":true},{"text":"Duplica atributos","correct":false}]}
				""").generateSimulado(blockId, 1, StudyQuestionDifficulty.INTERMEDIATE, StudyQuestionFormat.MULTIPLE_CHOICE, null);

		verify(questionService).create(eq(blockId), any(), eq(encapsulation.getId()), any(), any(), any(),
				eq(StudyQuestionDifficulty.INTERMEDIATE), eq(StudyQuestionFormat.MULTIPLE_CHOICE), any(), any());
	}

	@Test
	void unknownTopicFromAiFallsBackToMostSimilarTopic() {
		assertEquals(polymorphism.getId(), AiQuestionGeneratorService.resolveTopicId("Herança e subtipos",
				"Como o polimorfismo permite tratar subclasses como a superclasse?", List.of(polymorphism, encapsulation)));
		assertNull(AiQuestionGeneratorService.resolveTopicId("", "texto sem relação", List.of()));
	}

	@Test
	void invalidJsonFromTheAiIsBadGateway() {
		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> generatorReplying("isto não é JSON").generateSimulado(blockId, 1, StudyQuestionDifficulty.EASY,
						StudyQuestionFormat.MULTIPLE_CHOICE, null));
		assertEquals(502, ex.getStatusCode().value());
	}

	@Test
	void sourceContentGoesDelimitedAsUntrustedData() {
		generatorReplying("""
				{"topic":"Polimorfismo","prompt":"Explique o polimorfismo.","explanation":"x","sourceExcerpt":"Polimorfismo",
				 "options":[{"text":"a","correct":true},{"text":"b","correct":false}]}
				""").generateSimulado(blockId, 1, StudyQuestionDifficulty.EASY, StudyQuestionFormat.MULTIPLE_CHOICE, null);

		assertTrue(capturedPrompt.contains("Polimorfismo permite tratar objetos de subclasses"));
		assertTrue(capturedPrompt.contains("NÃO CONFIÁVEIS") || capturedPrompt.contains("não confiáveis"),
				"o conteúdo das fontes precisa ir marcado como dado não confiável");
		assertFalse(capturedPrompt.isBlank());
	}
}
