package br.com.estudos.estudos_backend.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** /actuator/health: banco verificado e provedores de IA listados sem chamar as APIs (sem chave nos testes). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthReportsDatabaseAndAiProvidersWithoutKeys() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.db.status").value("UP"))
				.andExpect(jsonPath("$.components.aiProviders.status").value("UP"))
				.andExpect(jsonPath("$.components.aiProviders.details.aiAvailable").value(false))
				.andExpect(jsonPath("$.components.aiProviders.details.providers.gemini").value("sem chave de API configurada"));
	}
}
