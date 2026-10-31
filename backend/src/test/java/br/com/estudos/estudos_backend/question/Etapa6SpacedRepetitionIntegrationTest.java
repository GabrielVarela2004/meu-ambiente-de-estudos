package br.com.estudos.estudos_backend.question;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
import br.com.estudos.estudos_backend.studysession.StudySessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Etapa6SpacedRepetitionIntegrationTest {

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
	void scalesIntervalsBasedOnDifficultyAndResetsOnFailure() throws Exception {
		// 1. Cria bloco de estudo
		String blockJson = mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Algoritmos e Estruturas\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String blockId = mapper.readTree(blockJson).get("id").asText();

		// 2. Cria 3 questões: EASY, INTERMEDIATE e HARD
		String easyQJson = mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"prompt":"O que é um array?",
									"difficulty":"EASY",
									"format":"MULTIPLE_CHOICE",
									"options":[
										{"text":"Coleção linear indexada","correct":true},
										{"text":"Árvore rubro-negra","correct":false}
									]
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String easyQId = mapper.readTree(easyQJson).get("id").asText();
		String easyCorrectOptId = mapper.readTree(easyQJson).get("options").get(0).get("id").asText();

		String interQJson = mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"prompt":"O que é uma fila?",
									"difficulty":"INTERMEDIATE",
									"format":"MULTIPLE_CHOICE",
									"options":[
										{"text":"FIFO (First In First Out)","correct":true},
										{"text":"LIFO","correct":false}
									]
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String interQId = mapper.readTree(interQJson).get("id").asText();
		String interCorrectOptId = mapper.readTree(interQJson).get("options").get(0).get("id").asText();

		String hardQJson = mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"prompt":"Complexidade do QuickSort no pior caso?",
									"difficulty":"HARD",
									"format":"MULTIPLE_CHOICE",
									"options":[
										{"text":"O(n^2)","correct":true},
										{"text":"O(n log n)","correct":false}
									]
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String hardQId = mapper.readTree(hardQJson).get("id").asText();
		String hardCorrectOptId = mapper.readTree(hardQJson).get("options").get(0).get("id").asText();
		String hardWrongOptId = mapper.readTree(hardQJson).get("options").get(1).get("id").asText();

		// Responde EASY corretamente (streak 1 -> 4 dias)
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, easyQId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + easyCorrectOptId + "\"}"))
				.andExpect(status().isOk());

		// Responde INTERMEDIATE corretamente (streak 1 -> 3 dias)
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, interQId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + interCorrectOptId + "\"}"))
				.andExpect(status().isOk());

		// Responde HARD corretamente (streak 1 -> 1 dia)
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, hardQId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + hardCorrectOptId + "\"}"))
				.andExpect(status().isOk());

		// Verifica intervalos diferenciados por dificuldade
		StudyQuestion easyQ = questionRepository.findById(UUID.fromString(easyQId)).orElseThrow();
		StudyQuestion interQ = questionRepository.findById(UUID.fromString(interQId)).orElseThrow();
		StudyQuestion hardQ = questionRepository.findById(UUID.fromString(hardQId)).orElseThrow();

		assertEquals(4, easyQ.getReviewIntervalDays());
		assertEquals(3, interQ.getReviewIntervalDays());
		assertEquals(1, hardQ.getReviewIntervalDays());
		assertEquals("LEARNING", easyQ.getRetentionLevel());

		// 2º acerto no HARD (streak 2 -> 2 dias)
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, hardQId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + hardCorrectOptId + "\"}"))
				.andExpect(status().isOk());
		hardQ = questionRepository.findById(UUID.fromString(hardQId)).orElseThrow();
		assertEquals(2, hardQ.getReviewIntervalDays());
		assertEquals(2, hardQ.getCorrectStreak());

		// Erro no HARD (reseta streak para 0 e intervalo para 1)
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, hardQId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + hardWrongOptId + "\"}"))
				.andExpect(status().isOk());
		hardQ = questionRepository.findById(UUID.fromString(hardQId)).orElseThrow();
		assertEquals(1, hardQ.getReviewIntervalDays());
		assertEquals(0, hardQ.getCorrectStreak());
		assertEquals("NEEDS_REINFORCEMENT", hardQ.getRetentionLevel());
	}

	@Test
	void prioritizesHardAndUnconsolidatedQuestionsInGlobalReviewBatteryAndSummary() throws Exception {
		// Bloco 1: Concorrência
		String block1Json = mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Java Concorrente\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String block1Id = mapper.readTree(block1Json).get("id").asText();

		// Bloco 2: Banco de Dados
		String block2Json = mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"PostgreSQL Avançado\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String block2Id = mapper.readTree(block2Json).get("id").asText();

		// Cria questão EASY no Bloco 1
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions", block1Id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"prompt":"Thread implementa Runnable?","difficulty":"EASY","format":"MULTIPLE_CHOICE",
									"options":[{"text":"Sim","correct":true},{"text":"Não","correct":false}]
								}
								"""))
				.andExpect(status().isCreated());

		// Cria questão HARD no Bloco 2
		String hardQJson = mockMvc.perform(post("/api/study-blocks/{blockId}/questions", block2Id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"prompt":"O que é Serializable Snapshot Isolation?","difficulty":"HARD","format":"MULTIPLE_CHOICE",
									"options":[{"text":"Isolamento com detecção de dependências rw","correct":true},{"text":"Bloqueio total de tabela","correct":false}]
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String hardQId = mapper.readTree(hardQJson).get("id").asText();

		// Bateria de revisão inter-blocos: deve trazer as questões vencidas
		// e a questão HARD com streak 0 deve ser a PRIMEIRA recomendação (prioridade máxima)
		String batteryJson = mockMvc.perform(get("/api/reviews/battery?limit=10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(hardQId))
				.andExpect(jsonPath("$[0].difficulty").value("HARD"))
				.andExpect(jsonPath("$[0].recommendationReason").value(org.hamcrest.Matchers.containsString("Prioridade Máxima")))
				.andReturn().getResponse().getContentAsString();

		// Resumo de retenção e prontidão global (/api/reviews/summary)
		mockMvc.perform(get("/api/reviews/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalDue").value(2))
				.andExpect(jsonPath("$.totalNeedsReinforcement").value(2))
				.andExpect(jsonPath("$.dueByDifficulty.HARD").value(1))
				.andExpect(jsonPath("$.dueByDifficulty.EASY").value(1));

		// Reagendamento manual
		mockMvc.perform(post("/api/reviews/questions/{questionId}/reschedule?days=5", hardQId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reviewIntervalDays").value(5));
	}
}
