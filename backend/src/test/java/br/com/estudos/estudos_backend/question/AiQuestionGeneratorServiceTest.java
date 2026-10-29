package br.com.estudos.estudos_backend.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import br.com.estudos.estudos_backend.chat.AiTutorProvider;
import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;

class AiQuestionGeneratorServiceTest {

	@Test
	void generatesAndSavesQuestionFromStudyBlockMaterialsViaProvider() {
		final String[] capturedPrompt = new String[1];
		AiTutorProvider fakeProvider = new AiTutorProvider() {
			@Override
			public String providerName() {
				return "FakeProvider";
			}

			@Override
			public boolean isConfigured() {
				return true;
			}

			@Override
			public AiTutorReply generateReply(String systemInstruction, String userPrompt) {
				capturedPrompt[0] = userPrompt;
				String json = """
						```json
						{
							"prompt": "Qual estrutura garante elementos únicos em Java?",
							"explanation": "HashSet implementa Set e impede duplicatas.",
							"options": [
								{"text": "HashSet", "correct": true},
								{"text": "ArrayList", "correct": false}
							]
						}
						```
						""";
				return new AiTutorReply(json, "FakeProvider", "fake-model");
			}
		};

		StudyBlockService blockService = Mockito.mock(StudyBlockService.class);
		StudyMaterialRepository materialRepo = Mockito.mock(StudyMaterialRepository.class);
		StudyQuestionService questionService = Mockito.mock(StudyQuestionService.class);

		UUID blockId = UUID.randomUUID();
		StudyBlock block = new StudyBlock("Java", "Coleções", "Aprender");
		StudyMaterial material = new StudyMaterial(block, "Set vs List", "HashSet impede elementos duplicados.");

		when(blockService.get(blockId)).thenReturn(block);
		when(materialRepo.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyMaterialStatus.ACTIVE))
				.thenReturn(List.of(material));

		StudyQuestion expectedSaved = new StudyQuestion(
				block, material, "Qual estrutura garante elementos únicos em Java?",
				"HashSet implementa Set e impede duplicatas.",
				StudyQuestionDifficulty.HARD, StudyQuestionFormat.MULTIPLE_CHOICE, null);
		when(questionService.create(eq(blockId), any(), any(), any(),
				eq(StudyQuestionDifficulty.HARD), eq(StudyQuestionFormat.MULTIPLE_CHOICE), any(), any()))
				.thenReturn(expectedSaved);

		AiQuestionGeneratorService generator = new AiQuestionGeneratorService(
				fakeProvider, blockService, materialRepo, questionService);

		StudyQuestion created = generator.generateFromMaterials(
				blockId, StudyQuestionDifficulty.HARD, StudyQuestionFormat.MULTIPLE_CHOICE);

		assertEquals("Qual estrutura garante elementos únicos em Java?", created.getPrompt());
		assertEquals(StudyQuestionDifficulty.HARD, created.getDifficulty());
		assertTrue(capturedPrompt[0].contains("Set vs List"));
		assertTrue(capturedPrompt[0].contains("HashSet impede elementos duplicados."));
		verify(questionService).create(
				eq(blockId),
				any(),
				eq("Qual estrutura garante elementos únicos em Java?"),
				eq("HashSet implementa Set e impede duplicatas."),
				eq(StudyQuestionDifficulty.HARD),
				eq(StudyQuestionFormat.MULTIPLE_CHOICE),
				eq(null),
				any());
	}
}
