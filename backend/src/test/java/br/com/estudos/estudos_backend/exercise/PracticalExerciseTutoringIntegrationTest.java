package br.com.estudos.estudos_backend.exercise;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import br.com.estudos.estudos_backend.security.CurrentUserResolver;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PracticalExerciseTutoringIntegrationTest {

	private static final String GENERATED_EXERCISE = """
			{"title":"Sobrescrita de emitirSom","prompt":"Crie Animal e Cachorro sobrescrevendo emitirSom().",
			 "guidance":"Use @Override.","steps":["Criar a classe base Animal","Sobrescrever emitirSom em Cachorro","Usar polimorfismo numa lista"],
			 "starterCode":"class Animal {}","solutionCode":"class Cachorro extends Animal { @Override String emitirSom() { return \\"Au\\"; } }",
			 "hintsJson":"[]","testCasesJson":"[]","sourceExcerpt":"Sobrescrita redefine um método herdado."}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AiTutorProvider aiTutorProvider;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private String userId;
	private String blockId;

	@BeforeEach
	void setUp() throws Exception {
		userId = "tutor-" + UUID.randomUUID();
		when(aiTutorProvider.isConfigured()).thenReturn(true);
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
				.content(objectMapper.createObjectNode().put("title", "Sobrescrita")
						.put("content", "Sobrescrita de métodos redefine em uma subclasse um método herdado da superclasse.").toString()))
				.andExpect(status().isCreated());
	}

	@Test
	void tutoringHelpReducesAutonomyAndScoreOfTheNextAttempt() throws Exception {
		when(aiTutorProvider.generateReply(any(AiTutorRequest.class))).thenReturn(
				new AiTutorReply(GENERATED_EXERCISE, "Fake", "fake-model"),
				new AiTutorReply("{\"answer\":\"A assinatura em Cachorro está diferente da de Animal.\",\"helpLevel\":\"SPECIFIC_HINT\",\"stepIndex\":1}", "Fake", "fake-model"));

		String generated = mockMvc.perform(post("/api/study-blocks/" + blockId + "/exercises/generate")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"difficulty\":\"INTERMEDIATE\",\"exerciseType\":\"METHOD_IMPLEMENTATION\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.steps.length()").value(3))
				.andExpect(jsonPath("$.solutionCode").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String exercisePath = "/api/study-blocks/" + blockId + "/exercises/" + objectMapper.readTree(generated).get("id").asText();

		mockMvc.perform(post(exercisePath + "/tutor/messages")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"Meu método não é chamado, por quê?\",\"currentCode\":\"class Cachorro extends Animal { String emitirSom(int x) {} }\",\"stepIndex\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages.length()").value(1))
				.andExpect(jsonPath("$.messages[0].helpLevel").value("SPECIFIC_HINT"))
				.andExpect(jsonPath("$.messages[0].stepIndex").value(1))
				.andExpect(jsonPath("$.autonomyPercent").value(90));

		mockMvc.perform(post(exercisePath + "/attempts")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"answer\":\"class Cachorro extends Animal { @Override String emitirSom() { return \\\"Au\\\"; } }\",\"selfRating\":5}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.autonomyPercent").value(90))
				.andExpect(jsonPath("$.scorePercent").value(90))
				.andExpect(jsonPath("$.hintsUsedCount").value(1))
				.andExpect(jsonPath("$.passed").value(true));

		// Depois da tentativa, a autonomia volta a 100% para a próxima.
		mockMvc.perform(get(exercisePath + "/tutor").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages.length()").value(1))
				.andExpect(jsonPath("$.autonomyPercent").value(100));
	}

	@Test
	void revealedSolutionCapsScoreAndDoesNotPass() throws Exception {
		when(aiTutorProvider.generateReply(any(AiTutorRequest.class)))
				.thenReturn(new AiTutorReply(GENERATED_EXERCISE, "Fake", "fake-model"));
		String generated = mockMvc.perform(post("/api/study-blocks/" + blockId + "/exercises/generate")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String exercisePath = "/api/study-blocks/" + blockId + "/exercises/" + objectMapper.readTree(generated).get("id").asText();

		mockMvc.perform(post(exercisePath + "/tutor/reveal-solution").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages[0].helpLevel").value("FULL_SOLUTION"))
				.andExpect(jsonPath("$.messages[0].assistantAnswer").value(org.hamcrest.Matchers.containsString("@Override")))
				.andExpect(jsonPath("$.solutionRevealed").value(true))
				.andExpect(jsonPath("$.autonomyPercent").value(ExerciseAutonomyCalculator.SOLUTION_REVEALED_CAP_PERCENT));

		mockMvc.perform(post(exercisePath + "/attempts")
				.header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"answer\":\"copiado\",\"selfRating\":5}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.scorePercent").value(30))
				.andExpect(jsonPath("$.solutionRevealed").value(true))
				.andExpect(jsonPath("$.passed").value(false));
	}
}
