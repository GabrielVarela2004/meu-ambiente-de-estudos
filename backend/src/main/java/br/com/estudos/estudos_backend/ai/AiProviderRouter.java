package br.com.estudos.estudos_backend.ai;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.chat.AiTutorProvider;

/**
 * Implementação principal de {@link AiTutorProvider}: escolhe o provedor configurado para a operação
 * atual ({@link AiOperationContext}), tenta os demais quando há fallback e registra cada chamada.
 */
@Primary
@Component
public class AiProviderRouter implements AiTutorProvider {

	private static final Logger log = LoggerFactory.getLogger(AiProviderRouter.class);

	/** Falhas transitórias ou do provedor: vale tentar o próximo da cadeia. */
	private static final Set<Integer> RETRYABLE_STATUSES = Set.of(422, 429, 500, 502, 503, 504);

	private final AiSettingsService settingsService;
	private final AiUsageService usageService;

	public AiProviderRouter(AiSettingsService settingsService, AiUsageService usageService) {
		this.settingsService = settingsService;
		this.usageService = usageService;
	}

	@Override
	public String providerName() {
		return primaryProvider().displayName();
	}

	@Override
	public boolean isConfigured() {
		return !settingsService.chainFor(AiOperationContext.current()).isEmpty();
	}

	@Override
	public String configuredModel() {
		return settingsService.effectiveModel(primaryProvider());
	}

	@Override
	public AiTutorReply generateReply(String systemInstruction, String userPrompt) {
		return generateReply(AiTutorRequest.ofText(systemInstruction, userPrompt));
	}

	@Override
	public AiTutorReply generateReply(AiTutorRequest request) {
		AiOperation operation = AiOperationContext.current();
		List<AiModelProvider> chain = settingsService.chainFor(operation);
		if (chain.isEmpty()) {
			String reasons = settingsService.providers().stream()
					.map(p -> p.displayName() + ": " + settingsService.unavailabilityReason(p).orElse("disponível"))
					.collect(Collectors.joining("; "));
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Nenhum provedor de IA disponível para " + operation.label() + " (" + reasons + ").");
		}

		ResponseStatusException lastFailure = null;
		for (int i = 0; i < chain.size(); i++) {
			AiModelProvider provider = chain.get(i);
			String model = settingsService.effectiveModel(provider);
			long start = System.nanoTime();
			try {
				AiModelProvider.AiModelResult result = provider.generate(request, model);
				usageService.record(provider.id(), result.model(), operation, true, 200,
						result.inputTokens(), result.outputTokens(), elapsedMs(start));
				return new AiTutorReply(result.text(), provider.displayName(), result.model());
			} catch (ResponseStatusException e) {
				int status = e.getStatusCode().value();
				usageService.record(provider.id(), model, operation, false, status, null, null, elapsedMs(start));
				lastFailure = e;
				boolean hasNext = i < chain.size() - 1;
				if (!RETRYABLE_STATUSES.contains(status) || !hasNext) {
					throw e;
				}
				log.warn("Provedor {} falhou em {} (HTTP {}): {}. Tentando o próximo.",
						provider.id(), operation, status, e.getReason());
			} catch (RuntimeException e) {
				usageService.record(provider.id(), model, operation, false, null, null, null, elapsedMs(start));
				lastFailure = new ResponseStatusException(HttpStatus.BAD_GATEWAY,
						"Falha inesperada no provedor " + provider.displayName() + ".");
				if (i == chain.size() - 1) {
					throw lastFailure;
				}
				log.warn("Provedor {} falhou em {}: {}. Tentando o próximo.", provider.id(), operation, e.toString());
			}
		}
		throw lastFailure;
	}

	private AiModelProvider primaryProvider() {
		List<AiModelProvider> chain = settingsService.chainFor(AiOperationContext.current());
		if (!chain.isEmpty()) {
			return chain.get(0);
		}
		return settingsService.provider(settingsService.route(AiOperationContext.current()).getProviderId());
	}

	private static long elapsedMs(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}
}
