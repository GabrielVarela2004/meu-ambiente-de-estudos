package br.com.estudos.estudos_backend.goal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/study-blocks/{blockId}/goals")
public class StudyGoalController {
	private final StudyGoalService service;

	public StudyGoalController(StudyGoalService service) { this.service = service; }

	@PostMapping
	public ResponseEntity<GoalResponse> create(@PathVariable UUID blockId, @Valid @RequestBody GoalRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(GoalResponse.from(
				service.create(blockId, request.title(), request.description(), request.targetUnits())));
	}

	@GetMapping
	public List<GoalResponse> list(@PathVariable UUID blockId) {
		return service.list(blockId).stream().map(GoalResponse::from).toList();
	}

	@PostMapping("/{goalId}/progress")
	public GoalResponse progress(@PathVariable UUID blockId, @PathVariable UUID goalId,
			@Valid @RequestBody ProgressRequest request) {
		return GoalResponse.from(service.addProgress(blockId, goalId, request.units()));
	}

	@DeleteMapping("/{goalId}")
	public ResponseEntity<Void> archive(@PathVariable UUID blockId, @PathVariable UUID goalId) {
		service.archive(blockId, goalId);
		return ResponseEntity.noContent().build();
	}

	public record GoalRequest(@NotBlank @Size(max = 160) String title,
			@Size(max = 2000) String description, @Min(1) @Max(100000) int targetUnits) {}
	public record ProgressRequest(@Min(1) @Max(100000) int units) {}
	public record GoalResponse(UUID id, String title, String description, int targetUnits,
			int completedUnits, StudyGoalStatus status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
		static GoalResponse from(StudyGoal goal) {
			return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDescription(), goal.getTargetUnits(),
					goal.getCompletedUnits(), goal.getStatus(), goal.getCreatedAt(), goal.getUpdatedAt());
		}
	}
}
