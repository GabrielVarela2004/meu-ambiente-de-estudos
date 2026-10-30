package br.com.estudos.estudos_backend.knowledge;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.security.CurrentUserResolver;

/**
 * Validação manual dos tópicos pela API, sem IA (extração determinística): sugestões pendentes fora da lista oficial,
 * ruído de transcrição filtrado, aprovar/excluir/comentar, oficializar e excluído que não volta na próxima extração.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TopicReviewFlowIntegrationTest {

	private static final String TRANSCRIPT = "Olá pessoal, bem-vindos ao nosso canal! Não esqueçam de deixar o like. "
			+ "Nesta aula vamos falar de Java. Tipos primitivos são valores simples como int, double e boolean. "
			+ "Herança permite que uma classe reutilize atributos e métodos de outra classe.";

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private String userId;
	private String blockId;

	@BeforeEach
	void setUp() throws Exception {
		userId = "topic-review-" + UUID.randomUUID();
		blockId = json(perform(post("/api/study-blocks").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Java\"}"))).get("id").asText();
		// Criar a fonte já dispara a extração automática: os tópicos entram como sugestões.
		perform(post("/api/study-blocks/" + blockId + "/materials").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.createObjectNode().put("title", "Java basico").put("content", TRANSCRIPT).toString()));
	}

	private String topics() {
		return "/api/study-blocks/" + blockId + "/topics";
	}

	private String perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(CurrentUserResolver.USER_HEADER, userId))
				.andReturn().getResponse().getContentAsString();
	}

	private JsonNode json(String body) throws Exception {
		return objectMapper.readTree(body);
	}

	private String idOf(String title) throws Exception {
		for (JsonNode item : json(perform(get(topics() + "/review"))).get("items")) {
			if (item.get("title").asText().equals(title)) {
				return item.get("id").asText();
			}
		}
		throw new AssertionError("Sugestão não encontrada: " + title);
	}

	private void review(String id, String body, int expectedStatus) throws Exception {
		mockMvc.perform(post(topics() + "/review/" + id).header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().is(expectedStatus));
	}

	@Test
	void suggestionsWaitForReviewAndOnlyApprovedOnesBecomeOfficial() throws Exception {
		// Nada oficial antes da validação.
		mockMvc.perform(get(topics()).header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(jsonPath("$", empty()));

		mockMvc.perform(get(topics() + "/review").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].title", hasItem("Java basico")))
				.andExpect(jsonPath("$.items[*].title", hasItem("Tipos primitivos")))
				.andExpect(jsonPath("$.items[*].title", hasItem("Herança")))
				// Saudação, pedido de like e aviso da aula não viram tópico.
				.andExpect(jsonPath("$.items[*].title", everyItem(not(containsStringIgnoringCase("pessoal")))))
				.andExpect(jsonPath("$.items[*].title", everyItem(not(containsStringIgnoringCase("like")))))
				.andExpect(jsonPath("$.items[*].title", everyItem(not(containsStringIgnoringCase("nesta aula")))))
				.andExpect(jsonPath("$.items[*].reviewState", everyItem(org.hamcrest.Matchers.is("PENDING"))))
				.andExpect(jsonPath("$.items[*].relevanceScore", everyItem(org.hamcrest.Matchers.notNullValue())))
				.andExpect(jsonPath("$.summary.round").value(1));

		String main = idOf("Java basico");
		String primitives = idOf("Tipos primitivos");
		String inheritance = idOf("Herança");

		// Comentário exige texto.
		review(primitives, "{\"action\":\"FEEDBACK\",\"feedback\":\"\"}", 400);
		review(main, "{\"action\":\"APPROVED\",\"title\":\"Fundamentos de Java\"}", 200);
		review(primitives, "{\"action\":\"APPROVED\"}", 200);
		review(inheritance, "{\"action\":\"REJECTED\"}", 200);

		// Revisão com IA sem provedor configurado: mensagem clara.
		review(primitives, "{\"action\":\"FEEDBACK\",\"feedback\":\"Inclua char e os tamanhos em bytes\"}", 200);
		mockMvc.perform(post(topics() + "/review/refine").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isServiceUnavailable());
		review(primitives, "{\"action\":\"APPROVED\"}", 200);

		mockMvc.perform(post(topics() + "/review/publish").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.published").value(2))
				.andExpect(jsonPath("$.archived").value(1));

		mockMvc.perform(get(topics()).header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(jsonPath("$[*].title", hasItem("Fundamentos de Java")))
				.andExpect(jsonPath("$[*].title", hasItem("Tipos primitivos")))
				.andExpect(jsonPath("$[*].title", not(hasItem("Herança"))))
				.andExpect(jsonPath("$[*].userValidated", everyItem(org.hamcrest.Matchers.is(true))));

		// Nova extração: o excluído não volta e os oficiais continuam oficiais.
		mockMvc.perform(post(topics() + "/extract").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk());
		mockMvc.perform(get(topics() + "/review").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(jsonPath("$.items[*].title", not(hasItem("Herança"))))
				.andExpect(jsonPath("$.summary.firstRoundApprovalPercent").isNumber());
		mockMvc.perform(get(topics()).header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(jsonPath("$[*].title", hasItem("Tipos primitivos")));
	}

	@Test
	void bulkApprovalUsesTheRelevanceThreshold() throws Exception {
		mockMvc.perform(post(topics() + "/review/approve-relevant").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"minRelevance\":65}"))
				.andExpect(status().isOk())
				// Só o assunto principal (70) passa; os conceitos tirados de frases (50) esperam revisão manual.
				.andExpect(jsonPath("$.count").value(1));

		mockMvc.perform(post(topics() + "/review/publish").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.published").value(1));
	}

	@Test
	void publishingWithoutDecisionsIsRefusedAndOtherUsersCannotReview() throws Exception {
		mockMvc.perform(post(topics() + "/review/publish").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().is4xxClientError());

		String main = idOf("Java basico");
		mockMvc.perform(post(topics() + "/review/" + main).header(CurrentUserResolver.USER_HEADER, "outro-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"APPROVED\"}"))
				.andExpect(status().is4xxClientError());
	}
}
