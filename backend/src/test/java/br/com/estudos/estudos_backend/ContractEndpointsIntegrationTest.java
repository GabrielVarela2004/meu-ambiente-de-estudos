package br.com.estudos.estudos_backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerJobRepository;
import br.com.estudos.estudos_backend.career.domain.CareerJob;
import br.com.estudos.estudos_backend.career.domain.ExperienceLevel;
import br.com.estudos.estudos_backend.career.domain.JobModality;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;

/**
 * Contratos usados pelo frontend que antes não existiam no backend:
 * restaurar bloco, gerar simulado, status de vaga e desafio de competência.
 * Cada execução usa um usuário próprio, sem interferir nos demais testes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractEndpointsIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StudyBlockRepository blockRepository;

	@Autowired
	private CareerJobRepository jobRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private String userId;

	@BeforeEach
	void setUp() {
		userId = "contract-test-" + UUID.randomUUID();
	}

	@Test
	void unarchiveRestoresArchivedBlock() throws Exception {
		String blockId = createBlock("Bloco para restaurar");

		mockMvc.perform(delete("/api/study-blocks/" + blockId).header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/study-blocks/" + blockId + "/unarchive").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		mockMvc.perform(get("/api/study-blocks").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void unarchiveOfActiveBlockIsConflict() throws Exception {
		String blockId = createBlock("Bloco já ativo");
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/unarchive").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isConflict());
	}

	@Test
	void unarchiveOfAnotherUsersBlockIsNotFound() throws Exception {
		String blockId = createBlock("Bloco de outro usuário");
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/unarchive")
				.header(CurrentUserResolver.USER_HEADER, "outro-" + UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void generateSimuladoCreatesRequestedQuantity() throws Exception {
		String blockId = createBlock("Bloco simulado");
		createMaterial(blockId);

		mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions/generate-simulado")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"quantity\":2,\"difficulty\":\"EASY\",\"format\":\"MULTIPLE_CHOICE\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void generateSimuladoRejectsInvalidQuantity() throws Exception {
		String blockId = createBlock("Bloco simulado inválido");
		createMaterial(blockId);

		mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions/generate-simulado")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"quantity\":11}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Há campos com valores inválidos."))
				.andExpect(jsonPath("$.fields.quantity").value(containsString("menor")));
	}

	@Test
	void jobStatusIsPersistedAndReturned() throws Exception {
		CareerJob job = jobRepository.save(new CareerJob(null, "Fonte Contrato", "ext-" + UUID.randomUUID(),
				"https://empresa.com/vagas/" + UUID.randomUUID(), "Dev Java Júnior", "Empresa", "Remoto",
				JobModality.REMOTE, ExperienceLevel.JUNIOR, BigDecimal.valueOf(4000), null, "BRL",
				"Java e Spring Boot", "Java", "Java", OffsetDateTime.now()));

		mockMvc.perform(patch("/api/career/jobs/" + job.getId() + "/status")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"APPLIED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPLIED"));

		mockMvc.perform(get("/api/career/jobs/" + job.getId()).header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(jsonPath("$.status").value("APPLIED"));

		mockMvc.perform(patch("/api/career/jobs/" + job.getId() + "/status")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"INVALIDO\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void skillAssessmentGeneratesPracticalExerciseForBlock() throws Exception {
		String blockId = createBlock("Polimorfismo");
		createMaterial(blockId);

		// A competência é o próprio bloco (título, sem diferenciar maiúsculas).
		mockMvc.perform(post("/api/career/skills/assess")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillName\":\"polimorfismo\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.studyBlockId").value(blockId))
				.andExpect(jsonPath("$.exerciseId").isNotEmpty());

		mockMvc.perform(post("/api/career/skills/assess")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillName\":\"Inexistente\"}"))
				.andExpect(status().isNotFound());
	}

	private String createBlock(String title) throws Exception {
		String body = mockMvc.perform(post("/api/study-blocks")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"" + title + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("id").asText();
	}

	private void createMaterial(String blockId) throws Exception {
		String content = "Polimorfismo permite que objetos de subclasses sejam tratados como objetos da superclasse. "
				+ "Herança define relações é-um entre classes. Interfaces declaram contratos que as classes implementam. "
				+ "Encapsulamento protege o estado interno do objeto expondo apenas métodos públicos bem definidos.";
		JsonNode payload = objectMapper.createObjectNode().put("title", "Orientação a objetos").put("content", content);
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/materials")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload.toString()))
				.andExpect(status().isCreated());
	}
}
