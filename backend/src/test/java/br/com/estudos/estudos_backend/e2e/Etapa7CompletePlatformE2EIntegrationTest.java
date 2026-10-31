package br.com.estudos.estudos_backend.e2e;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import br.com.estudos.estudos_backend.question.StudyQuestion;
import br.com.estudos.estudos_backend.question.StudyQuestionAttempt;
import br.com.estudos.estudos_backend.question.StudyQuestionAttemptRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionRepository;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
import br.com.estudos.estudos_backend.studysession.StudySessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Etapa7CompletePlatformE2EIntegrationTest {
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
	private ChatConversationRepository chatConversationRepository;

	private final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
		LegacyTopicTables.clear(jdbcTemplate);
		questionAttemptRepository.deleteAllInBatch();
		questionRepository.deleteAllInBatch();
		goalRepository.deleteAllInBatch();
		sessionRepository.deleteAllInBatch();
		materialRepository.deleteAllInBatch();
		blockRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("E2E Completo: Ciclo de vida ponta a ponta desde criação de bloco até revisão e desempenho global")
	void completeEndToEndPlatformFlow() throws Exception {
		// 1. Criação do Bloco de Estudos
		String blockRes = mockMvc.perform(post("/api/study-blocks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"title": "Arquitetura Cloud & Resiliência",
						"description": "Padrões de microsserviços, circuit breaker e observabilidade",
						"learningObjective": "Construir sistemas distribuídos resilientes e tolerantes a falhas"
					}
				"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Arquitetura Cloud & Resiliência"))
				.andReturn().getResponse().getContentAsString();

		String blockId = mapper.readTree(blockRes).get("id").asText();

		// 2. Upload de Material de Estudo Canônico
		String materialRes = mockMvc.perform(post("/api/study-blocks/" + blockId + "/materials")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"title": "Circuit Breaker Pattern com Resilience4j",
						"content": "O Circuit Breaker impede chamadas repetidas a serviços em falha através dos estados CLOSED, OPEN e HALF_OPEN."
					}
				"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Circuit Breaker Pattern com Resilience4j"))
				.andReturn().getResponse().getContentAsString();

		String materialId = mapper.readTree(materialRes).get("id").asText();

		// 4. Criação de Questão de Múltipla Escolha (HARD) vinculada ao Material
		String q1Res = mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"prompt": "Qual estado do Circuit Breaker permite um número limitado de requisições de teste?",
						"explanation": "No estado HALF_OPEN, requisições de teste avaliam a recuperação do serviço antes de fechar o circuito.",
						"difficulty": "HARD",
						"format": "MULTIPLE_CHOICE",
						"materialId": "%s",
						"options": [
							{"text": "CLOSED", "correct": false},
							{"text": "HALF_OPEN", "correct": true},
							{"text": "OPEN", "correct": false}
						]
					}
				""".formatted(materialId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.difficulty").value("HARD"))
				.andReturn().getResponse().getContentAsString();

		JsonNode q1Node = mapper.readTree(q1Res);
		String q1Id = q1Node.get("id").asText();
		String q1CorrectOptId = "";
		for (JsonNode opt : q1Node.get("options")) {
			if ("HALF_OPEN".equals(opt.get("text").asText())) {
				q1CorrectOptId = opt.get("id").asText();
				break;
			}
		}

		// 5. Criação de Questão Discursiva (INTERMEDIATE)
		String q2Res = mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"prompt": "Explique a diferença conceitual entre os estados OPEN e CLOSED do Circuit Breaker.",
						"explanation": "No CLOSED o tráfego flui normalmente; no OPEN as chamadas falham imediatamente.",
						"difficulty": "INTERMEDIATE",
						"format": "DISCURSIVE",
						"expectedAnswer": "CLOSED permite chamadas e OPEN bloqueia com fallback.",
						"materialId": "%s"
					}
				""".formatted(materialId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.format").value("DISCURSIVE"))
				.andReturn().getResponse().getContentAsString();

		String q2Id = mapper.readTree(q2Res).get("id").asText();

		// 6. Resolução da Questão 1 (Múltipla Escolha - Acerto)
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions/" + q1Id + "/answers")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"optionId": "%s"}
				""".formatted(q1CorrectOptId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(true));

		// 7. Resolução da Questão 2 (Discursiva com alta concordância com gabarito)
		mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions/" + q2Id + "/answers")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"discursiveAnswer": "O estado CLOSED permite chamadas e o estado OPEN bloqueia requisições com fallback."}
				"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(true));

		// 8. Verificação de Integridade de Snapshots Imutáveis
		var attempts = questionAttemptRepository.findAll();
		assertEquals(2, attempts.size());

		StudyQuestionAttempt q1Attempt = attempts.stream()
				.filter(a -> a.getQuestionId().equals(UUID.fromString(q1Id)))
				.findFirst().orElseThrow();
		assertNotNull(q1Attempt.getPromptSnapshot());
		assertEquals("Qual estado do Circuit Breaker permite um número limitado de requisições de teste?", q1Attempt.getPromptSnapshot());
		assertNotNull(q1Attempt.getStudyBlockTitleSnapshot());
		assertEquals("Arquitetura Cloud & Resiliência", q1Attempt.getStudyBlockTitleSnapshot());
		assertEquals("HALF_OPEN", q1Attempt.getUserAnswerText());
		assertEquals("HALF_OPEN", q1Attempt.getExpectedAnswerSnapshot());
		assertTrue(q1Attempt.isCorrect());

		// 9. Consulta e Validação do Painel de Desempenho Global (Etapa 5)
		mockMvc.perform(get("/api/performance/global"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall.totalAttempts").value(2))
				.andExpect(jsonPath("$.overall.correctAttempts").value(2))
				.andExpect(jsonPath("$.overall.accuracyPercent").value(100))
				.andExpect(jsonPath("$.blocks", hasSize(1)))
				.andExpect(jsonPath("$.blocks[0].blockTitle").value("Arquitetura Cloud & Resiliência"))
				.andExpect(jsonPath("$.blocks[0].totalAttempts").value(2))
				.andExpect(jsonPath("$.blocks[0].accuracyPercent").value(100))
				.andExpect(jsonPath("$.blocks[0].topics").doesNotExist())
				.andExpect(jsonPath("$.timeline", hasSize(greaterThanOrEqualTo(1))))
				.andExpect(jsonPath("$.recentAttempts", hasSize(2)));

		// 10. Consulta e Validação da Fila de Repetição Espaçada por Dificuldade (Etapa 6)
		mockMvc.perform(get("/api/reviews/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalActiveQuestions").value(2))
				.andExpect(jsonPath("$.learningCount").value(2));

		// 11. Reprogramação Manual de Revisão
		mockMvc.perform(post("/api/reviews/questions/" + q1Id + "/reschedule?days=3"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reviewIntervalDays").value(3));

		StudyQuestion updatedQ1 = questionRepository.findById(UUID.fromString(q1Id)).orElseThrow();
		assertEquals(3, updatedQ1.getReviewIntervalDays());
	}

	@Test
	@DisplayName("Segurança e Isolamento Multi-Usuário: Previne vazamento de dados entre usuários diferentes")
	void enforcesMultiTenantSecurityIsolation() throws Exception {
		// Usuário A cria um bloco de estudos privado
		String userABlockRes = mockMvc.perform(post("/api/study-blocks")
				.header(CurrentUserResolver.USER_HEADER, "usuario-alpha-101")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"title": "Segredos do Usuário Alpha",
						"description": "Dados sensíveis e privados de estudo"
					}
				"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		String userABlockId = mapper.readTree(userABlockRes).get("id").asText();

		// Usuário B tenta listar blocos: não deve ver os blocos do Usuário A
		mockMvc.perform(get("/api/study-blocks")
				.header(CurrentUserResolver.USER_HEADER, "usuario-beta-202"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		// Usuário B tenta acessar diretamente o bloco do Usuário A: deve ser bloqueado com 404
		mockMvc.perform(get("/api/study-blocks/" + userABlockId)
				.header(CurrentUserResolver.USER_HEADER, "usuario-beta-202"))
				.andExpect(status().isNotFound());

		// Usuário B consulta desempenho global: métricas devem estar zeradas e isoladas
		mockMvc.perform(get("/api/performance/global")
				.header(CurrentUserResolver.USER_HEADER, "usuario-beta-202"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall.totalAttempts").value(0))
				.andExpect(jsonPath("$.blocks", hasSize(0)));
	}
}
