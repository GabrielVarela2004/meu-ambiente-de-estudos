package br.com.estudos.estudos_backend.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Garante que o agendador do agente de vagas é registrado quando o agendamento está ativo.
 * Sem {@code @EnableScheduling}, os métodos {@code @Scheduled} são ignorados silenciosamente.
 */
@SpringBootTest(properties = "app.scheduling.enabled=true")
@ActiveProfiles("test")
class SchedulingConfigTest {

	@Autowired
	private ScheduledTaskHolder scheduledTaskHolder;

	@Test
	void registersCareerAgentScheduledTask() {
		boolean registered = scheduledTaskHolder.getScheduledTasks().stream()
				.anyMatch(task -> task.toString().contains("CareerAgentSchedulerService.checkAndRunScheduled"));
		assertTrue(registered, "checkAndRunScheduled deveria estar agendado");
	}
}
