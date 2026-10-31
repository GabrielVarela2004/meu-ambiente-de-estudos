package br.com.estudos.estudos_backend.ai;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registro e resumo do consumo dos provedores de IA. */
@Service
public class AiUsageService {

	private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

	private final AiUsageRecordRepository repository;

	public AiUsageService(AiUsageRecordRepository repository) {
		this.repository = repository;
	}

	/** Grava em transação própria: uma falha aqui nunca derruba a resposta da IA. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(String providerId, String model, AiOperation operation, boolean success,
			Integer httpStatus, Integer inputTokens, Integer outputTokens, long latencyMs) {
		try {
			repository.save(new AiUsageRecord(providerId, model, operation, success, httpStatus,
					inputTokens, outputTokens, (int) Math.min(Integer.MAX_VALUE, latencyMs)));
		} catch (RuntimeException e) {
			log.warn("Não foi possível registrar o uso de IA: {}", e.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public List<UsageSummary> summary(int days) {
		int window = Math.max(1, Math.min(days, 90));
		OffsetDateTime since = OffsetDateTime.now().minusDays(window);
		Map<String, List<AiUsageRecord>> grouped = repository.findAllByCreatedAtGreaterThanEqual(since).stream()
				.collect(Collectors.groupingBy(r -> r.getProviderId() + "|" + r.getOperation()));
		return grouped.values().stream()
				.map(records -> {
					AiUsageRecord first = records.get(0);
					long successes = records.stream().filter(AiUsageRecord::isSuccess).count();
					long inputTokens = records.stream().mapToLong(r -> r.getInputTokens() == null ? 0 : r.getInputTokens()).sum();
					long outputTokens = records.stream().mapToLong(r -> r.getOutputTokens() == null ? 0 : r.getOutputTokens()).sum();
					long avgLatency = Math.round(records.stream().mapToInt(AiUsageRecord::getLatencyMs).average().orElse(0));
					return new UsageSummary(first.getProviderId(), first.getOperation(), records.size(), successes,
							inputTokens, outputTokens, avgLatency);
				})
				.sorted(Comparator.comparing(UsageSummary::providerId).thenComparing(s -> s.operation().ordinal()))
				.toList();
	}

	public record UsageSummary(String providerId, AiOperation operation, long requests, long successes,
			long inputTokens, long outputTokens, long averageLatencyMs) {}

	/** Consumo de hoje por provedor e modelo (base do quadro "uso de hoje vs limite diário"). */
	public record ModelUsageToday(String providerId, String model, long requestsToday, long successesToday) {}

	@Transactional(readOnly = true)
	public List<ModelUsageToday> todayByModel() {
		OffsetDateTime startOfDay = java.time.LocalDate.now()
				.atStartOfDay(java.time.ZoneId.systemDefault()).toOffsetDateTime();
		Map<String, List<AiUsageRecord>> grouped = repository.findAllByCreatedAtGreaterThanEqual(startOfDay).stream()
				.collect(Collectors.groupingBy(r -> r.getProviderId() + "|" + (r.getModel() == null ? "" : r.getModel())));
		return grouped.values().stream()
				.map(records -> {
					AiUsageRecord first = records.get(0);
					long successes = records.stream().filter(AiUsageRecord::isSuccess).count();
					return new ModelUsageToday(first.getProviderId(), first.getModel(), records.size(), successes);
				})
				.sorted(Comparator.comparing(ModelUsageToday::providerId).thenComparing(m -> m.model() == null ? "" : m.model()))
				.toList();
	}
}
