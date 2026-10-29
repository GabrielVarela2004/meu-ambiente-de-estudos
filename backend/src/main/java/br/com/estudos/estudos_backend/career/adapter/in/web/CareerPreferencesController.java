package br.com.estudos.estudos_backend.career.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.estudos_backend.career.domain.CareerPreferences;
import br.com.estudos.estudos_backend.career.service.CareerPreferencesService;

@RestController
@RequestMapping("/api/career/preferences")
public class CareerPreferencesController {

	private final CareerPreferencesService service;

	public CareerPreferencesController(CareerPreferencesService service) {
		this.service = service;
	}

	@GetMapping
	public CareerDto.PreferencesResponse getPreferences() {
		return CareerDto.PreferencesResponse.from(service.getPreferences());
	}

	@PutMapping
	public ResponseEntity<CareerDto.PreferencesResponse> updatePreferences(@RequestBody CareerDto.PreferencesRequest request) {
		CareerPreferences updated = service.updatePreferences(
				request.targetRoles(),
				request.experienceLevels(),
				request.technologies(),
				request.mandatoryKeywords(),
				request.preferredKeywords(),
				request.location(),
				request.modality(),
				request.minSalary(),
				request.searchDays(),
				request.searchTimeHour(),
				request.searchTimeMinute(),
				request.maxRoundMinutes(),
				request.agentEnabled()
		);
		return ResponseEntity.ok(CareerDto.PreferencesResponse.from(updated));
	}
}
