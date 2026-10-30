package br.com.estudos.estudos_backend.learning;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Análises de desempenho: agregação semanal/mensal, dificuldade, tópicos, recomendações e análise escrita. */
@RestController
public class LearningInsightsController {

	private final LearningInsightsService service;

	public LearningInsightsController(LearningInsightsService service) {
		this.service = service;
	}

	@GetMapping("/api/performance/insights")
	public LearningInsightsService.Insights insights(
			@RequestParam(defaultValue = "WEEK") LearningInsightsService.Period period) {
		return service.insights(period);
	}

	@PostMapping("/api/performance/insights/analysis")
	public LearningInsightsService.Analysis analysis(
			@RequestParam(defaultValue = "WEEK") LearningInsightsService.Period period) {
		return service.analysis(period);
	}
}
