package br.com.estudos.estudos_backend.career.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerAgentExecutionRepository;
import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerJobRepository;
import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerJobSourceRepository;
import br.com.estudos.estudos_backend.career.domain.AgentExecutionStatus;
import br.com.estudos.estudos_backend.career.domain.AgentTriggerType;
import br.com.estudos.estudos_backend.career.domain.CareerAgentExecution;
import br.com.estudos.estudos_backend.career.domain.CareerJob;
import br.com.estudos.estudos_backend.career.domain.CareerJobSource;
import br.com.estudos.estudos_backend.career.domain.CareerPreferences;
import br.com.estudos.estudos_backend.career.domain.ExperienceLevel;
import br.com.estudos.estudos_backend.career.domain.JobModality;
import br.com.estudos.estudos_backend.career.port.JobProviderPort;
import br.com.estudos.estudos_backend.career.port.RawJobDto;

@Service
public class CareerJobSearchService {

	private static final Logger log = LoggerFactory.getLogger(CareerJobSearchService.class);
	private static final AtomicBoolean RUNNING_LOCK = new AtomicBoolean(false);

	private final CareerJobSourceRepository sourceRepository;
	private final CareerJobRepository jobRepository;
	private final CareerAgentExecutionRepository executionRepository;
	private final CareerPreferencesService preferencesService;
	private final List<JobProviderPort> providers;

	public CareerJobSearchService(
			CareerJobSourceRepository sourceRepository,
			CareerJobRepository jobRepository,
			CareerAgentExecutionRepository executionRepository,
			CareerPreferencesService preferencesService,
			List<JobProviderPort> providers) {
		this.sourceRepository = sourceRepository;
		this.jobRepository = jobRepository;
		this.executionRepository = executionRepository;
		this.preferencesService = preferencesService;
		this.providers = providers;
	}

	public boolean isRunning() {
		return RUNNING_LOCK.get();
	}

	@Transactional
	public CareerAgentExecution executeSearchRound(AgentTriggerType triggerType) {
		if (!RUNNING_LOCK.compareAndSet(false, true)) {
			throw new IllegalStateException("Uma rodada de coleta de vagas já está em execução no momento.");
		}

		CareerAgentExecution execution = new CareerAgentExecution(triggerType);
		execution = executionRepository.save(execution);

		try {
			CareerPreferences preferences = preferencesService.getPreferences();
			List<CareerJobSource> enabledSources = sourceRepository.findByEnabledTrue();

			int maxMinutes = preferences.getMaxRoundMinutes() > 0 ? preferences.getMaxRoundMinutes() : 30;
			OffsetDateTime deadline = OffsetDateTime.now().plusMinutes(maxMinutes);

			int totalFound = 0;
			int totalNew = 0;
			boolean timedOut = false;
			StringBuilder executionLog = new StringBuilder();
			executionLog.append(String.format("Iniciada rodada %s com prazo máximo de %d minutos. Fontes ativas: %d\n",
					triggerType, maxMinutes, enabledSources.size()));

			for (CareerJobSource source : enabledSources) {
				if (OffsetDateTime.now().isAfter(deadline)) {
					timedOut = true;
					executionLog.append(String.format("Prazo limite de %d minutos atingido antes da fonte '%s'.\n", maxMinutes, source.getName()));
					break;
				}

				JobProviderPort provider = providers.stream()
						.filter(p -> p.supports(source))
						.findFirst()
						.orElse(null);

				if (provider == null) {
					source.setLastCheckedAt(OffsetDateTime.now());
					source.setLastStatus("ERROR");
					source.setLastError("Nenhum adaptador registrado para esta fonte.");
					sourceRepository.save(source);
					executionLog.append(String.format("Fonte '%s' ignorada: sem adaptador disponível.\n", source.getName()));
					continue;
				}

				try {
					List<RawJobDto> rawJobs = provider.fetchJobs(source, preferences, 30);
					source.setLastCheckedAt(OffsetDateTime.now());
					source.setLastStatus("SUCCESS");
					source.setLastError(null);
					sourceRepository.save(source);

					int sourceFound = rawJobs.size();
					int sourceNew = 0;

					for (RawJobDto raw : rawJobs) {
						if (raw.url() == null || raw.url().isBlank()) {
							continue;
						}
						Optional<CareerJob> existing = jobRepository.findByUrl(raw.url());
						if (existing.isPresent()) {
							CareerJob existingJob = existing.get();
							existingJob.setLastSeenAt(OffsetDateTime.now());
							existingJob.setActive(true);
							jobRepository.save(existingJob);
						} else {
							CareerJob newJob = new CareerJob(
									source.getId(),
									source.getName(),
									raw.externalId(),
									raw.url(),
									raw.title(),
									raw.company(),
									raw.location(),
									parseModality(raw.modality()),
									parseExperience(raw.experienceLevel()),
									raw.salaryMin(),
									raw.salaryMax(),
									raw.salaryCurrency(),
									raw.description(),
									raw.requirementsSummary(),
									raw.tags() != null ? String.join(", ", raw.tags()) : "",
									raw.publishedAt()
							);
							jobRepository.save(newJob);
							sourceNew++;
						}
					}

					totalFound += sourceFound;
					totalNew += sourceNew;
					executionLog.append(String.format("Fonte '%s': %d vagas coletadas (%d novas).\n", source.getName(), sourceFound, sourceNew));

					if (source.getRateLimitDelayMs() > 0) {
						Thread.sleep(Math.min(source.getRateLimitDelayMs(), 2000));
					}
				} catch (Exception ex) {
					log.warn("Erro ao coletar vagas da fonte {}: {}", source.getName(), ex.getMessage());
					source.setLastCheckedAt(OffsetDateTime.now());
					source.setLastStatus("ERROR");
					source.setLastError(ex.getMessage() != null ? ex.getMessage() : "Erro desconhecido durante a requisição");
					sourceRepository.save(source);
					executionLog.append(String.format("Fonte '%s' falhou: %s\n", source.getName(), ex.getMessage()));
				}
			}

			if (timedOut) {
				execution.markTimedOut(totalFound, totalNew, executionLog.toString());
			} else {
				execution.complete(totalFound, totalNew, executionLog.toString());
			}
			return executionRepository.save(execution);

		} catch (Exception ex) {
			log.error("Falha crítica na rodada de busca do agente: {}", ex.getMessage(), ex);
			execution.fail(ex.getMessage(), "Falha geral do executor: " + ex.getMessage());
			return executionRepository.save(execution);
		} finally {
			RUNNING_LOCK.set(false);
		}
	}

	private JobModality parseModality(String val) {
		if (val == null) return JobModality.REMOTE;
		try {
			return JobModality.valueOf(val.toUpperCase());
		} catch (Exception e) {
			return JobModality.REMOTE;
		}
	}

	private ExperienceLevel parseExperience(String val) {
		if (val == null) return ExperienceLevel.JUNIOR;
		try {
			return ExperienceLevel.valueOf(val.toUpperCase());
		} catch (Exception e) {
			return ExperienceLevel.JUNIOR;
		}
	}
}
