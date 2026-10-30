package br.com.estudos.estudos_backend.exercise;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

import br.com.estudos.estudos_backend.chat.AiTutorProvider;
import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorReply;
import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorRequest;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.CheckResult;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.QualityIssue;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.RunStatus;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.TestResult;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PracticalExerciseAutoEvaluationIntegrationTest {

	private static final String JAVA_EXERCISE = """
			{"title":"Sobrescrita","prompt":"Crie Cachorro sobrescrevendo emitirSom().","steps":["Base","Sobrescrita"],
			 "language":"JAVA","starterCode":"","solutionCode":"SOLUCAO_REFERENCIA",
			 "testCode":"class AnimalTest {}","conceptChecks":["OVERRIDE_ANNOTATION","CHECAGEM_INVENTADA|x"],
			 "hintsJson":"[]","testCasesJson":"[]","sourceExcerpt":"Sobrescrita."}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AiTutorProvider aiTutorProvider;

	@MockitoBean
	private CodeRunner codeRunner;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private String userId;
	private String blockId;

	@BeforeEach
	void setUp() throws Exception {
		userId = "auto-eval-" + UUID.randomUUID();
		when(aiTutorProvider.isConfigured()).thenReturn(true);
		when(aiTutorProvider.generateReply(any(AiTutorRequest.class))).thenReturn(new AiTutorReply(JAVA_EXERCISE, "Fake", "fake"));
		when(codeRunner.isAvailable()).thenReturn(true);
		// A solução de referência passa em tudo: o exercício fica com correção automática.
		when(codeRunner.run(contains("SOLUCAO_REFERENCIA"), anyString(), anyList())).thenReturn(new CodeEvaluationReport(
				RunStatus.OK, null, List.of(), List.of(new TestResult("a()", true, true, null)),
				List.of(new CheckResult("OVERRIDE_ANNOTATION", "usa @Override", true, null)), List.of(), "{}"));
		String body = mockMvc.perform(post("/api/study-blocks")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Java OO\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		blockId = objectMapper.readTree(body).get("id").asText();
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/materials")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Sobrescrita\",\"content\":\"Sobrescrita redefine um método herdado.\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void submissionIsExecutedAndScoredWithoutSelfRating() throws Exception {
		when(codeRunner.run(contains("ALUNO"), anyString(), anyList())).thenReturn(new CodeEvaluationReport(
				RunStatus.OK, null, List.of(),
				List.of(new TestResult("a()", true, true, null), new TestResult("b()", false, false, "esperado Miau")),
				List.of(new CheckResult("OVERRIDE_ANNOTATION", "usa @Override", false, "Sem @Override: Cachorro.emitirSom()")),
				List.of(new QualityIssue("PUBLIC_FIELD", "MAJOR", "Atributo público", 1)),
				"{\"status\":\"OK\"}"));

		String generated = mockMvc.perform(post("/api/study-blocks/" + blockId + "/exercises/generate")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.language").value("JAVA"))
				.andExpect(jsonPath("$.autoEvaluable").value(true))
				.andReturn().getResponse().getContentAsString();
		String exerciseId = objectMapper.readTree(generated).get("id").asText();

		// correção: 2 de 3 pontos = 67%; conceito 0%; qualidade 85% → 67*0.5 + 0 + 85*0.25 = 54.75
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/exercises/" + exerciseId + "/attempts")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"answer\":\"// ALUNO\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.evaluationStatus").value("PARTIAL"))
				.andExpect(jsonPath("$.correctnessPercent").value(67))
				.andExpect(jsonPath("$.conceptPercent").value(0))
				.andExpect(jsonPath("$.qualityPercent").value(85))
				.andExpect(jsonPath("$.scorePercent").value(55))
				.andExpect(jsonPath("$.testsTotal").value(2))
				.andExpect(jsonPath("$.testsPassed").value(1))
				.andExpect(jsonPath("$.passed").value(false))
				.andExpect(jsonPath("$.report.status").value("OK"));
	}

	@Test
	void fallsBackToSelfRatingWhenRunnerIsUnavailable() throws Exception {
		String generated = mockMvc.perform(post("/api/study-blocks/" + blockId + "/exercises/generate")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String attemptsPath = "/api/study-blocks/" + blockId + "/exercises/" + objectMapper.readTree(generated).get("id").asText() + "/attempts";
		when(codeRunner.isAvailable()).thenReturn(false);

		mockMvc.perform(post(attemptsPath)
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"answer\":\"// ALUNO\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post(attemptsPath)
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"answer\":\"// ALUNO\",\"selfRating\":4}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.evaluationStatus").value("SELF_RATED"))
				.andExpect(jsonPath("$.scorePercent").value(80));
	}
}
