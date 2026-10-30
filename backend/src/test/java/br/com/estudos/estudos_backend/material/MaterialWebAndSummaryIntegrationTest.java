package br.com.estudos.estudos_backend.material;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.security.CurrentUserResolver;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaterialWebAndSummaryIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WebPageExtractor webPageExtractor;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private String userId;
	private String blockId;

	@BeforeEach
	void setUp() throws Exception {
		userId = "material-h-" + UUID.randomUUID();
		String body = mockMvc.perform(post("/api/study-blocks")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Bloco Etapa H\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		blockId = objectMapper.readTree(body).get("id").asText();
	}

	@Test
	void importsWebPageAndRejectsDuplicate() throws Exception {
		when(webPageExtractor.extract(anyString())).thenReturn(new WebPageExtractor.ExtractedWebPage(
				"https://example.com/polimorfismo", "Polimorfismo explicado",
				"Polimorfismo permite tratar objetos de subclasses como objetos da superclasse."));

		mockMvc.perform(post("/api/study-blocks/" + blockId + "/materials/web")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\"https://example.com/polimorfismo\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sourceType").value("WEB"))
				.andExpect(jsonPath("$.sourceUrl").value("https://example.com/polimorfismo"))
				.andExpect(jsonPath("$.title").value("Polimorfismo explicado"));

		mockMvc.perform(post("/api/study-blocks/" + blockId + "/materials/web")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\"https://example.com/polimorfismo\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void generatesExtractiveSummaryWithoutAiAndClearsItWhenContentChanges() throws Exception {
		String content = "Polimorfismo permite que objetos de subclasses sejam tratados como objetos da superclasse. "
				+ "Métodos sobrescritos são escolhidos em tempo de execução, conforme o tipo real do objeto. "
				+ "Interfaces habilitam polimorfismo quando várias classes implementam o mesmo contrato.";
		String created = mockMvc.perform(post("/api/study-blocks/" + blockId + "/materials")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.createObjectNode().put("title", "OO").put("content", content).toString()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String materialId = objectMapper.readTree(created).get("id").asText();

		mockMvc.perform(post("/api/study-blocks/" + blockId + "/materials/" + materialId + "/summary")
				.header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summarySource").value("Extrativo (sem IA)"))
				.andExpect(jsonPath("$.summary").isNotEmpty())
				.andExpect(jsonPath("$.summaryGeneratedAt").isNotEmpty());

		mockMvc.perform(put("/api/study-blocks/" + blockId + "/materials/" + materialId)
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.createObjectNode().put("title", "OO").put("content", content + " Novo parágrafo.").toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary").doesNotExist());
	}
}
