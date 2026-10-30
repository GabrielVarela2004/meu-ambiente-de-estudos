package br.com.estudos.estudos_backend.exercise;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.security.CurrentUserResolver;

/** Sem tipo escolhido, exercícios gerados em sequência variam de formato (o menos praticado no bloco). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PracticalExerciseVarietyIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void consecutiveGenerationsWithoutTypeUseDifferentFormats() throws Exception {
		String userId = "variety-" + UUID.randomUUID();
		String block = mockMvc.perform(post("/api/study-blocks").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Bloco variedade\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String blockId = objectMapper.readTree(block).get("id").asText();
		String content = "Uma lista ligada guarda nós encadeados por referências. Inserir no início custa tempo constante. "
				+ "Buscar um elemento exige percorrer os nós em sequência. Uma pilha remove sempre o último elemento inserido.";
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/materials").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.createObjectNode().put("title", "Estruturas").put("content", content).toString()))
				.andExpect(status().isCreated());

		String first = generate(userId, blockId);
		String second = generate(userId, blockId);

		assertNotEquals(first, second);
	}

	private String generate(String userId, String blockId) throws Exception {
		String body = mockMvc.perform(post("/api/study-blocks/" + blockId + "/exercises/generate")
				.header(CurrentUserResolver.USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("exerciseType").asText();
	}
}
