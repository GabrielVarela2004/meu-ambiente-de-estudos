package br.com.estudos.estudos_backend.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configurações › Correção das questões abertas: nível de rigor. */
@RestController
@RequestMapping("/api/settings/evaluation")
public class EvaluationPreferenceController {

	private final EvaluationPreferenceService service;

	public EvaluationPreferenceController(EvaluationPreferenceService service) {
		this.service = service;
	}

	@GetMapping
	public EvaluationSettings get() {
		return new EvaluationSettings(service.currentRigor());
	}

	@PutMapping
	public EvaluationSettings update(@Valid @RequestBody EvaluationSettings request) {
		return new EvaluationSettings(service.updateRigor(request.rigorLevel()));
	}

	public record EvaluationSettings(@NotNull EvaluationRigor rigorLevel) {}
}
