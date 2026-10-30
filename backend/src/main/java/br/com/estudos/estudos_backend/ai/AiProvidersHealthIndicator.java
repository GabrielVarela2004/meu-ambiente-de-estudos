package br.com.estudos.estudos_backend.ai;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Saúde da camada de IA em {@code /actuator/health} (componente {@code aiProviders}). Não chama as APIs (não gasta
 * cota): só diz, por provedor, se há chave, se está habilitado e se o limite diário foi atingido. Fica UP mesmo sem
 * nenhum provedor, porque a plataforma funciona sem IA (fallbacks); o detalhe {@code aiAvailable} indica isso.
 */
@Component("aiProviders")
public class AiProvidersHealthIndicator implements HealthIndicator {

	private final AiSettingsService settingsService;

	public AiProvidersHealthIndicator(AiSettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@Override
	public Health health() {
		Map<String, Object> providers = new LinkedHashMap<>();
		boolean anyAvailable = false;
		for (AiModelProvider provider : settingsService.providers()) {
			String reason = settingsService.unavailabilityReason(provider).orElse(null);
			anyAvailable |= reason == null;
			providers.put(provider.id(), reason == null ? "disponível" : reason);
		}
		return Health.up()
				.withDetail("aiAvailable", anyAvailable)
				.withDetail("providers", providers)
				.build();
	}
}
