package br.com.estudos.estudos_backend.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;

/** Geração variada: as perguntas já existentes do bloco vão no prompt para a IA não repeti-las. */
class AiQuestionGenerationVarietyTest {

	private final UUID blockId = UUID.randomUUID();
	private final StudyBlockService blockService = mock(StudyBlockService.class);
	private final StudyMaterialRepository materialRepository = mock(StudyMaterialRepository.class);
	private final StudyQuestionService questionService = mock(StudyQuestionService.class);
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

		when(questionService.recentPrompts(eq(blockId), anyInt()))
				.thenReturn(List.of("Explique o que é herança em Java."));
		when(questionService.create(eq(blockId), any(), any(), any(), any(), any(), any(), any(), any()))
				.thenAnswer(inv -> {
					StudyQuestion question = new StudyQuestion(block, null, inv.getArgument(3), null,
							inv.getArgument(5), inv.getArgument(6), inv.getArgument(7));
					ReflectionTestUtils.setField(question, "id", UUID.randomUUID());
					return question;
				});
		when(questionService.attachRubric(eq(blockId), any(), any(), any(), any())).thenAnswer(inv -> mock(StudyQuestion.class));
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
				new SourceContextSelector());
	}

	@Test
	void promptCarriesExistingQuestionsAndCoversTheWholeBlock() {
		generatorReplying("""
				{"prompt":"Explique como o encapsulamento protege o estado do objeto.",
				 "modelAnswer":"Protegendo o estado interno e expondo apenas métodos públicos.",
				 "rubric":{"essentialConcepts":["estado interno protegido"]},
				 "sourceExcerpt":"Encapsulamento protege o estado interno expondo apenas métodos públicos"}
				""").generateSimulado(blockId, 1, StudyQuestionDifficulty.INTERMEDIATE, StudyQuestionFormat.MULTIPLE_CHOICE);

		assertTrue(capturedPrompt.contains("[PERGUNTAS JÁ EXISTENTES"));
		assertTrue(capturedPrompt.contains("Explique o que é herança em Java."));
		assertTrue(capturedPrompt.contains("Abranja os conceitos mais relevantes disponíveis nas fontes do bloco."));
		assertFalse(capturedPrompt.contains("\"topic\""), "a questão não é mais vinculada a tópicos");
		verify(questionService).create(eq(blockId), any(), any(), any(), any(),
				eq(StudyQuestionDifficulty.INTERMEDIATE), eq(StudyQuestionFormat.DISCURSIVE), any(), any());
	}

	@Test
	void invalidJsonFromTheAiIsBadGateway() {
		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> generatorReplying("isto não é JSON").generateSimulado(blockId, 1, StudyQuestionDifficulty.EASY,
						StudyQuestionFormat.MULTIPLE_CHOICE));
		assertEquals(502, ex.getStatusCode().value());
	}

	@Test
	void sourceContentGoesDelimitedAsUntrustedData() {
		generatorReplying("""
				{"prompt":"Explique o polimorfismo com um exemplo.","modelAnswer":"Tratar subclasses como a superclasse.",
				 "rubric":{"essentialConcepts":["subclasse tratada como superclasse"]},
				 "sourceExcerpt":"Polimorfismo permite tratar objetos de subclasses como da superclasse"}
				""").generateSimulado(blockId, 1, StudyQuestionDifficulty.EASY, StudyQuestionFormat.MULTIPLE_CHOICE);

		assertTrue(capturedPrompt.contains("Polimorfismo permite tratar objetos de subclasses"));
		assertTrue(capturedPrompt.contains("NÃO CONFIÁVEIS") || capturedPrompt.contains("não confiáveis"),
				"o conteúdo das fontes precisa ir marcado como dado não confiável");
		assertFalse(capturedPrompt.isBlank());
	}
}
