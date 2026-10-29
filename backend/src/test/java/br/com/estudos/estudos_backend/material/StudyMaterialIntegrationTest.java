package br.com.estudos.estudos_backend.material;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.chat.OpenAiChatService;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudyMaterialIntegrationTest {

	@Autowired
	private StudyMaterialService materialService;

	@Autowired
	private StudyBlockService blockService;

	@Autowired
	private StudyBlockRepository blockRepository;

	@Autowired
	private StudyMaterialRepository materialRepository;

	private StudyBlock block;

	@BeforeEach
	void setUp() {
		block = blockRepository.save(new StudyBlock(
				"default-user",
				"Bloco de Fontes Avançadas",
				"Testes de materiais no estilo NotebookLM",
				"Dominar gestão de fontes"));
	}

	@Test
	void createsMaterialWithChatEnabledByDefaultAndCalculatesWordAndCharCounts() {
		StudyMaterial material = materialService.create(
				block.getId(),
				"Guia de Arquitetura",
				"Este material possui exatamente sete palavras aqui.");

		assertNotNull(material.getId());
		assertTrue(material.isEnabledForChat(), "Material deve iniciar habilitado para o chat");
		assertEquals(StudyMaterialProcessingStatus.COMPLETED, material.getProcessingStatus());

		StudyMaterialController.StudyMaterialResponse response =
				StudyMaterialController.StudyMaterialResponse.from(material);

		assertEquals(7, response.wordCount());
		assertTrue(response.charCount() > 0);
		assertTrue(response.enabledForChat());
	}

	@Test
	void togglesChatInclusionWithoutDeletingOrAffectingContent() {
		StudyMaterial material = materialService.create(
				block.getId(),
				"Fonte Opcional",
				"Conteúdo técnico detalhado para testes de alternância.");

		assertTrue(material.isEnabledForChat());

		StudyMaterial toggledOff = materialService.toggleChatInclusion(block.getId(), material.getId());
		assertFalse(toggledOff.isEnabledForChat(), "Deve desabilitar a inclusão da fonte no chat");

		StudyMaterial toggledOn = materialService.toggleChatInclusion(block.getId(), material.getId());
		assertTrue(toggledOn.isEnabledForChat(), "Deve reabilitar a inclusão da fonte no chat");
	}

	@Test
	void archivingMaterialPreservesEntityInHistoryQuery() {
		StudyMaterial material = materialService.create(
				block.getId(),
				"Fonte a ser Arquivada",
				"Este texto deve continuar existindo para integridade referencial.");

		materialService.archive(block.getId(), material.getId());

		List<StudyMaterial> activeOnly = materialService.list(block.getId(), false);
		assertFalse(activeOnly.stream().anyMatch(m -> m.getId().equals(material.getId())));

		List<StudyMaterial> withArchived = materialService.list(block.getId(), true);
		assertTrue(withArchived.stream().anyMatch(m -> m.getId().equals(material.getId())),
				"Fonte arquivada deve ser mantida para histórico e integridade");
	}
}
