package br.com.estudos.estudos_backend;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionOptionRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionAttemptRepository;
import br.com.estudos.estudos_backend.studysession.StudySessionRepository;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseAttemptRepository;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseRepository;
import br.com.estudos.estudos_backend.goal.StudyGoalRepository;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicQuestionRepository;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicRepository;
import br.com.estudos.estudos_backend.chat.ChatConversationEntry;
import br.com.estudos.estudos_backend.chat.ChatConversationRepository;

/**
 * Testes de integração com PostgreSQL real.
 * O perfil "test" aponta para o banco estudos_test: o método cleanDatabase apaga todas as tabelas
 * e nunca deve rodar sobre o banco da aplicação.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstudosBackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StudyBlockRepository repository;

	@Autowired
	private StudyMaterialRepository materialRepository;

	@Autowired
	private StudyQuestionRepository questionRepository;

	@Autowired
	private StudyQuestionOptionRepository questionOptionRepository;

	@Autowired
	private StudyQuestionAttemptRepository attemptRepository;

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

	@BeforeEach
	void cleanDatabase() {
		chatConversationRepository.deleteAllInBatch();
		exerciseAttemptRepository.deleteAllInBatch();
		exerciseRepository.deleteAllInBatch();
		topicQuestionRepository.deleteAllInBatch();
		attemptRepository.deleteAllInBatch();
		questionRepository.deleteAllInBatch();
		topicRepository.deleteAllInBatch();
		goalRepository.deleteAllInBatch();
		sessionRepository.deleteAllInBatch();
		materialRepository.deleteAllInBatch();
		repository.deleteAllInBatch();
	}

	@Test
	void createsAndArchivesStudyBlock() throws Exception {
		String request = """
				{"title":"Java","description":"Fundamentos","learningObjective":"Construir APIs"}
				""";

		mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Java"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.id").isNotEmpty());

		String id = repository.findAll().get(0).getId().toString();
		mockMvc.perform(delete("/api/study-blocks/{id}", id))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/study-blocks"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		mockMvc.perform(get("/api/study-blocks?includeArchived=true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].status").value("ARCHIVED"));
	}

	@Test
	void createsStartsAndCompletesStudySession() throws Exception {
		mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Java\"}"))
				.andExpect(status().isCreated());
		String blockId = repository.findAll().get(0).getId().toString();

		String response = mockMvc.perform(post("/api/study-blocks/{blockId}/sessions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"plannedMinutes\":25,\"notes\":\"Revisar coleções\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PLANNED"))
				.andExpect(jsonPath("$.plannedMinutes").value(25))
				.andReturn().getResponse().getContentAsString();
		String sessionId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();

		mockMvc.perform(post("/api/study-blocks/{blockId}/sessions/{sessionId}/start", blockId, sessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.startedAt").isNotEmpty());

		mockMvc.perform(post("/api/study-blocks/{blockId}/sessions/{sessionId}/complete", blockId, sessionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.endedAt").isNotEmpty());
	}

	@Test
	void createsExerciseAndPersistsAttempt() throws Exception {
		mockMvc.perform(post("/api/study-blocks").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Python\"}")).andExpect(status().isCreated());
		String blockId = repository.findAll().get(0).getId().toString();
		String response = mockMvc.perform(post("/api/study-blocks/{blockId}/exercises", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Listas\",\"prompt\":\"Explique uma lista.\",\"guidance\":\"Use um exemplo.\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
				.andReturn().getResponse().getContentAsString();
		String exerciseId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();

		mockMvc.perform(post("/api/study-blocks/{blockId}/exercises/{exerciseId}/attempts", blockId, exerciseId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"answer\":\"Uma lista mantém uma sequência.\",\"selfRating\":4,\"feedback\":\"Bom entendimento.\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.selfRating").value(4));
		mockMvc.perform(get("/api/study-blocks/{blockId}/exercises/{exerciseId}/attempts", blockId, exerciseId))
				.andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void createsGoalAndCompletesItWithProgress() throws Exception {
		mockMvc.perform(post("/api/study-blocks").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Estatística\"}")).andExpect(status().isCreated());
		String blockId = repository.findAll().get(0).getId().toString();
		String response = mockMvc.perform(post("/api/study-blocks/{blockId}/goals", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Resolver listas\",\"targetUnits\":3}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.completedUnits").value(0))
				.andReturn().getResponse().getContentAsString();
		String goalId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();

		mockMvc.perform(post("/api/study-blocks/{blockId}/goals/{goalId}/progress", blockId, goalId)
						.contentType(MediaType.APPLICATION_JSON).content("{\"units\":3}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.completedUnits").value(3))
				.andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	@Test
	void rejectsBlankTitle() throws Exception {
		mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"   \"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createsUpdatesAndArchivesStudyMaterial() throws Exception {
		String blockRequest = """
				{"title":"Java"}
				""";

		mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content(blockRequest))
				.andExpect(status().isCreated());

		String blockId = repository.findAll().get(0).getId().toString();
		String materialRequest = """
				{"title":"Collections","content":"Listas e conjuntos"}
				""";

		mockMvc.perform(post("/api/study-blocks/{blockId}/materials", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(materialRequest))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Collections"))
				.andExpect(jsonPath("$.content").value("Listas e conjuntos"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		String materialId = materialRepository.findAll().get(0).getId().toString();
		mockMvc.perform(get("/api/study-blocks/{blockId}/materials", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.put("/api/study-blocks/{blockId}/materials/{id}", blockId, materialId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Collections atualizadas\",\"content\":\"Mapas e listas\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Collections atualizadas"));

		mockMvc.perform(delete("/api/study-blocks/{blockId}/materials/{id}", blockId, materialId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/study-blocks/{blockId}/materials", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		mockMvc.perform(get("/api/study-blocks/{blockId}/materials?includeArchived=true", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].status").value("ARCHIVED"));
	}

	@Test
	void createsListsAndAnswersMultipleChoiceQuestion() throws Exception {
		mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Java\"}"))
				.andExpect(status().isCreated());

		String blockId = repository.findAll().get(0).getId().toString();
		String questionRequest = """
				{"prompt":"Qual tipo representa texto?","explanation":"String representa sequências de caracteres.","options":[
				{"text":"String","correct":true},{"text":"Integer","correct":false}]}
				""";

		mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(questionRequest))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.prompt").value("Qual tipo representa texto?"))
				.andExpect(jsonPath("$.difficulty").value("INTERMEDIATE"))
				.andExpect(jsonPath("$.format").value("MULTIPLE_CHOICE"))
				.andExpect(jsonPath("$.options", hasSize(2)));

		String questionId = questionRepository.findAll().get(0).getId().toString();
		String optionId = questionOptionRepository.findAllByQuestionIdOrderByOrderAsc(
				UUID.fromString(questionId)).get(0).getId().toString();
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, questionId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + optionId + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(true))
				.andExpect(jsonPath("$.explanation").value("String representa sequências de caracteres."));
		mockMvc.perform(get("/api/study-blocks/{blockId}/questions", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].reviewIntervalDays").value(3))
				.andExpect(jsonPath("$[0].correctStreak").value(1));

		mockMvc.perform(get("/api/study-blocks/{blockId}/questions/review", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		mockMvc.perform(get("/api/study-blocks/{blockId}/questions/stats", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.attempts").value(1))
				.andExpect(jsonPath("$.correct").value(1))
				.andExpect(jsonPath("$.accuracyPercent").value(100));
		mockMvc.perform(get("/api/study-blocks/{blockId}/questions/stats?days=30", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.attempts").value(1))
				.andExpect(jsonPath("$.accuracyPercent").value(100));
	}

	@Test
	void createsAndAnswersTrueFalseAndDiscursiveQuestionsWithDifficulties() throws Exception {
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		String blockId = mapper.readTree(mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Banco de Dados\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString()).get("id").asText();

		String trueFalseRequest = """
				{
					"prompt":"Uma chave primária pode conter valores nulos em SQL padrão.",
					"explanation":"Chaves primárias exigem unicidade e NOT NULL.",
					"difficulty":"EASY",
					"format":"TRUE_FALSE",
					"options":[
						{"text":"Verdadeiro","correct":false},
						{"text":"Falso","correct":true}
					]
				}
				""";
		String tfResponse = mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(trueFalseRequest))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.difficulty").value("EASY"))
				.andExpect(jsonPath("$.format").value("TRUE_FALSE"))
				.andExpect(jsonPath("$.options", hasSize(2)))
				.andReturn().getResponse().getContentAsString();
		String tfQuestionId = mapper.readTree(tfResponse).get("id").asText();
		String falseOptionId = mapper.readTree(tfResponse).get("options").get(1).get("id").asText();

		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, tfQuestionId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + falseOptionId + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(true));

		String discursiveRequest = """
				{
					"prompt":"Explique as propriedades ACID de uma transação.",
					"explanation":"Transações garantem consistência e isolamento.",
					"difficulty":"HARD",
					"format":"DISCURSIVE",
					"expectedAnswer":"Atomicidade Consistência Isolamento Durabilidade"
				}
				""";
		String discResponse = mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(discursiveRequest))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.difficulty").value("HARD"))
				.andExpect(jsonPath("$.format").value("DISCURSIVE"))
				.andExpect(jsonPath("$.expectedAnswer").value("Atomicidade Consistência Isolamento Durabilidade"))
				.andExpect(jsonPath("$.options", hasSize(0)))
				.andReturn().getResponse().getContentAsString();
		String discQuestionId = mapper.readTree(discResponse).get("id").asText();

		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, discQuestionId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"discursiveAnswer\":\"As propriedades são atomicidade, consistência, isolamento e durabilidade.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(true))
				.andExpect(jsonPath("$.explanation", containsString("Critérios/Gabarito esperado")));

		// Discursiva sem expectedAnswer na criação deve falhar com 400
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prompt\":\"Sem gabarito\",\"format\":\"DISCURSIVE\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listsGlobalDueReviewsWithReasonsAndAllowsRescheduling() throws Exception {
		mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Banco de Dados\"}"))
				.andExpect(status().isCreated());
		String blockId = repository.findAll().get(0).getId().toString();

		String questionRequest = """
				{"prompt":"O que é chave primária?","explanation":"Identificador único da linha.","options":[
				{"text":"Identificador único","correct":true},{"text":"Índice secundário","correct":false}]}
				""";
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(questionRequest))
				.andExpect(status().isCreated());

		String questionId = questionRepository.findAll().get(0).getId().toString();

		mockMvc.perform(get("/api/reviews/due"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].studyBlockTitle").value("Banco de Dados"))
				.andExpect(jsonPath("$[0].recommendationReason").isNotEmpty());

		mockMvc.perform(post("/api/reviews/questions/{questionId}/reschedule?days=7", questionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reviewIntervalDays").value(7));

		mockMvc.perform(get("/api/reviews/due"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void managesTopicsAndEstimatesLearningStateFromRealAnswers() throws Exception {
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		String blockId = mapper.readTree(mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Estrutura de dados\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString()).get("id").asText();

		String topicId = mapper.readTree(mockMvc.perform(post("/api/study-blocks/{blockId}/topics", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Listas\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.learningState").value("INSUFFICIENT_DATA"))
				.andReturn().getResponse().getContentAsString()).get("id").asText();

		String subtopicId = mapper.readTree(mockMvc.perform(post("/api/study-blocks/{blockId}/topics", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Listas encadeadas\",\"parentTopicId\":\"" + topicId + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.parentTopicId").value(topicId))
				.andReturn().getResponse().getContentAsString()).get("id").asText();

		// A hierarquia desta etapa tem somente dois níveis.
		mockMvc.perform(post("/api/study-blocks/{blockId}/topics", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Nível 3\",\"parentTopicId\":\"" + subtopicId + "\"}"))
				.andExpect(status().isBadRequest());

		// Define topicId como pré-requisito de subtopicId e verifica alerta de pré-requisito não consolidado
		mockMvc.perform(put("/api/study-blocks/{blockId}/topics/{topicId}/prerequisite", blockId, subtopicId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prerequisiteTopicId\":\"" + topicId + "\"}"))
				.andExpect(status().isNoContent());

		// Impede dependência circular (topicId ter subtopicId como pré-requisito)
		mockMvc.perform(put("/api/study-blocks/{blockId}/topics/{topicId}/prerequisite", blockId, topicId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prerequisiteTopicId\":\"" + subtopicId + "\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"prompt":"Cada nó aponta para?","options":[
								{"text":"O próximo nó","correct":true},{"text":"A raiz","correct":false}]}
								"""))
				.andExpect(status().isCreated());
		UUID questionId = questionRepository.findAll().get(0).getId();
		String correctOptionId = questionOptionRepository.findAllByQuestionIdOrderByOrderAsc(questionId)
				.get(0).getId().toString();

		mockMvc.perform(post("/api/study-blocks/{blockId}/topics/{topicId}/questions/{questionId}",
						blockId, subtopicId, questionId))
				.andExpect(status().isNoContent());
		// Vincular de novo não duplica o vínculo.
		mockMvc.perform(post("/api/study-blocks/{blockId}/topics/{topicId}/questions/{questionId}",
						blockId, subtopicId, questionId))
				.andExpect(status().isNoContent());

		String subtopicPath = "$[?(@.id=='" + subtopicId + "')]";
		mockMvc.perform(get("/api/study-blocks/{blockId}/topics", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath(subtopicPath + ".learningState").value(hasItem("NOT_STUDIED")))
				.andExpect(jsonPath(subtopicPath + ".prerequisiteTopicId").value(hasItem(topicId)))
				.andExpect(jsonPath(subtopicPath + ".prerequisiteWarning").value(hasItem(containsString("ainda não está consolidado"))))
				.andExpect(jsonPath(subtopicPath + ".questionIds[*]", hasSize(1)));

		// Uma única resposta correta não pode virar "domínio".
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions/{questionId}/answers", blockId, questionId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"optionId\":\"" + correctOptionId + "\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/study-blocks/{blockId}/topics", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath(subtopicPath + ".learningState").value(hasItem("INSUFFICIENT_DATA")))
				.andExpect(jsonPath(subtopicPath + ".attempts").value(hasItem(1)));

		// Questões de outro bloco não podem ser vinculadas.
		String otherBlockId = mapper.readTree(mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Outro bloco\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString()).get("id").asText();
		mockMvc.perform(post("/api/study-blocks/{blockId}/topics/{topicId}/questions/{questionId}",
						otherBlockId, subtopicId, questionId))
				.andExpect(status().isNotFound());

		// Tópico com subtópico ativo não pode ser arquivado antes do subtópico.
		mockMvc.perform(delete("/api/study-blocks/{blockId}/topics/{topicId}", blockId, topicId))
				.andExpect(status().isConflict());

		mockMvc.perform(delete("/api/study-blocks/{blockId}/topics/{topicId}/questions/{questionId}",
						blockId, subtopicId, questionId))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/study-blocks/{blockId}/topics/{topicId}", blockId, subtopicId))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/study-blocks/{blockId}/topics/{topicId}", blockId, topicId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/study-blocks/{blockId}/topics", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(get("/api/study-blocks/{blockId}/topics?includeArchived=true", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void importsTextPdfAndDocxMaterialsAndRejectsInvalidFiles() throws Exception {
		mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Java\"}"))
				.andExpect(status().isCreated());
		String blockId = repository.findAll().get(0).getId().toString();
		MockMultipartFile text = new MockMultipartFile(
				"file", "anotacoes.txt", "text/plain", "Conteúdo importado".getBytes());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.multipart("/api/study-blocks/{blockId}/materials/import", blockId)
						.file(text))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("anotacoes.txt"))
				.andExpect(jsonPath("$.content").value("Conteúdo importado"));

		MockMultipartFile bomText = new MockMultipartFile(
				"file", "com-bom.txt", "text/plain",
				new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'T', 'e', 'x', 't', 'o'});
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.multipart("/api/study-blocks/{blockId}/materials/import", blockId)
						.file(bomText))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content").value("Texto"));

		// PDF real gerado em memória com PDFBox
		byte[] validPdfBytes;
		try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			doc.addPage(page);
			try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
				contentStream.beginText();
				contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				contentStream.newLineAtOffset(50, 700);
				contentStream.showText("Resumo de PDF sobre Estruturas de Dados");
				contentStream.endText();
			}
			doc.save(out);
			validPdfBytes = out.toByteArray();
		}
		MockMultipartFile validPdf = new MockMultipartFile(
				"file", "resumo.pdf", "application/pdf", validPdfBytes);
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.multipart("/api/study-blocks/{blockId}/materials/import", blockId)
						.file(validPdf))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("resumo.pdf"))
				.andExpect(jsonPath("$.content", containsString("Resumo de PDF sobre Estruturas de Dados")));

		// DOCX real (ZIP com word/document.xml) gerado em memória
		byte[] validDocxBytes;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
				ZipOutputStream zos = new ZipOutputStream(out)) {
			zos.putNextEntry(new ZipEntry("word/document.xml"));
			String xml = """
					<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
					<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
						<w:body>
							<w:p><w:r><w:t>Apostila DOCX de Banco de Dados</w:t></w:r></w:p>
						</w:body>
					</w:document>
					""";
			zos.write(xml.getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			zos.finish();
			validDocxBytes = out.toByteArray();
		}
		MockMultipartFile validDocx = new MockMultipartFile(
				"file", "apostila.docx",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				validDocxBytes);
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.multipart("/api/study-blocks/{blockId}/materials/import", blockId)
						.file(validDocx))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("apostila.docx"))
				.andExpect(jsonPath("$.content").value("Apostila DOCX de Banco de Dados"));

		// PDF corrompido deve retornar 400 Bad Request
		MockMultipartFile corruptedPdf = new MockMultipartFile(
				"file", "material.pdf", "application/pdf", "not pdf".getBytes());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.multipart("/api/study-blocks/{blockId}/materials/import", blockId)
						.file(corruptedPdf))
				.andExpect(status().isBadRequest());

		// Extensão não suportada (.exe) deve retornar 415 Unsupported Media Type
		MockMultipartFile unsupportedExe = new MockMultipartFile(
				"file", "script.exe", "application/octet-stream", "bin".getBytes());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.multipart("/api/study-blocks/{blockId}/materials/import", blockId)
						.file(unsupportedExe))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void rejectsQuestionWithoutExactlyOneCorrectOption() throws Exception {
		mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Java\"}"))
				.andExpect(status().isCreated());
		String blockId = repository.findAll().get(0).getId().toString();
		mockMvc.perform(post("/api/study-blocks/{blockId}/questions", blockId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prompt\":\"Inválida\",\"options\":[{\"text\":\"A\",\"correct\":false},{\"text\":\"B\",\"correct\":false}]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void reportsChatUnavailableWithoutProviderKey() throws Exception {
		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"Explique listas\"}"))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void rejectsChatContextForUnknownStudyBlock() throws Exception {
		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"Explique listas\","
								+ "\"studyBlockId\":\"00000000-0000-0000-0000-000000000000\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void listsChatHistoryForStudyBlockAndReportsProviderStatus() throws Exception {
		mockMvc.perform(get("/api/chat/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.providerName").value("OpenAI"))
				.andExpect(jsonPath("$.configured").value(false));

		mockMvc.perform(post("/api/study-blocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Java\"}"))
				.andExpect(status().isCreated());
		UUID blockId = repository.findAll().get(0).getId();

		chatConversationRepository.save(new ChatConversationEntry(
				blockId,
				"O que é polimorfismo?",
				"Polimorfismo permite tratar objetos de subtipos por uma interface comum.",
				"OpenAI",
				"gpt-4o-mini",
				"POO.md"));

		mockMvc.perform(get("/api/study-blocks/{blockId}/chat/messages", blockId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].userMessage").value("O que é polimorfismo?"))
				.andExpect(jsonPath("$[0].referencedMaterials").value("POO.md"));
	}
}
