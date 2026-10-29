package br.com.estudos.estudos_backend.chat;

import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.learning.ActivityOriginType;
import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UniversalMultiBlockChatIntegrationTest {

	@Autowired
	private OpenAiChatService chatService;

	@Autowired
	private StudyBlockRepository blockRepository;

	@Autowired
	private StudyMaterialRepository materialRepository;

	@Autowired
	private ChatConversationRepository conversationRepository;

	@MockitoBean
	private AiTutorProvider aiTutorProvider;

	private StudyBlock blockJava;
	private StudyBlock blockPython;

	@BeforeEach
	void setUp() {
		when(aiTutorProvider.isConfigured()).thenReturn(true);
		when(aiTutorProvider.providerName()).thenReturn("MockTutor");
		when(aiTutorProvider.configuredModel()).thenReturn("gpt-4o-mini");
		when(aiTutorProvider.generateReply(any(AiTutorProvider.AiTutorRequest.class))).thenAnswer(invocation -> {
			AiTutorProvider.AiTutorRequest req = invocation.getArgument(0);
			return new AiTutorProvider.AiTutorReply(
					"Síntese comparativa entre Java e Python fundamentada nas fontes dos blocos selecionados.",
					"MockTutor",
					"mock-model");
		});

		blockJava = blockRepository.save(new StudyBlock(
				"default-user",
				"Java Backend",
				"Estudos de Spring Boot e Hibernate",
				"Construir APIs REST escaláveis"));

		blockPython = blockRepository.save(new StudyBlock(
				"default-user",
				"Python Data Science",
				"Estudos de NumPy e Pandas",
				"Manipulação e análise de dados"));
	}

	@Test
	void performsMultiBlockCrossChatReferencingBothBlocksSources() {
		StudyMaterial matJava = materialRepository.save(new StudyMaterial(
				blockJava,
				"Coleções em Java",
				"Java utiliza List, Set e Map no pacote java.util para gerenciamento de coleções com tipagem estrita."));

		StudyMaterial matPython = materialRepository.save(new StudyMaterial(
				blockPython,
				"Estruturas em Python",
				"Python utiliza listas nativas, conjuntos e dicionários com tipagem dinâmica flexível."));

		List<UUID> selectedBlocks = List.of(blockJava.getId(), blockPython.getId());

		OpenAiChatService.ChatResponse response = chatService.ask(
				"Compare as coleções em Java com as estruturas do Python",
				null,
				selectedBlocks,
				null);

		assertNotNull(response);
		assertNotNull(response.answer());
		assertFalse(response.referencedMaterials().isEmpty(), "Deve referenciar materiais dos blocos selecionados");
		assertTrue(response.referencedMaterials().contains("Coleções em Java"));
		assertTrue(response.referencedMaterials().contains("Estruturas em Python"));

		// Verifica se o histórico universal foi salvo
		List<ChatConversationEntry> universalHistory = chatService.listUniversalHistory();
		assertFalse(universalHistory.isEmpty());
		assertEquals(ActivityOriginType.UNIVERSAL, universalHistory.get(0).getScopeType());
	}

	@Test
	void rejectsMultiBlockChatWithInaccessibleOrUnknownBlock() {
		UUID unknownBlockId = UUID.randomUUID();
		assertThrows(ResponseStatusException.class, () ->
				chatService.ask("Pergunta", null, List.of(blockJava.getId(), unknownBlockId), null));
	}
}
