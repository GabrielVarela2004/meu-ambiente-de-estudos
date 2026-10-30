package br.com.estudos.estudos_backend.ai;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiSettingsControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AiProviderSettingsRepository settingsRepository;

	@Autowired
	private AiOperationRouteRepository routeRepository;

	@AfterEach
	void cleanUp() {
		settingsRepository.deleteAll();
		routeRepository.deleteAll();
	}

	@Test
	void listsAllProvidersAndOperationsWithoutExposingKeys() throws Exception {
		mockMvc.perform(get("/api/ai/settings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.providers", hasSize(3)))
				.andExpect(jsonPath("$.providers[0].id").value("openai"))
				.andExpect(jsonPath("$.providers[1].id").value("anthropic"))
				.andExpect(jsonPath("$.providers[1].defaultModel").value("claude-opus-5-5"))
				.andExpect(jsonPath("$.providers[2].id").value("gemini"))
				.andExpect(jsonPath("$.providers[0].keyConfigured").value(false))
				.andExpect(jsonPath("$.providers[0].unavailableReason").value("sem chave de API configurada"))
				.andExpect(jsonPath("$.routes", hasSize((int) java.util.Arrays.stream(AiOperation.values()).filter(AiOperation::routable).count())))
				.andExpect(content().string(not(containsString("apiKey"))));
	}

	@Test
	void updatesProviderPreferencesAndRoutes() throws Exception {
		mockMvc.perform(put("/api/ai/providers/anthropic")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":false,\"model\":\"claude-sonnet-5-5\",\"dailyRequestLimit\":50}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false))
				.andExpect(jsonPath("$.effectiveModel").value("claude-sonnet-5-5"))
				.andExpect(jsonPath("$.dailyRequestLimit").value(50));

		mockMvc.perform(put("/api/ai/routes/QUESTION_GENERATION")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"providerId\":\"gemini\",\"fallbackEnabled\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.providerId").value("gemini"))
				.andExpect(jsonPath("$.fallbackEnabled").value(false));

		mockMvc.perform(get("/api/ai/settings"))
				.andExpect(jsonPath("$.routes[1].operation").value("QUESTION_GENERATION"))
				.andExpect(jsonPath("$.routes[1].providerId").value("gemini"));
	}

	@Test
	void rejectsUnknownProviderAndInvalidLimit() throws Exception {
		mockMvc.perform(put("/api/ai/routes/CHAT")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"providerId\":\"inexistente\",\"fallbackEnabled\":true}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/ai/providers/openai")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":true,\"dailyRequestLimit\":0}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void usageSummaryIsAvailable() throws Exception {
		mockMvc.perform(get("/api/ai/usage?days=7")).andExpect(status().isOk());
	}
}
