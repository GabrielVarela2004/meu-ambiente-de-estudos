package br.com.estudos.estudos_backend.career.adapter.in.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerAgentExecutionRepository;
import br.com.estudos.estudos_backend.career.domain.AgentTriggerType;
import br.com.estudos.estudos_backend.career.domain.CareerAgentExecution;
import br.com.estudos.estudos_backend.career.service.CareerJobSearchService;

@RestController
@RequestMapping("/api/career/agent")
public class CareerAgentController {

	private final CareerJobSearchService jobSearchService;
	private final CareerAgentExecutionRepository executionRepository;

	public CareerAgentController(
			CareerJobSearchService jobSearchService,
			CareerAgentExecutionRepository executionRepository) {
		this.jobSearchService = jobSearchService;
		this.executionRepository = executionRepository;
	}

	@GetMapping("/status")
	public CareerDto.AgentStatusResponse getStatus() {
		boolean running = jobSearchService.isRunning();
		List<CareerAgentExecution> recent = executionRepository.findTop20ByOrderByStartedAtDesc();

		CareerDto.AgentExecutionResponse latest = recent.isEmpty()
				? null
				: CareerDto.AgentExecutionResponse.from(recent.get(0));

		List<CareerDto.AgentExecutionResponse> recentDto = recent.stream()
				.map(CareerDto.AgentExecutionResponse::from)
				.toList();

		return new CareerDto.AgentStatusResponse(running, latest, recentDto);
	}

	@PostMapping("/run")
	public ResponseEntity<CareerDto.AgentExecutionResponse> runAgentManually() {
		CareerAgentExecution execution = jobSearchService.executeSearchRound(AgentTriggerType.MANUAL);
		return ResponseEntity.ok(CareerDto.AgentExecutionResponse.from(execution));
	}
}
