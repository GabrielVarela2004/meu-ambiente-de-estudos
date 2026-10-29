package br.com.estudos.estudos_backend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialProcessingStatus;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialSourceType;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;

/**
 * Testes unitários da orquestração do chat com IA, seleção de materiais de estudo,
 * envio de histórico recente (multi-turn) e proteção contra acesso indevido entre blocos.
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

			@Override
			public AiTutorReply generateReply(AiTutorRequest request) {
				capturedPrompt[0] = request.userPrompt();
				return new AiTutorReply("Explicação baseada em Coleções Java.", "FakeProvider", "fake-model-1");
			}
		};

		StudyBlockRepository blockRepo = Mockito.mock(StudyBlockRepository.class);
		StudyMaterialRepository materialRepo = Mockito.mock(StudyMaterialRepository.class);
		ChatConversationRepository chatRepo = Mockito.mock(ChatConversationRepository.class);

		UUID blockId = UUID.randomUUID();
		StudyBlock block = new StudyBlock("Java", "Estudo", "Aprender");
		StudyMaterial material = new StudyMaterial(
				block,
				"Coleções Java",
				"ArrayList e HashSet são estruturas fundamentais.",
				StudyMaterialSourceType.FILE_PDF,
				null,
				null,
				null,
				StudyMaterialProcessingStatus.COMPLETED,
				null);

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
		assertEquals(1, response.referencedSources().size());
		assertEquals(StudyMaterialSourceType.FILE_PDF, response.referencedSources().get(0).sourceType());
		assertTrue(capturedPrompt[0].contains("[INÍCIO DOS MATERIAIS DE REFERÊNCIA — DADOS NÃO CONFIÁVEIS]"));
		assertTrue(capturedPrompt[0].contains("Coleções Java"));
		assertTrue(capturedPrompt[0].contains("Tipo: FILE_PDF"));

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

	@Test
	void selectsRelevantChunkFromLongYoutubeTranscriptAndRanksMatchingSourcesFirst() {
		StudyBlock block = new StudyBlock("Java", "Estudo", "Aprender");

		String filler = "Introdução geral à sintaxe básica de variáveis e operadores aritméticos em Java. ".repeat(180);
		String targetSection = " O Garbage Collector ZGC (Z Garbage Collector) realiza coleta concorrente com pausas sub-milissegundo em heaps grandes.";
		String longTranscript = filler + targetSection;

		StudyMaterial unrelatedNote = new StudyMaterial(
				block,
				"Anotação de CSS",
				"Flexbox e Grid Layout servem para alinhar elementos na página web.",
				StudyMaterialSourceType.TEXT,
				null,
				null,
				null,
				StudyMaterialProcessingStatus.COMPLETED,
				null);

		StudyMaterial youtubeMaterial = new StudyMaterial(
				block,
				"Aula Avançada de JVM e Memória",
				longTranscript,
				StudyMaterialSourceType.YOUTUBE,
				"https://www.youtube.com/watch?v=dQw4w9WgXcQ",
				"dQw4w9WgXcQ",
				"pt-BR",
				StudyMaterialProcessingStatus.COMPLETED,
				null);

		SourceContextSelector selector = new SourceContextSelector();
		SourceContextSelector.SelectedContext selected = selector.selectContext(
				"Como funciona o Garbage Collector ZGC na JVM?",
				List.of(unrelatedNote, youtubeMaterial),
				null);

		assertEquals("Aula Avançada de JVM e Memória", selected.referencedTitles().get(0));
		assertEquals(StudyMaterialSourceType.YOUTUBE, selected.referencedSources().get(0).sourceType());
		assertTrue(selected.formattedContextSection().contains("Z Garbage Collector"));
		assertTrue(selected.formattedContextSection().contains("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
	}

	@Test
	void conversaSemMateriaisNoBlocoGeraAvisoNoPromptEContinua() {
		final String[] capturedPrompt = new String[1];
		AiTutorProvider fakeProvider = new AiTutorProvider() {
			@Override
			public String providerName() { return "FakeProvider"; }
			@Override
			public boolean isConfigured() { return true; }
			@Override
			public AiTutorReply generateReply(String systemInstruction, String userPrompt) {
				return generateReply(AiTutorRequest.ofText(systemInstruction, userPrompt));
			}
			@Override
			public AiTutorReply generateReply(AiTutorRequest request) {
				capturedPrompt[0] = request.userPrompt();
				return new AiTutorReply("Resposta sem fontes específicas.", "FakeProvider", "fake-model");
			}
		};

		StudyBlockRepository blockRepo = Mockito.mock(StudyBlockRepository.class);
		StudyMaterialRepository materialRepo = Mockito.mock(StudyMaterialRepository.class);
		ChatConversationRepository chatRepo = Mockito.mock(ChatConversationRepository.class);

		UUID blockId = UUID.randomUUID();
		when(blockRepo.existsById(blockId)).thenReturn(true);
		when(materialRepo.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyMaterialStatus.ACTIVE))
				.thenReturn(List.of()); // Bloco sem materiais
		when(chatRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		OpenAiChatService service = new OpenAiChatService(fakeProvider, blockRepo, materialRepo, chatRepo);
		OpenAiChatService.ChatResponse response = service.ask("Explique polimorfismo", blockId);

		assertNotNull(response.answer());
		assertTrue(capturedPrompt[0].contains("AVISO DE CONTEXTO: Este bloco de estudo ainda não possui fontes ativas cadastradas"));
		assertTrue(response.referencedMaterials().isEmpty());
	}

	@Test
	void tentativaDeAcessarMateriaisDeOutroBlocoEhRejeitadaComBadRequest() {
		AiTutorProvider mockProvider = Mockito.mock(AiTutorProvider.class);
		StudyBlockRepository blockRepo = Mockito.mock(StudyBlockRepository.class);
		StudyMaterialRepository materialRepo = Mockito.mock(StudyMaterialRepository.class);
		ChatConversationRepository chatRepo = Mockito.mock(ChatConversationRepository.class);

		UUID blockId = UUID.randomUUID();
		UUID allowedMaterialId = UUID.randomUUID();
		UUID foreignMaterialId = UUID.randomUUID();

		StudyBlock block = new StudyBlock("Bloco Atual", "Desc", "Meta");
		StudyMaterial allowedMaterial = new StudyMaterial(block, "Material Permitido", "Conteudo");
		// Injeta ID no mock do material
		StudyMaterial spyMaterial = Mockito.spy(allowedMaterial);
		when(spyMaterial.getId()).thenReturn(allowedMaterialId);

		when(blockRepo.existsById(blockId)).thenReturn(true);
		when(materialRepo.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyMaterialStatus.ACTIVE))
				.thenReturn(List.of(spyMaterial));

		OpenAiChatService service = new OpenAiChatService(mockProvider, blockRepo, materialRepo, chatRepo);

		// Tenta enviar ID de fonte que pertence a outro bloco
		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> service.ask("Pergunta", blockId, List.of(foreignMaterialId)));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
		assertTrue(ex.getReason().contains("não pertence a este bloco"));
		verify(mockProvider, never()).generateReply(any(AiTutorProvider.AiTutorRequest.class));
	}

	@Test
	void enviaHistoricoRecenteEmOrdemCronologicaNoTutorRequest() {
		final AiTutorProvider.AiTutorRequest[] captured = new AiTutorProvider.AiTutorRequest[1];
		AiTutorProvider fakeProvider = new AiTutorProvider() {
			@Override
			public String providerName() { return "FakeProvider"; }
			@Override
			public boolean isConfigured() { return true; }
			@Override
			public AiTutorReply generateReply(String systemInstruction, String userPrompt) {
				return generateReply(AiTutorRequest.ofText(systemInstruction, userPrompt));
			}
			@Override
			public AiTutorReply generateReply(AiTutorRequest request) {
				captured[0] = request;
				return new AiTutorReply("Resposta continuação.", "FakeProvider", "fake-model");
			}
		};

		StudyBlockRepository blockRepo = Mockito.mock(StudyBlockRepository.class);
		StudyMaterialRepository materialRepo = Mockito.mock(StudyMaterialRepository.class);
		ChatConversationRepository chatRepo = Mockito.mock(ChatConversationRepository.class);

		UUID blockId = UUID.randomUUID();
		when(blockRepo.existsById(blockId)).thenReturn(true);
		when(materialRepo.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyMaterialStatus.ACTIVE))
				.thenReturn(List.of());

		// Histórico mockado (ordem descendente de criação retornada pelo repositório)
		ChatConversationEntry msg1 = new ChatConversationEntry(
				blockId, "Pergunta 1 (mais antiga)", "Resposta 1", "FakeProvider", "fake-model", null);
		ChatConversationEntry msg2 = new ChatConversationEntry(
				blockId, "Pergunta 2 (mais recente)", "Resposta 2", "FakeProvider", "fake-model", null);
		when(chatRepo.findAllByStudyBlockIdOrderByCreatedAtDesc(blockId))
				.thenReturn(List.of(msg2, msg1));
		when(chatRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		OpenAiChatService service = new OpenAiChatService(fakeProvider, blockRepo, materialRepo, chatRepo);
		service.ask("Pergunta 3 (atual)", blockId);

		assertNotNull(captured[0]);
		List<AiTutorProvider.AiTutorMessage> history = captured[0].conversationHistory();
		assertEquals(4, history.size()); // 2 pares pergunta/resposta

		// Ordem no request deve ser cronológica (msg1 antes de msg2)
		assertEquals("user", history.get(0).role());
		assertEquals("Pergunta 1 (mais antiga)", history.get(0).content());
		assertEquals("assistant", history.get(1).role());
		assertEquals("Resposta 1", history.get(1).content());
		assertEquals("user", history.get(2).role());
		assertEquals("Pergunta 2 (mais recente)", history.get(2).content());
		assertEquals("assistant", history.get(3).role());
		assertEquals("Resposta 2", history.get(3).content());
	}
}
