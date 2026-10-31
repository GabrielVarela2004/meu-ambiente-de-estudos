package br.com.estudos.estudos_backend.learning;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.estudos.estudos_backend.LegacyTopicTables;
import br.com.estudos.estudos_backend.chat.ChatConversationRepository;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseAttemptRepository;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseRepository;
import br.com.estudos.estudos_backend.goal.StudyGoalRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionAttemptRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionRepository;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
import br.com.estudos.estudos_backend.studysession.StudySessionRepository;

/**
 * Testes da Etapa 1: isolamento por usuário, persistência do histórico central
 * de tentativas (teóricas e práticas), numeração incremental de tentativas
 * e consistência dos indicadores entre o bloco e o painel global.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CentralLearningHistoryIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StudyBlockRepository blockRepository;

	@Autowired
	private StudyMaterialRepository materialRepository;

	@Autowired
	private StudyQuestionRepository questionRepository;

	@Autowired
	private StudyQuestionAttemptRepository questionAttemptRepository;

	@Autowired
	private StudySessionRepository sessionRepository;

	@Autowired
	private PracticalExerciseAttemptRepository exerciseAttemptRepository;

	@Autowired
	private PracticalExerciseRepository exerciseRepository;

	@Autowired
	private StudyGoalRepository goalRepository;

	@Autowired
	private ChatConversationRepository chatConversationRepository;

	private final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		chatConversationRepository.deleteAllInBatch();
		exerciseAttemptRepository.deleteAllInBatch();
		exerciseRepository.deleteAllInBatch();
		LegacyTopicTables.clear(jdbcTemplate);
		questionAttemptRepository.deleteAllInBatch();
		questionRepository.deleteAllInBatch();
		goalRepository.deleteAllInBatch();
		sessionRepository.deleteAllInBatch();
		materialRepository.deleteAllInBatch();
		blockRepository.deleteAllInBatch();
	}

	@Test
	void isolatesBlocksQuestionsReviewsAndHistoryBetweenDifferentUsers() throws Exception {
		// Usuário Alice cria um bloco e uma questão
		String aliceBlockJson = mockMvc.perform(post("/api/study-blocks")
						.header(CurrentUserResolver.USER_HEADER, "alice")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Java - Alice\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String aliceBlockId = mapper.readTree(aliceBlockJson).get("id").asText();

		mockMvc.perform(post("/api/study-blocks/{blockId}/questions", aliceBlockId)
						.header(CurrentUserResolver.USER_HEADER, "alice")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"prompt":"O que é JVM?","options":[
								{"text":"Java Virtual Machine","correct":true},
								{"text":"Java Visual Model","correct":false}]}
								"""))
				.andExpect(status().isCreated());

		// Usuário Bob cria outro bloco
		String bobBlockJson = mockMvc.perform(post("/api/study-blocks")
						.header(CurrentUserResolver.USER_HEADER, "bob")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"SQL - Bob\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String bobBlockId = mapper.readTree(bobBlockJson).get("id").asText();

		// Alice vê apenas seu bloco
		mockMvc.perform(get("/api/study-blocks")
						.header(CurrentUserResolver.USER_HEADER, "alice"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].title").value("Java - Alice"));

		// Bob vê apenas seu bloco e recebe 404 ao tentar acessar o bloco de Alice
		mockMvc.perform(get("/api/study-blocks")
						.header(CurrentUserResolver.USER_HEADER, "bob"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].title").value("SQL - Bob"));

		mockMvc.perform(get("/api/study-blocks/{id}", aliceBlockId)
						.header(CurrentUserResolver.USER_HEADER, "bob"))
				.andExpect(status().isNotFound());

		// Revisões globais de Bob não incluem a questão vencida de Alice
		mockMvc.perform(get("/api/reviews/due")
						.header(CurrentUserResolver.USER_HEADER, "bob"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		mockMvc.perform(get("/api/reviews/due")
						.header(CurrentUserResolver.USER_HEADER, "alice"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].studyBlockId").value(aliceBlockId));

		// Cabeçalho com caracteres inválidos deve ser rejeitado com 400 Bad Request
		mockMvc.perform(get("/api/study-blocks")
						.header(CurrentUserResolver.USER_HEADER, "invalid user with spaces!"))
				.andExpect(status().isBadRequest());

		assertEquals(2, blockRepository.count());
		assertEquals(bobBlockId, blockRepository.findAllByOwnerIdOrderByUpdatedAtDesc("bob").get(0).getId().toString());
	}

	@Test
	void recordsTheoreticalAndPracticalAttemptsAndProducesConsistentBlockAndGlobalIndicators() throws Exception {
		String blockJson = mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Spring Boot\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String blockId = mapper.readTree(blockJson).get("id").asText();

		String questionJson = mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"prompt":"Qual anotação marca um serviço Spring?","options":[
								{"text":"@Service","correct":true},
								{"text":"@Table","correct":false}]}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode questionNode = mapper.readTree(questionJson);
		String questionId = questionNode.get("id").asText();
		String correctOptionId = questionNode.get("options").get(0).get("id").asText();
		String wrongOptionId = questionNode.get("options").get(1).get("id").asText();

		// 1ª tentativa (incorreta) e 2ª tentativa (correta)
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, questionId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + wrongOptionId + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(false));

		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, questionId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + correctOptionId + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(true));

		// Cria um exercício prático e registra uma tentativa
		String exerciseJson = mockMvc.perform(post("/api/study-blocks/{blockId}/exercises", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Criar Service\",\"prompt\":\"Implemente a classe UserService.\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String exerciseId = mapper.readTree(exerciseJson).get("id").asText();

		mockMvc.perform(post("/api/study-blocks/{blockId}/exercises/{exerciseId}/attempts", blockId, exerciseId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"answer\":\"@Service public class UserService {}\",\"selfRating\":5,\"feedback\":\"Resolvido sem ajuda\"}"))
				.andExpect(status().isCreated());

		// Verifica histórico unificado do bloco (2 tentativas teóricas + 1 prática = 3 registros)
		mockMvc.perform(get("/api/study-blocks/{blockId}/learning-history", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)));

		// Verifica consistência exata entre indicadores do bloco e indicadores globais para o mesmo conjunto de tentativas
		String blockIndicatorsJson = mockMvc.perform(get("/api/study-blocks/{blockId}/indicators", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.theoreticalEvaluatedAttempts").value(2))
				.andExpect(jsonPath("$.theoreticalCorrectAttempts").value(1))
				.andExpect(jsonPath("$.theoreticalAccuracyPercent").value(50))
				.andExpect(jsonPath("$.practicalEvaluatedAttempts").value(1))
				.andExpect(jsonPath("$.distinctPracticalExercises").value(1))
				.andExpect(jsonPath("$.practicalCompletedAttempts").value(1))
				.andExpect(jsonPath("$.practicalIndependentAttempts").value(1))
				.andReturn().getResponse().getContentAsString();

		String globalIndicatorsJson = mockMvc.perform(get("/api/indicators/global"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertEquals(mapper.readTree(blockIndicatorsJson), mapper.readTree(globalIndicatorsJson));
	}

	@Test
	void excludesUngradableAttemptsFromTheoreticalAccuracyDenominator() {
		LearningCriteriaProperties props = LearningCriteriaProperties.defaults();
		LearningHistoryService service = new LearningHistoryService(
				null, null, null, new CurrentUserResolver(), props);

		LearningAttemptRecord ungradable = new LearningAttemptRecord(
				java.util.UUID.randomUUID(), "default-user", java.time.OffsetDateTime.now(),
				java.util.UUID.randomUUID(), null, LearningModality.THEORETICAL,
				java.util.UUID.randomUUID(), null, ActivityOriginType.BLOCK,
				false, false, 0, null, 1, AssistanceLevel.NONE, 0, false, 0, 0, false, "UNGRADABLE");

		LearningAttemptRecord correct = new LearningAttemptRecord(
				java.util.UUID.randomUUID(), "default-user", java.time.OffsetDateTime.now(),
				java.util.UUID.randomUUID(), null, LearningModality.THEORETICAL,
				java.util.UUID.randomUUID(), null, ActivityOriginType.BLOCK,
				true, true, 100, null, 1, AssistanceLevel.NONE, 0, false, 0, 0, true, "CORRECT");

		LearningHistoryService.ConsolidatedIndicators onlyUngradable = service.aggregateIndicators(java.util.List.of(ungradable));
		assertEquals(0, onlyUngradable.theoreticalEvaluatedAttempts());
		assertNull(onlyUngradable.theoreticalAccuracyPercent());

		LearningHistoryService.ConsolidatedIndicators mixed = service.aggregateIndicators(java.util.List.of(ungradable, correct));
		assertEquals(1, mixed.theoreticalEvaluatedAttempts());
		assertEquals(1, mixed.theoreticalCorrectAttempts());
		assertEquals(100, mixed.theoreticalAccuracyPercent());
	}
}
