package br.com.estudos.estudos_backend.career;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

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

/** Etapa G: perfil profissional, acompanhamento da candidatura, análise da vaga e vaga → bloco de estudo. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareerProfileAndJobInsightIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CareerJobRepository jobRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void profileStartsEmptyAndIsPersisted() throws Exception {
		String user = "profile-" + UUID.randomUUID();
		mockMvc.perform(get("/api/career/profile").header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.headline").doesNotExist());

		mockMvc.perform(put("/api/career/profile")
				.header(CurrentUserResolver.USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"headline\":\"  Dev Java Júnior  \",\"experience\":\"Estágio em backend com Spring\","
						+ "\"projects\":\"\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.headline").value("Dev Java Júnior"))
				.andExpect(jsonPath("$.projects").doesNotExist());

		mockMvc.perform(get("/api/career/profile").header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(jsonPath("$.experience").value("Estágio em backend com Spring"));

		mockMvc.perform(put("/api/career/profile")
				.header(CurrentUserResolver.USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"headline\":\"" + "x".repeat(201) + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void statusKeepsNotesAndAcceptsNewStates() throws Exception {
		String user = "tracking-" + UUID.randomUUID();
		CareerJob job = saveJob();

		mockMvc.perform(patch("/api/career/jobs/" + job.getId() + "/status")
				.header(CurrentUserResolver.USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"INTERVIEWING\",\"notes\":\"Entrevista técnica na sexta\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INTERVIEWING"))
				.andExpect(jsonPath("$.applicationNotes").value("Entrevista técnica na sexta"))
				.andExpect(jsonPath("$.statusUpdatedAt").isNotEmpty());

		// Sem "notes" no corpo, as anotações atuais são mantidas.
		mockMvc.perform(patch("/api/career/jobs/" + job.getId() + "/status")
				.header(CurrentUserResolver.USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"OFFER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OFFER"))
				.andExpect(jsonPath("$.applicationNotes").value("Entrevista técnica na sexta"));

		mockMvc.perform(patch("/api/career/jobs/" + job.getId() + "/status")
				.header(CurrentUserResolver.USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"REJECTED\",\"notes\":\"\"}"))
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.applicationNotes").doesNotExist());

		mockMvc.perform(patch("/api/career/jobs/" + UUID.randomUUID() + "/status")
				.header(CurrentUserResolver.USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"APPLIED\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void analysisFallsBackToRulesWithoutAi() throws Exception {
		String user = "analysis-" + UUID.randomUUID();
		CareerJob job = saveJob();

		mockMvc.perform(post("/api/career/jobs/" + job.getId() + "/analysis").header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.source").value("Regras (sem IA)"))
				.andExpect(jsonPath("$.compatibilityPercent").isNumber())
				.andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("Próximos passos")))
				.andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("perfil profissional")));

		mockMvc.perform(post("/api/career/jobs/" + UUID.randomUUID() + "/analysis").header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(status().isNotFound());
	}

	@Test
	void createsStudyBlockFromJobOnlyOnce() throws Exception {
		String user = "jobblock-" + UUID.randomUUID();
		CareerJob job = saveJob();

		JsonNode created = json(mockMvc.perform(post("/api/career/jobs/" + job.getId() + "/study-block")
				.header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.created").value(true))
				.andExpect(jsonPath("$.title").value("Vaga: Dev Backend Júnior — Empresa G"))
				.andExpect(jsonPath("$.skills", hasItem("Kubernetes")))
				.andReturn().getResponse().getContentAsString());
		String blockId = created.get("studyBlockId").asText();

		// As competências da vaga vão no objetivo do bloco (não há mais tópicos).
		mockMvc.perform(get("/api/study-blocks/" + blockId).header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.learningObjective", containsString("Kubernetes")));

		mockMvc.perform(get("/api/career/jobs/" + job.getId()).header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(jsonPath("$.studyBlockId").value(blockId));

		mockMvc.perform(post("/api/career/jobs/" + job.getId() + "/study-block")
				.header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(false))
				.andExpect(jsonPath("$.studyBlockId").value(blockId));
	}

	private CareerJob saveJob() {
		return jobRepository.save(new CareerJob(null, "Fonte G", "ext-" + UUID.randomUUID(),
				"https://empresa.com/vagas/" + UUID.randomUUID(), "Dev Backend Júnior", "Empresa G", "Remoto",
				JobModality.REMOTE, ExperienceLevel.JUNIOR, BigDecimal.valueOf(4000), null, "BRL",
				"Buscamos pessoa desenvolvedora com Java, Spring Boot, Docker e Kubernetes.", "Java", "Java", OffsetDateTime.now()));
	}

	private JsonNode json(String body) throws Exception {
		return objectMapper.readTree(body);
	}
}
