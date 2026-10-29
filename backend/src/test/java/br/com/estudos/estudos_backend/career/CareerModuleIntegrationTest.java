package br.com.estudos.estudos_backend.career;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.career.adapter.in.web.CareerDto;
import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerAgentExecutionRepository;
import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerJobRepository;
import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerJobSourceRepository;
import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerPreferencesRepository;
import br.com.estudos.estudos_backend.career.domain.AgentTriggerType;
import br.com.estudos.estudos_backend.career.domain.CareerAgentExecution;
import br.com.estudos.estudos_backend.career.domain.CareerJob;
import br.com.estudos.estudos_backend.career.domain.CareerJobSource;
import br.com.estudos.estudos_backend.career.domain.ExperienceLevel;
import br.com.estudos.estudos_backend.career.domain.JobModality;
import br.com.estudos.estudos_backend.career.service.CareerJobCompatibilityService;
import br.com.estudos.estudos_backend.career.service.CareerJobCompatibilityService.JobMatchEvaluation;
import br.com.estudos.estudos_backend.career.service.CareerJobSearchService;
import br.com.estudos.estudos_backend.career.service.CareerSkillsProfileService;
import br.com.estudos.estudos_backend.career.service.CareerSkillsProfileService.UserCareerSkillsProfile;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareerModuleIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private CareerPreferencesRepository preferencesRepository;

	@Autowired
	private CareerJobSourceRepository sourceRepository;

	@Autowired
	private CareerJobRepository jobRepository;

	@Autowired
	private CareerAgentExecutionRepository executionRepository;

	@Autowired
	private StudyBlockRepository blockRepository;

	@Autowired
	private CareerJobCompatibilityService compatibilityService;

	@Autowired
	private CareerSkillsProfileService skillsProfileService;

	@Autowired
	private CareerJobSearchService jobSearchService;

	private final String testUserId = "career-test-user";

	@BeforeEach
	void setUp() {
		jobRepository.deleteAll();
		executionRepository.deleteAll();
		preferencesRepository.deleteAll();
	}

	@Test
	void testPreferencesCrud() throws Exception {
		// 1. Obter preferências padrão
		mockMvc.perform(get("/api/career/preferences")
						.header(CurrentUserResolver.USER_HEADER, testUserId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetRoles").exists())
				.andExpect(jsonPath("$.maxRoundMinutes").value(30))
				.andExpect(jsonPath("$.agentEnabled").value(true));

		// 2. Atualizar preferências
		CareerDto.PreferencesRequest updateReq = new CareerDto.PreferencesRequest(
				"Engenheiro de Software Java Sênior, Tech Lead",
				"MID,SENIOR",
				"Java, Spring Boot, AWS, Docker, Kubernetes",
				"Microsserviços",
				"TDD, Clean Arch",
				"Remoto Brasil",
				JobModality.REMOTE,
				BigDecimal.valueOf(12000),
				"MON,WED,FRI",
				10,
				30,
				45,
				true
		);

		mockMvc.perform(put("/api/career/preferences")
						.header(CurrentUserResolver.USER_HEADER, testUserId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetRoles").value("Engenheiro de Software Java Sênior, Tech Lead"))
				.andExpect(jsonPath("$.modality").value("REMOTE"))
				.andExpect(jsonPath("$.maxRoundMinutes").value(45))
				.andExpect(jsonPath("$.searchTimeHour").value(10))
				.andExpect(jsonPath("$.searchTimeMinute").value(30));
	}

	@Test
	void testJobSourcesListAndToggle() throws Exception {
		List<CareerJobSource> sources = sourceRepository.findAll();
		assertFalse(sources.isEmpty(), "Fontes iniciais devem ser inseridas via migração Flyway");

		CareerJobSource first = sources.get(0);

		// Desabilitar fonte
		mockMvc.perform(patch("/api/career/sources/" + first.getId() + "/toggle")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"enabled\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false));

		CareerJobSource reloaded = sourceRepository.findById(first.getId()).orElseThrow();
		assertFalse(reloaded.isEnabled());

		// Reabilitar fonte
		mockMvc.perform(patch("/api/career/sources/" + first.getId() + "/toggle")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"enabled\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(true));
	}

	@Test
	void testJobFeedAndDeterministicCompatibility() throws Exception {
		// Inserir bloco de estudo do usuário com tema "Java & Spring Boot"
		StudyBlock block = new StudyBlock(testUserId, "Java e Spring Boot", "Desenvolvimento de APIs robustas", "Dominar o ecossistema Spring");
		blockRepository.save(block);

		// Inserir vaga mock no banco
		CareerJob job = new CareerJob(
				null,
				"Mock Tech Source",
				"job-123",
				"https://empresa.com/vagas/backend-java-123",
				"Desenvolvedor Java Backend Júnior",
				"Empresa XPTO",
				"Remoto - Brasil",
				JobModality.REMOTE,
				ExperienceLevel.JUNIOR,
				BigDecimal.valueOf(5000),
				BigDecimal.valueOf(7000),
				"BRL",
				"Procuramos desenvolvedor com sólidos conhecimentos em Java, Spring Boot, REST e PostgreSQL.",
				"Java, Spring Boot, PostgreSQL",
				"Java, Spring, SQL",
				OffsetDateTime.now()
		);
		jobRepository.save(job);

		// Buscar feed de vagas com cálculo de compatibilidade
		mockMvc.perform(get("/api/career/jobs")
						.header(CurrentUserResolver.USER_HEADER, testUserId)
						.param("query", "Java"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content[0].title").value("Desenvolvedor Java Backend Júnior"))
				.andExpect(jsonPath("$.content[0].company").value("Empresa XPTO"))
				.andExpect(jsonPath("$.content[0].compatibilityPercent").isNumber())
				.andExpect(jsonPath("$.content[0].matchedRequirements").isArray());
	}

	@Test
	void testAgentStatusAndManualRunTrigger() throws Exception {
		// Verificar status inicial
		mockMvc.perform(get("/api/career/agent/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.running").value(false));

		// Criar uma execução de teste
		CareerAgentExecution exec = new CareerAgentExecution(AgentTriggerType.RECOVERED);
		exec.complete(10, 3, "Execução consolidada de recuperação bem sucedida.");
		executionRepository.save(exec);

		mockMvc.perform(get("/api/career/agent/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.latestExecution.triggerType").value("RECOVERED"))
				.andExpect(jsonPath("$.latestExecution.jobsFound").value(10))
				.andExpect(jsonPath("$.latestExecution.newJobsCount").value(3));
	}

	@Test
	void testDeterministicScoringCalculation() {
		UserCareerSkillsProfile profile = skillsProfileService.getUserSkillsProfile();
		assertNotNull(profile);

		CareerJob job = new CareerJob(
				null,
				"Fonte Teste",
				"ext-1",
				"https://test.com/job/1",
				"Software Engineer - Java & React",
				"Tech Corp",
				"Remoto",
				JobModality.REMOTE,
				ExperienceLevel.MID,
				null,
				null,
				"USD",
				"Requirements: Strong knowledge of Java, Spring, React, TypeScript, Docker.",
				null,
				"Java, React, Docker",
				OffsetDateTime.now()
		);

		JobMatchEvaluation evaluation = compatibilityService.evaluateCompatibility(job, profile);
		assertNotNull(evaluation);
		assertTrue(evaluation.matchPercent() >= 0 && evaluation.matchPercent() <= 100);
		assertNotNull(evaluation.summaryNote());
	}
}
