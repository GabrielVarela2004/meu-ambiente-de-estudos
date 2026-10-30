package br.com.estudos.estudos_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Confirma a condição de {@link SchedulingConfiguration} sem subir a aplicação inteira:
 * ativo por padrão e desligável com {@code app.scheduling.enabled=false} (valor do perfil de teste).
 */
class SchedulingConfigurationConditionTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(SchedulingConfiguration.class);

	@Test
	void enabledByDefault() {
		runner.run(context -> assertThat(context).hasSingleBean(SchedulingConfiguration.class));
	}

	@Test
	void enabledWhenPropertyIsTrue() {
		runner.withPropertyValues("app.scheduling.enabled=true")
				.run(context -> assertThat(context).hasSingleBean(SchedulingConfiguration.class));
	}

	@Test
	void disabledWhenPropertyIsFalse() {
		runner.withPropertyValues("app.scheduling.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(SchedulingConfiguration.class));
	}
}
