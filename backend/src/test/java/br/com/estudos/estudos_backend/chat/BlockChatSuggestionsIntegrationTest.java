package br.com.estudos.estudos_backend.chat;

import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
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
class BlockChatSuggestionsIntegrationTest {

	@Autowired
	private OpenAiChatService chatService;

	@Autowired
	private StudyBlockRepository blockRepository;

	@Autowired
	private StudyMaterialRepository materialRepository;

	private StudyBlock block;

	@BeforeEach
	void setUp() {
		block = blockRepository.save(new StudyBlock(
				"default-user",
				"Inteligência Artificial e LLMs",
				"Estudo de modelos fundacionais e RAG",
				"Compreender arquitetura de atenção"));
	}

	@Test
	void generatesSuggestedQuestionsFromActiveChatEnabledMaterials() {
		StudyMaterial mat1 = materialRepository.save(new StudyMaterial(
				block,
				"Mecanismo de Atenção",
				"O mecanismo de atenção permite ao modelo ponderar a relevância de diferentes tokens."));
		StudyMaterial mat2 = materialRepository.save(new StudyMaterial(
				block,
				"Embeddings Vetoriais",
				"Embeddings transformam palavras em vetores densos em espaço contínuo."));

		List<OpenAiChatService.SuggestedQuestion> suggestions = chatService.suggestQuestions(block.getId());

		assertNotNull(suggestions);
		assertFalse(suggestions.isEmpty(), "Deve sugerir perguntas baseadas nas fontes do bloco");
		assertTrue(suggestions.stream().anyMatch(s -> s.questionText().contains("Mecanismo de Atenção")));
		assertTrue(suggestions.stream().anyMatch(s -> s.questionText().contains("Embeddings Vetoriais")));
	}

	@Test
	void excludesDisabledChatMaterialsFromSuggestions() {
		StudyMaterial mat1 = new StudyMaterial(
				block,
				"Material Habilitado",
				"Conteúdo incluído normalmente para consulta.");
		mat1.setEnabledForChat(true);
		materialRepository.save(mat1);

		StudyMaterial mat2 = new StudyMaterial(
				block,
				"Material Desabilitado",
				"Este material não deve gerar perguntas rápidas.");
		mat2.setEnabledForChat(false);
		materialRepository.save(mat2);

		List<OpenAiChatService.SuggestedQuestion> suggestions = chatService.suggestQuestions(block.getId());

		assertTrue(suggestions.stream().anyMatch(s -> s.questionText().contains("Material Habilitado")));
		assertFalse(suggestions.stream().anyMatch(s -> s.questionText().contains("Material Desabilitado")),
				"Fontes desabilitadas para o chat não devem constar nas sugestões");
	}
}
