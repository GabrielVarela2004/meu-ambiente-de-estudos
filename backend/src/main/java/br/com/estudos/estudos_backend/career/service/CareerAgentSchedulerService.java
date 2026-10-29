package br.com.estudos.estudos_backend.career.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerAgentExecutionRepository;
import br.com.estudos.estudos_backend.career.domain.AgentTriggerType;
import br.com.estudos.estudos_backend.career.domain.CareerAgentExecution;
import br.com.estudos.estudos_backend.career.domain.CareerPreferences;

@Service
public class CareerAgentSchedulerService {

	private static final Logger log = LoggerFactory.getLogger(CareerAgentSchedulerService.class);

	private final CareerJobSearchService jobSearchService;
	private final CareerPreferencesService preferencesService;
	private final CareerAgentExecutionRepository executionRepository;

	private LocalDate lastScheduledRunDate = null;

	public CareerAgentSchedulerService(
			CareerJobSearchService jobSearchService,
			CareerPreferencesService preferencesService,
			CareerAgentExecutionRepository executionRepository) {
		this.jobSearchService = jobSearchService;
		this.preferencesService = preferencesService;
		this.executionRepository = executionRepository;
	}

	/**
	 * Verifica periodicamente a cada 60 segundos se o horário programado do agente foi alcançado.
	 */
	@Scheduled(fixedDelay = 60000)
	public void checkAndRunScheduled() {
		try {
			CareerPreferences prefs = preferencesService.getPreferences();
			if (!prefs.isAgentEnabled()) {
				return;
			}

			LocalDate today = LocalDate.now();
			if (today.equals(lastScheduledRunDate)) {
				return; // Já executou hoje no horário agendado
			}

			DayOfWeek dayOfWeek = today.getDayOfWeek();
			Set<String> configuredDays = parseDays(prefs.getSearchDays());
			if (!configuredDays.contains(dayOfWeek.name().substring(0, 3))) {
				return; // Não é um dia configurado para busca
			}

			LocalTime nowTime = LocalTime.now();
			boolean timeMatches = (nowTime.getHour() == prefs.getSearchTimeHour() &&
					Math.abs(nowTime.getMinute() - prefs.getSearchTimeMinute()) <= 1);

			if (timeMatches && !jobSearchService.isRunning()) {
				log.info("Iniciando rodada programada do Agente de Vagas às {}:{}", prefs.getSearchTimeHour(), prefs.getSearchTimeMinute());
				lastScheduledRunDate = today;
				CompletableFuture.runAsync(() -> jobSearchService.executeSearchRound(AgentTriggerType.SCHEDULED));
			}
		} catch (Exception ex) {
			log.warn("Erro ao verificar agendamento do agente: {}", ex.getMessage());
		}
	}

	/**
	 * Recuperação de execuções perdidas quando o computador esteve desligado.
	 * Executa uma única rodada consolidada com gatilho RECOVERED se o horário agendado
	 * passou enquanto a aplicação esteve desligada.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationStartupCheckMissedExecutions() {
		CompletableFuture.runAsync(() -> {
			try {
				// Aguarda 5 segundos para estabilização de todos os beans e conexões
				Thread.sleep(5000);

				CareerPreferences prefs = preferencesService.getPreferences();
				if (!prefs.isAgentEnabled()) {
					log.info("Agente de vagas está desabilitado nas preferências. Nenhuma recuperação necessária.");
					return;
				}

				LocalDate today = LocalDate.now();
				DayOfWeek dayOfWeek = today.getDayOfWeek();
				Set<String> configuredDays = parseDays(prefs.getSearchDays());

				if (!configuredDays.contains(dayOfWeek.name().substring(0, 3))) {
					log.info("Hoje ({}) não é dia de agendamento de vagas. Nenhuma recuperação pendente.", dayOfWeek);
					return;
				}

				LocalTime scheduledTime = LocalTime.of(prefs.getSearchTimeHour(), prefs.getSearchTimeMinute());
				LocalTime nowTime = LocalTime.now();

				// Se o horário programado de hoje já passou
				if (nowTime.isAfter(scheduledTime)) {
					Optional<CareerAgentExecution> latest = executionRepository.findLatestFinishedExecution();

					boolean missedToday = true;
					if (latest.isPresent()) {
						LocalDate lastRunDate = latest.get().getStartedAt().toLocalDate();
						if (lastRunDate.equals(today)) {
							missedToday = false;
						}
					}

					if (missedToday && !jobSearchService.isRunning()) {
						log.warn("Detectada execução perdida do Agente de Vagas de hoje ({}) às {}. " +
								"Iniciando rodada consolidada de RECUPERAÇÃO após inicialização.", today, scheduledTime);
						lastScheduledRunDate = today;
						jobSearchService.executeSearchRound(AgentTriggerType.RECOVERED);
					}
				}
			} catch (Exception e) {
				log.error("Erro na verificação de recuperação de execuções perdidas do agente: {}", e.getMessage());
			}
		});
	}

	private Set<String> parseDays(String daysStr) {
		if (daysStr == null || daysStr.isBlank()) {
			return Set.of("MON", "TUE", "WED", "THU", "FRI");
		}
		return Arrays.stream(daysStr.split("[,; ]+"))
				.map(String::trim)
				.map(String::toUpperCase)
				.collect(Collectors.toSet());
	}
}
