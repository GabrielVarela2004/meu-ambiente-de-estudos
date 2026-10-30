package br.com.estudos.estudos_backend.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.chat.AiTutorProvider;
import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;

/** Geração da área teórica: só questões abertas, com resposta-modelo e rubrica fundamentadas no material. */
class AiOpenQuestionGenerationTest {

	private static final String MATERIAL = "O HashSet impede elementos duplicados porque usa hashCode e equals para "
			+ "comparar os objetos antes de inserir. Duas instâncias iguais segundo equals ocupam uma única posição.";

	private final UUID blockId = UUID.randomUUID();
	private final StudyBlockService blockService = mock(StudyBlockService.class);
	private final StudyMaterialRepository materialRepository = mock(StudyMaterialRepository.class);
	private final StudyQuestionService questionService = mock(StudyQuestionService.class);
	private String capturedPrompt;

	@BeforeEach
	void setUp() {
		StudyBlock block = new StudyBlock("Java", "Coleções", "Aprender");
		when(blockService.get(blockId)).thenReturn(block);
		when(materialRepository.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyMaterialStatus.ACTIVE))
				.thenReturn(List.of(new StudyMaterial(block, "Set vs List", MATERIAL)));
		when(questionService.create(eq(blockId), any(), any(), any(), any(), any(), any(), any(), any(), any()))
				.thenAnswer(inv -> {
					StudyQuestion question = new StudyQuestion(block, null, inv.getArgument(4), inv.getArgument(5),
							inv.getArgument(6), inv.getArgument(7), inv.getArgument(8));
					ReflectionTestUtils.setField(question, "id", UUID.randomUUID());
					return question;
				});
		when(questionService.attachRubric(eq(blockId), any(), anyString(), anyString(), any()))
				.thenAnswer(inv -> mock(StudyQuestion.class));
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
		return new AiQuestionGeneratorService(provider, blockService, materialRepository, questionService);
	}

	private static String openQuestion(String excerpt, String essentials) {
		return """
				{"prompt":"Explique por que o HashSet não aceita elementos duplicados.",
				 "modelAnswer":"Porque o HashSet usa hashCode e equals para comparar os objetos antes de inserir; objetos iguais ocupam uma única posição.",
				 "explanation":"O material explica a comparação por hashCode e equals.",
				 "rubric":{"essentialConcepts":%s,"complementaryConcepts":["posição única"],
				           "materialTerms":["hashCode","equals"],"commonMistakes":["achar que compara só por referência"]},
				 "sourceExcerpt":"%s","sourceTimestamp":"03:15"}
				""".formatted(essentials, excerpt);
	}

	@Test
	void objectiveFormatRequestStillCreatesOpenQuestionWithModelAnswerAndRubric() {
		generatorReplying(openQuestion("usa hashCode e equals para comparar os objetos antes de inserir",
				"[\"hashCode e equals\",\"comparação antes de inserir\"]"))
				.generateSimulado(blockId, 1, StudyQuestionDifficulty.INTERMEDIATE, StudyQuestionFormat.MULTIPLE_CHOICE, null);

		assertTrue(capturedPrompt.contains("Formato exigido: DISCURSIVE"));
		assertTrue(capturedPrompt.contains("Cada pergunta exige explicação"));
		verify(questionService).create(eq(blockId), any(), any(),
				eq("usa hashCode e equals para comparar os objetos antes de inserir"),
				eq("Explique por que o HashSet não aceita elementos duplicados."), any(),
				eq(StudyQuestionDifficulty.INTERMEDIATE), eq(StudyQuestionFormat.DISCURSIVE), anyString(), eq(List.of()));

		ArgumentCaptor<String> rubric = ArgumentCaptor.forClass(String.class);
		verify(questionService).attachRubric(eq(blockId), any(),
				eq("Porque o HashSet usa hashCode e equals para comparar os objetos antes de inserir; objetos iguais ocupam uma única posição."),
				rubric.capture(), eq("03:15"));
		QuestionRubric saved = QuestionRubric.parse(rubric.getValue());
		assertEquals(List.of("hashCode e equals", "comparação antes de inserir"), saved.essentialConcepts());
		assertEquals(List.of("hashCode", "equals"), saved.materialTerms());
	}

	@Test
	void questionWhoseExcerptIsNotInTheMaterialIsDiscarded() {
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> generatorReplying(
				openQuestion("o TreeSet mantém os elementos ordenados por uma árvore rubro-negra", "[\"árvore\"]"))
				.generateSimulado(blockId, 1, StudyQuestionDifficulty.EASY, StudyQuestionFormat.DISCURSIVE, null));

		assertEquals(502, ex.getStatusCode().value());
		verify(questionService, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void questionWithoutEssentialConceptsIsDiscarded() {
		assertThrows(ResponseStatusException.class, () -> generatorReplying(
				openQuestion("usa hashCode e equals para comparar os objetos antes de inserir", "[]"))
				.generateSimulado(blockId, 1, StudyQuestionDifficulty.EASY, StudyQuestionFormat.DISCURSIVE, null));
	}

	@Test
	void groundingAcceptsLiteralOrNearlyLiteralExcerpts() {
		StudyMaterial material = new StudyMaterial(new StudyBlock("B", "D", "G"), "M", MATERIAL);
		assertTrue(AiQuestionGeneratorService.isGroundedInMaterials(
				"Duas instâncias iguais segundo equals ocupam uma única posição", List.of(material)));
		assertTrue(AiQuestionGeneratorService.isGroundedInMaterials(
				"duas instancias iguais segundo EQUALS ocupam uma unica posicao!", List.of(material)));
		assertTrue(!AiQuestionGeneratorService.isGroundedInMaterials("curto", List.of(material)));
	}
}
