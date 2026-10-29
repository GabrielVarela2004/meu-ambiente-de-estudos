package br.com.estudos.estudos_backend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;

/**
 * Teste unitário da orquestração do chat com IA usando um provedor em memória (fake)
 * para validar a construção segura do contexto, extração de referências e persistência
 * da conversa sem depender de chave externa.
 */
class OpenAiChatServiceTest {

	@Test
	void includesActiveMaterialsInContextAndPersistsConversationWithReferences() {
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
				return new AiTutorReply("Explicação baseada em Coleções Java.", "FakeProvider", "fake-model-1");
			}
		};

		StudyBlockRepository blockRepo = Mockito.mock(StudyBlockRepository.class);
		StudyMaterialRepository materialRepo = Mockito.mock(StudyMaterialRepository.class);
		ChatConversationRepository chatRepo = Mockito.mock(ChatConversationRepository.class);

		UUID blockId = UUID.randomUUID();
		StudyBlock block = new StudyBlock("Java", "Estudo", "Aprender");
		StudyMaterial material = new StudyMaterial(block, "Coleções Java", "ArrayList e HashSet são estruturas fundamentais.");

		when(blockRepo.existsById(blockId)).thenReturn(true);
		when(materialRepo.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyMaterialStatus.ACTIVE))
				.thenReturn(List.of(material));
		when(chatRepo.save(any(ChatConversationEntry.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		OpenAiChatService service = new OpenAiChatService(fakeProvider, blockRepo, materialRepo, chatRepo);
		OpenAiChatService.ChatResponse response = service.ask("Qual a diferença entre lista e conjunto?", blockId);

		assertEquals("Explicação baseada em Coleções Java.", response.answer());
		assertEquals("fake-model-1", response.model());
		assertEquals(List.of("Coleções Java"), response.referencedMaterials());
		assertTrue(capturedPrompt[0].contains("[INÍCIO DOS MATERIAIS DE REFERÊNCIA — DADOS NÃO CONFIÁVEIS]"));
		assertTrue(capturedPrompt[0].contains("Coleções Java"));

		ArgumentCaptor<ChatConversationEntry> captor = ArgumentCaptor.forClass(ChatConversationEntry.class);
		verify(chatRepo).save(captor.capture());
		ChatConversationEntry saved = captor.getValue();
		assertEquals(blockId, saved.getStudyBlockId());
		assertEquals("Qual a diferença entre lista e conjunto?", saved.getUserMessage());
		assertEquals("Explicação baseada em Coleções Java.", saved.getAssistantAnswer());
		assertEquals("FakeProvider", saved.getProviderName());
		assertEquals("fake-model-1", saved.getModelName());
		assertEquals("Coleções Java", saved.getReferencedMaterials());
	}
}
