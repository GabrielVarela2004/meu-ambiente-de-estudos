package br.com.estudos.estudos_backend.question;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Configurações › Correção das questões abertas: monitoramento da avaliação. */
@RestController
@RequestMapping("/api/settings/evaluation/monitoring")
public class EvaluationMonitoringController {

	private final EvaluationMonitoringService service;

	public EvaluationMonitoringController(EvaluationMonitoringService service) {
		this.service = service;
	}

	@GetMapping
	public EvaluationMonitoringService.MonitoringStats stats() {
		return service.stats();
	}

	/** Reavalia as tentativas recentes sem gravar e devolve a taxa de concordância (custa chamadas de IA). */
	@PostMapping("/consistency-check")
	public EvaluationMonitoringService.ConsistencyReport consistencyCheck(
			@RequestParam(defaultValue = "5") int limit) {
		return service.consistencyCheck(limit);
	}
}
