package br.com.estudos.estudos_backend.ai;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import br.com.estudos.estudos_backend.common.exception.InvalidInputException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configurações da camada de IA: preferências por provedor, rota por operação e disponibilidade
 * (chave presente, provedor habilitado e limite diário não atingido).
 */
@Service
public class AiSettingsService {

	/** Ordem de preferência padrão e de fallback. */
	private static final List<String> PROVIDER_ORDER = List.of("openai", "anthropic", "gemini");

	private final List<AiModelProvider> providers;
	private final AiProviderSettingsRepository settingsRepository;
	private final AiOperationRouteRepository routeRepository;
	private final AiUsageRecordRepository usageRepository;

	public AiSettingsService(List<AiModelProvider> providers, AiProviderSettingsRepository settingsRepository,
			AiOperationRouteRepository routeRepository, AiUsageRecordRepository usageRepository) {
		this.providers = providers.stream()
				.sorted(Comparator.comparingInt(p -> {
					int index = PROVIDER_ORDER.indexOf(p.id());
					return index < 0 ? Integer.MAX_VALUE : index;
				}))
				.toList();
		this.settingsRepository = settingsRepository;
		this.routeRepository = routeRepository;
		this.usageRepository = usageRepository;
	}

	public List<AiModelProvider> providers() {
		return providers;
	}

	public AiModelProvider provider(String id) {
		return providers.stream().filter(p -> p.id().equals(id)).findFirst()
				.orElseThrow(() -> new InvalidInputException("Provedor de IA desconhecido: " + id));
	}

	@Transactional(readOnly = true)
	public AiProviderSettings settings(String providerId) {
		return settingsRepository.findById(providerId).orElseGet(() -> new AiProviderSettings(providerId));
	}

	public String effectiveModel(AiModelProvider provider) {
		String model = settings(provider.id()).getModel();
		return model == null ? provider.defaultModel() : model;
	}

	@Transactional(readOnly = true)
	public AiOperationRoute route(AiOperation operation) {
		return routeRepository.findById(operation)
				.orElseGet(() -> new AiOperationRoute(operation, defaultProviderId(), true));
	}

	/** Primeiro provedor com chave configurada; sem nenhum, o primeiro da lista. */
	private String defaultProviderId() {
		return providers.stream().filter(AiModelProvider::isConfigured).findFirst()
				.or(() -> providers.stream().findFirst())
				.map(AiModelProvider::id)
				.orElse("openai");
	}

	public long requestsToday(String providerId) {
		OffsetDateTime startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
		return usageRepository.countByProviderIdAndCreatedAtGreaterThanEqual(providerId, startOfDay);
	}

	/** Motivo pelo qual o provedor não pode ser usado agora, ou vazio se está disponível. */
	public Optional<String> unavailabilityReason(AiModelProvider provider) {
		if (!provider.isConfigured()) {
			return Optional.of("sem chave de API configurada");
		}
		AiProviderSettings settings = settings(provider.id());
		if (!settings.isEnabled()) {
			return Optional.of("desabilitado nas configurações");
		}
		Integer limit = settings.getDailyRequestLimit();
		if (limit != null && requestsToday(provider.id()) >= limit) {
			return Optional.of("limite diário de " + limit + " chamadas atingido");
		}
		return Optional.empty();
	}

	/** Provedores a tentar, em ordem: o da rota e, com fallback ligado, os demais disponíveis. */
	public List<AiModelProvider> chainFor(AiOperation operation) {
		AiOperationRoute route = route(operation);
		List<AiModelProvider> chain = new ArrayList<>();
		providers.stream().filter(p -> p.id().equals(route.getProviderId())).findFirst().ifPresent(chain::add);
		if (route.isFallbackEnabled() || chain.isEmpty()) {
			providers.stream().filter(p -> !chain.contains(p)).forEach(chain::add);
		}
		return chain.stream().filter(p -> unavailabilityReason(p).isEmpty()).toList();
	}

	@Transactional
	public AiProviderSettings updateProvider(String providerId, boolean enabled, String model, Integer dailyRequestLimit) {
		provider(providerId);
		if (model != null && model.length() > 120) {
			throw new InvalidInputException("Nome de modelo muito longo.");
		}
		AiProviderSettings settings = settingsRepository.findById(providerId).orElseGet(() -> new AiProviderSettings(providerId));
		settings.update(enabled, model, dailyRequestLimit);
		return settingsRepository.save(settings);
	}

	@Transactional
	public AiOperationRoute updateRoute(AiOperation operation, String providerId, boolean fallbackEnabled) {
		provider(providerId);
		AiOperationRoute route = routeRepository.findById(operation)
				.orElseGet(() -> new AiOperationRoute(operation, providerId, fallbackEnabled));
		route.update(providerId, fallbackEnabled);
		return routeRepository.save(route);
	}
}
