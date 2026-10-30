package br.com.estudos.estudos_backend.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.chat.AiTutorProvider;
import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;

class AiQuestionGeneratorServiceTest {

	private void setupBlockWithMaterial(UUID blockId, StudyBlockService blockService, StudyMaterialRepository materialRepo) {
		StudyBlock block = new StudyBlock("Java", "Coleções", "Aprender");
		StudyMaterial material = new StudyMaterial(block, "Set vs List", "HashSet impede elementos duplicados.");
		when(blockService.get(blockId)).thenReturn(block);
		when(materialRepo.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyMaterialStatus.ACTIVE))
				.thenReturn(List.of(material));
	}

	@Test
	void rejectsInvalidProviderResponseWithBadGateway() {
		AiTutorProvider fakeProvider = new AiTutorProvider() {
			@Override public String providerName() { return "FakeProvider"; }
			@Override public boolean isConfigured() { return true; }
			@Override
			public AiTutorReply generateReply(String systemInstruction, String userPrompt) {
				return new AiTutorReply("Resposta não é JSON!", "FakeProvider", "fake-model");
			}
		};

		StudyBlockService blockService = Mockito.mock(StudyBlockService.class);
		StudyMaterialRepository materialRepo = Mockito.mock(StudyMaterialRepository.class);
		StudyQuestionService questionService = Mockito.mock(StudyQuestionService.class);

		UUID blockId = UUID.randomUUID();
		setupBlockWithMaterial(blockId, blockService, materialRepo);

		AiQuestionGeneratorService generator = new AiQuestionGeneratorService(
				fakeProvider, blockService, materialRepo, questionService);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> generator.generateFromMaterials(blockId, StudyQuestionDifficulty.EASY, StudyQuestionFormat.MULTIPLE_CHOICE));

		assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
		verify(questionService, never()).create(any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void rejectsInvalidOptionCountWithBadGateway() {
		AiTutorProvider fakeProvider = new AiTutorProvider() {
			@Override public String providerName() { return "FakeProvider"; }
			@Override public boolean isConfigured() { return true; }
			@Override
			public AiTutorReply generateReply(String systemInstruction, String userPrompt) {
				// True/False com 3 opções e 2 corretas -> deve ser rejeitado com 502 BAD_GATEWAY
				String json = """
						{
							"prompt": "Pergunta inválida",
							"options": [
								{"text": "A", "correct": true},
								{"text": "B", "correct": true},
								{"text": "C", "correct": false}
							]
						}
						""";
				return new AiTutorReply(json, "FakeProvider", "fake-model");
			}
		};

		StudyBlockService blockService = Mockito.mock(StudyBlockService.class);
		StudyMaterialRepository materialRepo = Mockito.mock(StudyMaterialRepository.class);
		StudyQuestionService questionService = Mockito.mock(StudyQuestionService.class);

		UUID blockId = UUID.randomUUID();
		setupBlockWithMaterial(blockId, blockService, materialRepo);

		AiQuestionGeneratorService generator = new AiQuestionGeneratorService(
				fakeProvider, blockService, materialRepo, questionService);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> generator.generateFromMaterials(blockId, StudyQuestionDifficulty.EASY, StudyQuestionFormat.TRUE_FALSE));

		assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
		verify(questionService, never()).create(any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void rejectsGeneratingQuestionsWhenBlockHasNoMaterials() {
		AiTutorProvider mockProvider = Mockito.mock(AiTutorProvider.class);
		StudyBlockService blockService = Mockito.mock(StudyBlockService.class);
		StudyMaterialRepository materialRepo = Mockito.mock(StudyMaterialRepository.class);
		StudyQuestionService questionService = Mockito.mock(StudyQuestionService.class);

		UUID blockId = UUID.randomUUID();
		StudyBlock block = new StudyBlock("Bloco Vazio", "Desc", "Meta");
		when(blockService.get(blockId)).thenReturn(block);
		when(materialRepo.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyMaterialStatus.ACTIVE))
				.thenReturn(List.of()); // Sem materiais

		AiQuestionGeneratorService generator = new AiQuestionGeneratorService(
				mockProvider, blockService, materialRepo, questionService);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> generator.generateFromMaterials(blockId, StudyQuestionDifficulty.EASY, StudyQuestionFormat.MULTIPLE_CHOICE));

		assertTrue(ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 422);
		verify(mockProvider, never()).generateReply(any(AiTutorProvider.AiTutorRequest.class));
	}
}
