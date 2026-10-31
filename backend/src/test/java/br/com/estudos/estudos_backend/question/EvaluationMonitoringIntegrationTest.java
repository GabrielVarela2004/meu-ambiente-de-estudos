package br.com.estudos.estudos_backend.question;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
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

import br.com.estudos.estudos_backend.security.CurrentUserResolver;

/**
 * Monitoramento da correção das questões abertas: estatísticas por usuário e verificação de consistência que
 * reavalia tentativas recentes sem gravar nada (corretor de IA simulado).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvaluationMonitoringIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	/** Mesmo nome dos outros testes que simulam o corretor: compartilham o contexto do Spring. */
	@MockitoBean
	private DiscursiveAnswerEvaluator discursiveAnswerEvaluator;

	private String userId;
	private String blockId;
	private String questionId;

	@BeforeEach
	void setUp() throws Exception {
		userId = "monitoring-" + UUID.randomUUID();
		String block = mockMvc.perform(post("/api/study-blocks").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Coleções\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		blockId = objectMapper().readTree(block).get("id").asText();
		String question = mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions")
				.header(CurrentUserResolver.USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"prompt":"Explique por que o HashSet não aceita duplicatas.","format":"DISCURSIVE",
						 "difficulty":"INTERMEDIATE","explanation":"Usa hashCode e equals.",
						 "expectedAnswer":"O HashSet usa hashCode e equals para não inserir objetos iguais."}
						"""))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		questionId = objectMapper().readTree(question).get("id").asText();
	}

	private static com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
		return new com.fasterxml.jackson.databind.ObjectMapper();
	}

	private static DiscursiveEvaluationRules.Result result(QuestionEvaluationStatus status, int score) {
		return new DiscursiveEvaluationRules.Result(status, score,
				Map.of("conceptualPrecision", 70, "practicalBehavior", 60, "technicalVocabulary", 70),
				List.of("Citou o hashCode"), List.of(), List.of(), List.of(), "Explique o papel do equals.", false);
	}

	@Test
	void statsAndConsistencyCheckReflectTheUsersGradedAttempts() throws Exception {
		when(discursiveAnswerEvaluator.evaluate(any(), eq(EvaluationRigor.RIGOROSO)))
				.thenReturn(result(QuestionEvaluationStatus.PARTIAL, 67));
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions/" + questionId + "/answers")
				.header(CurrentUserResolver.USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"discursiveAnswer\":\"Ele usa o hashCode para achar o balde e o equals para comparar.\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/settings/evaluation/monitoring").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.graded").value(1))
				.andExpect(jsonPath("$.pending").value(0))
				.andExpect(jsonPath("$.partial").value(1))
				.andExpect(jsonPath("$.averageScore").value(67.0))
				.andExpect(jsonPath("$.averageCriteria.practicalBehavior").value(60.0))
				.andExpect(jsonPath("$.blocks[0].blockTitle").value("Coleções"))
				.andExpect(jsonPath("$.blocks[0].attempts").value(1));

		// Reavaliação de consistência devolve concordância sem alterar a tentativa gravada.
		mockMvc.perform(post("/api/settings/evaluation/monitoring/consistency-check")
				.header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checked").value(1))
				.andExpect(jsonPath("$.agreements").value(1))
				.andExpect(jsonPath("$.agreementPercent").value(100.0))
				.andExpect(jsonPath("$.items[0].storedClassification").value("PARTIAL"))
				.andExpect(jsonPath("$.items[0].sameClassification").value(true));

		mockMvc.perform(get("/api/settings/evaluation/monitoring").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(jsonPath("$.graded").value(1))
				.andExpect(jsonPath("$.partial").value(1));
	}

	@Test
	void consistencyCheckDivergenceIsReportedWithoutChangingTheStoredAttempt() throws Exception {
		when(discursiveAnswerEvaluator.evaluate(any(), eq(EvaluationRigor.RIGOROSO)))
				.thenReturn(result(QuestionEvaluationStatus.PARTIAL, 67));
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions/" + questionId + "/answers")
				.header(CurrentUserResolver.USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"discursiveAnswer\":\"Ele não deixa repetir elementos.\"}"))
				.andExpect(status().isOk());

		// Agora o provedor atual classificaria como INCORRETA: divergência aparece, tentativa original continua.
		when(discursiveAnswerEvaluator.evaluate(any(), eq(EvaluationRigor.RIGOROSO)))
				.thenReturn(result(QuestionEvaluationStatus.INCORRECT, 35));
		mockMvc.perform(post("/api/settings/evaluation/monitoring/consistency-check")
				.header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.agreements").value(0))
				.andExpect(jsonPath("$.agreementPercent").value(0.0))
				.andExpect(jsonPath("$.items[0].newClassification").value("INCORRECT"))
				.andExpect(jsonPath("$.items[0].sameClassification").value(false));

		mockMvc.perform(get("/api/settings/evaluation/monitoring").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(jsonPath("$.partial").value(1));
	}

	@Test
	void consistencyCheckWithoutAiFailsWithClearMessage() throws Exception {
		when(discursiveAnswerEvaluator.evaluate(any(), eq(EvaluationRigor.RIGOROSO)))
				.thenReturn(result(QuestionEvaluationStatus.PARTIAL, 67));
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions/" + questionId + "/answers")
				.header(CurrentUserResolver.USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"discursiveAnswer\":\"Ele usa hashCode e equals.\"}"))
				.andExpect(status().isOk());

		when(discursiveAnswerEvaluator.evaluate(any(), any())).thenThrow(
				new DiscursiveAnswerEvaluator.EvaluationUnavailableException("Nenhum provedor de IA configurado."));
		mockMvc.perform(post("/api/settings/evaluation/monitoring/consistency-check")
				.header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(
						"Não foi possível verificar a consistência agora: Nenhum provedor de IA configurado."));
	}
}
