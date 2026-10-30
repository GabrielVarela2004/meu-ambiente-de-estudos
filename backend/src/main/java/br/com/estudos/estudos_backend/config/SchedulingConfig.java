package br.com.estudos.estudos_backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ativa os métodos {@code @Scheduled} (ex.: agente de vagas).
 * Pode ser desligado com {@code app.scheduling.enabled=false} (usado nos testes).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
