package br.com.estudos.estudos_backend.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.security.CurrentUserResolver;

/**
 * A IA só usa fontes ativas no chat: com a única fonte desligada, geração de questões, de exercícios e
 * extração de tópicos recusam com mensagem clara (sem chave de IA, os caminhos sem IA também respeitam o filtro).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatScopedSourcesIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private String userId;
	private String blockId;

	@BeforeEach
	void setUp() throws Exception {
		userId = "chat-scope-" + UUID.randomUUID();
		String block = mockMvc.perform(post("/api/study-blocks").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Bloco escopo do chat\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		blockId = objectMapper.readTree(block).get("id").asText();

		String content = "Polimorfismo permite que objetos de subclasses sejam tratados como objetos da superclasse. "
				+ "Herança define relações é-um entre classes. Interfaces declaram contratos que as classes implementam. "
				+ "Encapsulamento protege o estado interno do objeto expondo apenas métodos públicos bem definidos.";
		String material = mockMvc.perform(post("/api/study-blocks/" + blockId + "/materials")
				.header(CurrentUserResolver.USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.createObjectNode().put("title", "POO").put("content", content).toString()))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String materialId = objectMapper.readTree(material).get("id").asText();

		mockMvc.perform(patch("/api/study-blocks/" + blockId + "/materials/" + materialId + "/toggle-chat")
				.header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabledForChat").value(false));
	}

	@Test
	void questionGenerationIgnoresSourcesDisabledInChat() throws Exception {
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions/generate-simulado")
				.header(CurrentUserResolver.USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"quantity\":1}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.message")
						.value("Adicione uma fonte concluída ao bloco e deixe-a ativa no chat antes de gerar questões."));
	}

	@Test
	void exerciseGenerationIgnoresSourcesDisabledInChat() throws Exception {
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/exercises/generate")
				.header(CurrentUserResolver.USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.message").value(
						"Adicione uma fonte concluída ao bloco e deixe-a ativa no chat antes de gerar exercícios práticos."));
	}
}
