package br.com.estudos.estudos_backend.studysession;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-blocks/{blockId}/sessions")
public class StudySessionController {
	private final StudySessionService service;

	public StudySessionController(StudySessionService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<StudySessionResponse> create(@PathVariable UUID blockId,
			@Valid @RequestBody StudySessionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(StudySessionResponse.from(service.create(blockId, request.plannedMinutes(), request.notes())));
	}

	@GetMapping
	public List<StudySessionResponse> list(@PathVariable UUID blockId) {
		return service.list(blockId).stream().map(StudySessionResponse::from).toList();
	}

	@PostMapping("/{sessionId}/start")
	public StudySessionResponse start(@PathVariable UUID blockId, @PathVariable UUID sessionId) {
		return StudySessionResponse.from(service.start(blockId, sessionId));
	}

	@PostMapping("/{sessionId}/complete")
	public StudySessionResponse complete(@PathVariable UUID blockId, @PathVariable UUID sessionId) {
		return StudySessionResponse.from(service.complete(blockId, sessionId));
	}

	@PostMapping("/{sessionId}/cancel")
	public StudySessionResponse cancel(@PathVariable UUID blockId, @PathVariable UUID sessionId) {
		return StudySessionResponse.from(service.cancel(blockId, sessionId));
	}

	public record StudySessionRequest(@Min(5) @Max(720) int plannedMinutes, @Size(max = 2000) String notes) {}

	public record StudySessionResponse(UUID id, int plannedMinutes, String notes, StudySessionStatus status,
			OffsetDateTime startedAt, OffsetDateTime endedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
		static StudySessionResponse from(StudySession session) {
			return new StudySessionResponse(session.getId(), session.getPlannedMinutes(), session.getNotes(),
					session.getStatus(), session.getStartedAt(), session.getEndedAt(), session.getCreatedAt(),
					session.getUpdatedAt());
		}
	}
}
