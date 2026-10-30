package br.com.estudos.estudos_backend.question;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.security.CurrentUserResolver;

/**
 * Fluxo da questão aberta pela API com o corretor de IA simulado: resposta-modelo escondida antes da resposta,
 * resposta pendente sem IA, reavaliação, feedback completo, nova tentativa e nível de rigor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiscursiveAnswerFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StudyQuestionRepository questionRepository;

	/** Mesmo nome dos outros testes que simulam o corretor: compartilham o contexto do Spring. */
	@MockitoBean
	private DiscursiveAnswerEvaluator discursiveAnswerEvaluator;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private String userId;
	private String blockId;
	private String questionId;

	@BeforeEach
	void setUp() throws Exception {
		userId = "open-question-" + UUID.randomUUID();
		String block = mockMvc.perform(post("/api/study-blocks").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Coleções\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		blockId = objectMapper.readTree(block).get("id").asText();
		String question = mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions")
				.header(CurrentUserResolver.USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"prompt":"Explique por que o HashSet não aceita duplicatas.","format":"DISCURSIVE",
						 "difficulty":"INTERMEDIATE","explanation":"Usa hashCode e equals.",
						 "expectedAnswer":"O HashSet usa hashCode e equals para não inserir objetos iguais."}
						"""))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		questionId = objectMapper.readTree(question).get("id").asText();
	}

	private String questions() {
		return "/api/study-blocks/" + blockId + "/questions";
	}

	private static DiscursiveEvaluationRules.Result result(QuestionEvaluationStatus status, int score) {
		return new DiscursiveEvaluationRules.Result(status, score,
				Map.of("conceptualAccuracy", 80, "essentialCoverage", 60, "depth", 60, "terminology", 70, "noErrors", 100),
				List.of("Citou o hashCode"), List.of("equals compara os objetos"), List.of("balde"), List.of(),
				"Explique o papel do equals.", false);
	}

	@Test
	void modelAnswerIsHiddenUntilAnsweredAndFeedbackIsCompleteAfterwards() throws Exception {
		mockMvc.perform(get(questions()).header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].expectedAnswer").doesNotExist())
				.andExpect(jsonPath("$[0].explanation").doesNotExist());

		when(discursiveAnswerEvaluator.evaluate(any(), eq(EvaluationRigor.RIGOROSO)))
				.thenReturn(result(QuestionEvaluationStatus.PARTIAL, 68));

		mockMvc.perform(post(questions() + "/" + questionId + "/answers").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"discursiveAnswer\":\"Ele usa o hashCode.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(false))
				.andExpect(jsonPath("$.classification").value("PARTIAL"))
				.andExpect(jsonPath("$.scorePercent").value(68))
				.andExpect(jsonPath("$.feedback.modelAnswer").value("O HashSet usa hashCode e equals para não inserir objetos iguais."))
				.andExpect(jsonPath("$.feedback.missingEssentialConcepts[0]").value("equals compara os objetos"))
				.andExpect(jsonPath("$.feedback.missingTerms[0]").value("balde"))
				.andExpect(jsonPath("$.feedback.correctPoints[0]").value("Citou o hashCode"))
				.andExpect(jsonPath("$.feedback.criteriaScores.essentialCoverage").value(60))
				.andExpect(jsonPath("$.feedback.improvementTip").value("Explique o papel do equals."))
				.andExpect(jsonPath("$.feedback.rigorLevel").value("RIGOROSO"));

		// PARCIAL mantém um intervalo curto de revisão, sem contar como acerto.
		StudyQuestion saved = questionRepository.findById(UUID.fromString(questionId)).orElseThrow();
		assertEquals(2, saved.getReviewIntervalDays());
		assertEquals(0, saved.getCorrectStreak());

		// Responder de novo cria outra tentativa.
		when(discursiveAnswerEvaluator.evaluate(any(), any())).thenReturn(result(QuestionEvaluationStatus.CORRECT, 92));
		mockMvc.perform(post(questions() + "/" + questionId + "/answers").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"discursiveAnswer\":\"Usa hashCode para achar o balde e equals para comparar.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(true))
				.andExpect(jsonPath("$.classification").value("CORRECT"));
	}

	@Test
	void pendingAnswerIsReevaluatedWhenAiComesBack() throws Exception {
		when(discursiveAnswerEvaluator.evaluate(any(), any()))
				.thenThrow(new DiscursiveAnswerEvaluator.EvaluationUnavailableException("Sem provedor de IA."));

		String body = mockMvc.perform(post(questions() + "/" + questionId + "/answers")
				.header(CurrentUserResolver.USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"discursiveAnswer\":\"Usa hashCode e equals.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.classification").value("PENDING_EVALUATION"))
				.andExpect(jsonPath("$.feedback.pending").value(true))
				.andExpect(jsonPath("$.feedback.modelAnswer").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String attemptId = objectMapper.readTree(body).path("feedback").path("attemptId").asText();

		mockMvc.perform(get(questions() + "/" + questionId + "/attempts/pending").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));

		// doReturn: o mock está configurado para lançar exceção, então when(...) dispararia a exceção.
		doReturn(result(QuestionEvaluationStatus.CORRECT, 90)).when(discursiveAnswerEvaluator).evaluate(any(), any());
		mockMvc.perform(post(questions() + "/" + questionId + "/attempts/" + attemptId + "/evaluate")
				.header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.classification").value("CORRECT"))
				.andExpect(jsonPath("$.feedback.modelAnswer").isNotEmpty());

		mockMvc.perform(get(questions() + "/" + questionId + "/attempts/pending").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(jsonPath("$", hasSize(0)));
		// Já corrigida: o snapshot não muda mais.
		mockMvc.perform(post(questions() + "/" + questionId + "/attempts/" + attemptId + "/evaluate")
				.header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isConflict());
	}

	@Test
	void rigorLevelDefaultsToRigorousAndCanBeChanged() throws Exception {
		mockMvc.perform(get("/api/settings/evaluation").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rigorLevel").value("RIGOROSO"));
		mockMvc.perform(put("/api/settings/evaluation").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"rigorLevel\":\"MODERADO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rigorLevel").value("MODERADO"));

		when(discursiveAnswerEvaluator.evaluate(any(), eq(EvaluationRigor.MODERADO)))
				.thenReturn(result(QuestionEvaluationStatus.CORRECT, 80));
		mockMvc.perform(post(questions() + "/" + questionId + "/answers").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"discursiveAnswer\":\"Usa hashCode e equals.\"}"))
				.andExpect(jsonPath("$.feedback.rigorLevel").value("MODERADO"));
	}
}
