package br.com.estudos.estudos_backend.ai;

import java.util.Arrays;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Configuração dos provedores de IA, rota por operação e consumo. Nunca expõe chaves de API. */
@RestController
@RequestMapping("/api/ai")
public class AiSettingsController {

	private final AiSettingsService settingsService;
	private final AiUsageService usageService;

	public AiSettingsController(AiSettingsService settingsService, AiUsageService usageService) {
		this.settingsService = settingsService;
		this.usageService = usageService;
	}

	@GetMapping("/settings")
	public SettingsResponse settings() {
		List<ProviderResponse> providers = settingsService.providers().stream().map(this::toResponse).toList();
		List<RouteResponse> routes = Arrays.stream(AiOperation.values())
				.filter(AiOperation::routable)
				.map(op -> {
					AiOperationRoute route = settingsService.route(op);
					return new RouteResponse(op, op.label(), route.getProviderId(), route.isFallbackEnabled());
				})
				.toList();
		return new SettingsResponse(providers, routes);
	}

	@PutMapping("/providers/{providerId}")
	public ProviderResponse updateProvider(@PathVariable String providerId, @Valid @RequestBody ProviderRequest request) {
		settingsService.updateProvider(providerId, request.enabled(), request.model(), request.dailyRequestLimit());
		return toResponse(settingsService.provider(providerId));
	}

	@PutMapping("/routes/{operation}")
	public RouteResponse updateRoute(@PathVariable AiOperation operation, @Valid @RequestBody RouteRequest request) {
		AiOperationRoute route = settingsService.updateRoute(operation, request.providerId(), request.fallbackEnabled());
		return new RouteResponse(operation, operation.label(), route.getProviderId(), route.isFallbackEnabled());
	}

	@GetMapping("/usage")
	public List<AiUsageService.UsageSummary> usage(@RequestParam(defaultValue = "7") int days) {
		return usageService.summary(days);
	}

	private ProviderResponse toResponse(AiModelProvider provider) {
		AiProviderSettings settings = settingsService.settings(provider.id());
		return new ProviderResponse(
				provider.id(),
				provider.displayName(),
				provider.isConfigured(),
				settings.isEnabled(),
				provider.defaultModel(),
				settings.getModel(),
				settingsService.effectiveModel(provider),
				provider.suggestedModels(),
				settings.getDailyRequestLimit(),
				settingsService.requestsToday(provider.id()),
				settingsService.unavailabilityReason(provider).orElse(null));
	}

	public record ProviderRequest(boolean enabled, @Size(max = 120) String model,
			@Min(1) @Max(1_000_000) Integer dailyRequestLimit) {}

	public record RouteRequest(@NotBlank String providerId, boolean fallbackEnabled) {}

	public record ProviderResponse(String id, String displayName, boolean keyConfigured, boolean enabled,
			String defaultModel, String modelOverride, String effectiveModel, List<String> suggestedModels,
			Integer dailyRequestLimit, long requestsToday, String unavailableReason) {}

	public record RouteResponse(AiOperation operation, String label, String providerId, boolean fallbackEnabled) {}

	public record SettingsResponse(List<ProviderResponse> providers, List<RouteResponse> routes) {}
}
