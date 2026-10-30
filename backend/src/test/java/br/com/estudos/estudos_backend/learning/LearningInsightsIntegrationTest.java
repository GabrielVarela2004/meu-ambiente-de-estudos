package br.com.estudos.estudos_backend.learning;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.security.CurrentUserResolver;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LearningInsightsIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void aggregatesRealAttemptsByWeekAndDifficulty() throws Exception {
		String user = "insights-" + UUID.randomUUID();
		String blockId = json(mockMvc.perform(post("/api/study-blocks")
				.header(CurrentUserResolver.USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Bloco insights\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

		JsonNode question = json(mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions")
				.header(CurrentUserResolver.USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"prompt\":\"O que e polimorfismo?\",\"difficulty\":\"HARD\",\"format\":\"MULTIPLE_CHOICE\","
						+ "\"options\":[{\"text\":\"Certa\",\"correct\":true},{\"text\":\"Errada\",\"correct\":false}]}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		String questionId = question.get("id").asText();
		String rightOption = null;
		String wrongOption = null;
		for (JsonNode option : question.get("options")) {
			if ("Certa".equals(option.get("text").asText())) rightOption = option.get("id").asText();
			else wrongOption = option.get("id").asText();
		}
		for (String option : new String[] {rightOption, wrongOption, wrongOption}) {
			mockMvc.perform(post("/api/study-blocks/" + blockId + "/questions/" + questionId + "/answers")
					.header(CurrentUserResolver.USER_HEADER, user)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"optionId\":\"" + option + "\"}"))
					.andExpect(status().isOk());
		}

		mockMvc.perform(get("/api/performance/insights?period=WEEK").header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.buckets", hasSize(8)))
				.andExpect(jsonPath("$.buckets[7].questionAttempts").value(3))
				.andExpect(jsonPath("$.buckets[7].accuracyPercent").value(33))
				.andExpect(jsonPath("$.byDifficulty[2].difficulty").value("HARD"))
				.andExpect(jsonPath("$.byDifficulty[2].attempts").value(3))
				.andExpect(jsonPath("$.consistency.activeDaysLast7").value(1))
				.andExpect(jsonPath("$.totalAttempts").value(3))
				.andExpect(jsonPath("$.recommendations").isNotEmpty());

		mockMvc.perform(get("/api/performance/insights?period=MONTH").header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(jsonPath("$.buckets", hasSize(6)));

		mockMvc.perform(post("/api/performance/insights/analysis?period=WEEK").header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.source").value("Regras (sem IA)"))
				.andExpect(jsonPath("$.text").isNotEmpty());
	}

	@Test
	void emptyHistoryProducesStarterGuidance() throws Exception {
		String user = "insights-empty-" + UUID.randomUUID();
		mockMvc.perform(get("/api/performance/insights").header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalAttempts").value(0))
				.andExpect(jsonPath("$.recommendations[0]").value(org.hamcrest.Matchers.startsWith("Comece")));
		mockMvc.perform(post("/api/performance/insights/analysis").header(CurrentUserResolver.USER_HEADER, user))
				.andExpect(jsonPath("$.source").value("Regras (sem dados)"));
	}

	private JsonNode json(String body) throws Exception {
		return objectMapper.readTree(body);
	}
}
