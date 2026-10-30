package br.com.estudos.estudos_backend.learning;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.estudos.estudos_backend.chat.ChatConversationRepository;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseAttemptRepository;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseRepository;
import br.com.estudos.estudos_backend.goal.StudyGoalRepository;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicQuestionRepository;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionAttemptRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionRepository;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
import br.com.estudos.estudos_backend.studysession.StudySessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Etapa5GlobalPerformanceIntegrationTest {
	/** Sem chave de IA nos testes: o corretor das questões abertas é simulado e considera a resposta correta. */
	@org.springframework.test.context.bean.override.mockito.MockitoBean
	private br.com.estudos.estudos_backend.question.DiscursiveAnswerEvaluator discursiveAnswerEvaluator;

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
	private KnowledgeTopicQuestionRepository topicQuestionRepository;

	@Autowired
	private KnowledgeTopicRepository topicRepository;

	@Autowired
	private ChatConversationRepository chatConversationRepository;

	private final ObjectMapper mapper = new ObjectMapper();

	@BeforeEach
	void cleanDatabase() {
		org.mockito.Mockito.when(discursiveAnswerEvaluator.evaluate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new br.com.estudos.estudos_backend.question.DiscursiveEvaluationRules.Result(
						br.com.estudos.estudos_backend.question.QuestionEvaluationStatus.CORRECT, 92, java.util.Map.of(),
						java.util.List.of("Explicou o conceito"), java.util.List.of(), java.util.List.of(), java.util.List.of(),
						"", false));
		chatConversationRepository.deleteAllInBatch();
		exerciseAttemptRepository.deleteAllInBatch();
		exerciseRepository.deleteAllInBatch();
		topicQuestionRepository.deleteAllInBatch();
		questionAttemptRepository.deleteAllInBatch();
		questionRepository.deleteAllInBatch();
		topicRepository.deleteAllInBatch();
		goalRepository.deleteAllInBatch();
		sessionRepository.deleteAllInBatch();
		materialRepository.deleteAllInBatch();
		blockRepository.deleteAllInBatch();
	}

	@Test
	void calculatesGlobalPerformanceKPIsBreakdownsAndTimelineCorrectly() throws Exception {
		// 1. Cria bloco e tópico
		String blockJson = mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Java Core\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String blockId = mapper.readTree(blockJson).get("id").asText();

		String topicJson = mockMvc.perform(post("/api/study-blocks/{blockId}/topics", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Coleções\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String topicId = mapper.readTree(topicJson).get("id").asText();

		// 2. Cria 2 questões (1 de múltipla escolha e 1 discursiva)
		String q1Json = mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"prompt":"Qual List mantém ordem de inserção?",
									"explanation":"ArrayList preserva ordem de inserção.",
									"difficulty":"EASY",
									"format":"MULTIPLE_CHOICE",
									"options":[
										{"text":"ArrayList","correct":true},
										{"text":"HashSet","correct":false}
									]
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode q1Node = mapper.readTree(q1Json);
		String q1Id = q1Node.get("id").asText();
		String q1CorrectOptId = q1Node.get("options").get(0).get("id").asText();
		String q1WrongOptId = q1Node.get("options").get(1).get("id").asText();

		// Vincula q1 ao tópico Coleções
		mockMvc.perform(post("/api/study-blocks/{blockId}/topics/{topicId}/questions/{questionId}",
						blockId, topicId, q1Id))
				.andExpect(status().isNoContent());

		String q2Json = mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"prompt":"O que faz a interface Map em Java?",
									"explanation":"Mapeia chaves únicas para valores.",
									"difficulty":"INTERMEDIATE",
									"format":"DISCURSIVE",
									"expectedAnswer":"Mapeia chaves para valores"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String q2Id = mapper.readTree(q2Json).get("id").asText();

		// 3. Executa tentativas:
		// q1: 1 tentativa errada, depois 1 tentativa certa (2 tentativas na mesma questão)
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, q1Id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + q1WrongOptId + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(false));

		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, q1Id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + q1CorrectOptId + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(true));

		// q2: 1 tentativa discursiva correta
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, q2Id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"discursiveAnswer\":\"A interface Map mapeia chaves para valores.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(true));

		// 4. Consulta Dashboard Global:
		// Total attempts = 3; Unique questions = 2; Evaluated = 3; Correct = 2; Incorrect = 1
		// accuracyPercent = 2/3 * 100 = 67%; errorPercent = 1/3 * 100 = 33%
		String dashboardJson = mockMvc.perform(get("/api/performance/global"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall.totalAttempts").value(3))
				.andExpect(jsonPath("$.overall.uniqueQuestionsAnswered").value(2))
				.andExpect(jsonPath("$.overall.evaluatedAttempts").value(3))
				.andExpect(jsonPath("$.overall.correctAttempts").value(2))
				.andExpect(jsonPath("$.overall.incorrectAttempts").value(1))
				.andExpect(jsonPath("$.overall.accuracyPercent").value(67))
				.andExpect(jsonPath("$.overall.errorPercent").value(33))
				.andExpect(jsonPath("$.blocks", hasSize(1)))
				.andExpect(jsonPath("$.blocks[0].blockTitle").value("Java Core"))
				.andExpect(jsonPath("$.blocks[0].totalAttempts").value(3))
				.andExpect(jsonPath("$.blocks[0].uniqueQuestions").value(2))
				.andExpect(jsonPath("$.blocks[0].topics", hasSize(1)))
				.andExpect(jsonPath("$.blocks[0].topics[0].topicTitle").value("Coleções"))
				.andExpect(jsonPath("$.blocks[0].topics[0].totalAttempts").value(2))
				.andExpect(jsonPath("$.blocks[0].topics[0].accuracyPercent").value(50))
				.andExpect(jsonPath("$.timeline", hasSize(greaterThanOrEqualTo(1))))
				.andExpect(jsonPath("$.recentAttempts", hasSize(3)))
				.andReturn().getResponse().getContentAsString();

		JsonNode root = mapper.readTree(dashboardJson);
		assertNotNull(root.get("timeline").get(0).get("date").asText());
		assertEquals(3, root.get("timeline").get(0).get("totalAttempts").asLong());

		// 5. Testa filtros dinâmicos:
		// Filtro por dificuldade EASY (apenas q1 -> 2 tentativas)
		mockMvc.perform(get("/api/performance/global?difficulty=EASY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall.totalAttempts").value(2))
				.andExpect(jsonPath("$.overall.uniqueQuestionsAnswered").value(1))
				.andExpect(jsonPath("$.overall.accuracyPercent").value(50));

		// Filtro por tópico Coleções (apenas q1 -> 2 tentativas)
		mockMvc.perform(get("/api/performance/global?topicId={topicId}", topicId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall.totalAttempts").value(2))
				.andExpect(jsonPath("$.overall.uniqueQuestionsAnswered").value(1));
	}

	@Test
	void preservesSnapshotsEvenWhenQuestionIsArchivedOrDeleted() throws Exception {
		String blockJson = mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Arquitetura Segura\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String blockId = mapper.readTree(blockJson).get("id").asText();

		String qJson = mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"prompt":"O que é zero trust?",
									"explanation":"Nunca confie sempre verifique.",
									"difficulty":"HARD",
									"format":"DISCURSIVE",
									"expectedAnswer":"Nunca confie sempre verifique"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String qId = mapper.readTree(qJson).get("id").asText();

		// Responde à questão discursiva
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, qId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"discursiveAnswer\":\"Nunca confie sempre verifique no zero trust.\"}"))
				.andExpect(status().isOk());

		// Arquiva a questão
		mockMvc.perform(delete("/api/study-blocks/{blockId}/questions/{questionId}", blockId, qId))
				.andExpect(status().isNoContent());

		// O histórico no dashboard global deve preservar os snapshots imutáveis mesmo com a questão arquivada
		mockMvc.perform(get("/api/performance/global"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall.totalAttempts").value(1))
				.andExpect(jsonPath("$.recentAttempts", hasSize(1)))
				.andExpect(jsonPath("$.recentAttempts[0].prompt").value("O que é zero trust?"))
				.andExpect(jsonPath("$.recentAttempts[0].studyBlockTitle").value("Arquitetura Segura"))
				.andExpect(jsonPath("$.recentAttempts[0].difficulty").value("HARD"))
				.andExpect(jsonPath("$.recentAttempts[0].userAnswer").value("Nunca confie sempre verifique no zero trust."))
				.andExpect(jsonPath("$.recentAttempts[0].explanation").value("Nunca confie sempre verifique."));
	}

	@Test
	void isolatesGlobalPerformanceBetweenDifferentUsers() throws Exception {
		// Usuário Carlos responde questão
		String blockJson = mockMvc.perform(post("/api/study-blocks")
						.header(CurrentUserResolver.USER_HEADER, "carlos")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"DevOps Carlos\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String blockId = mapper.readTree(blockJson).get("id").asText();

		String qJson = mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.header(CurrentUserResolver.USER_HEADER, "carlos")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"prompt":"Docker é container?","options":[{"text":"Sim","correct":true},{"text":"Não","correct":false}]}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode qNode = mapper.readTree(qJson);
		String qId = qNode.get("id").asText();
		String optId = qNode.get("options").get(0).get("id").asText();

		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, qId)
						.header(CurrentUserResolver.USER_HEADER, "carlos")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + optId + "\"}"))
				.andExpect(status().isOk());

		// Carlos vê 1 tentativa
		mockMvc.perform(get("/api/performance/global")
						.header(CurrentUserResolver.USER_HEADER, "carlos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall.totalAttempts").value(1));

		// Diana vê 0 tentativas
		mockMvc.perform(get("/api/performance/global")
						.header(CurrentUserResolver.USER_HEADER, "diana"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall.totalAttempts").value(0))
				.andExpect(jsonPath("$.recentAttempts", hasSize(0)));
	}
}
