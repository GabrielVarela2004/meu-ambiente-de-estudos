package br.com.estudos.estudos_backend.studyblock;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-blocks")
public class StudyBlockController {

	private final StudyBlockService service;

	public StudyBlockController(StudyBlockService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<StudyBlockResponse> create(@Valid @RequestBody StudyBlockRequest request) {
		StudyBlock block = service.create(request.title(), request.description(), request.learningObjective());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(block));
	}

	@GetMapping
	public List<StudyBlockResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.list(includeArchived).stream().map(this::toResponse).toList();
	}

	@GetMapping("/{id}")
	public StudyBlockResponse get(@PathVariable UUID id) {
		return toResponse(service.get(id));
	}

	@PutMapping("/{id}")
	public StudyBlockResponse update(@PathVariable UUID id, @Valid @RequestBody StudyBlockRequest request) {
		return toResponse(
				service.update(id, request.title(), request.description(), request.learningObjective()));
	}

	@PostMapping("/{id}/unarchive")
	public StudyBlockResponse unarchive(@PathVariable UUID id) {
		return toResponse(service.unarchive(id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> archive(@PathVariable UUID id) {
		service.archive(id);
		return ResponseEntity.noContent().build();
	}

	private StudyBlockResponse toResponse(StudyBlock block) {
		return StudyBlockResponse.from(block, service.countActiveSources(block.getId()));
	}

	public record StudyBlockRequest(
			@NotBlank @Size(max = 160) String title,
			@Size(max = 2000) String description,
			@Size(max = 2000) String learningObjective) {
	}

	public record StudyBlockResponse(
			UUID id,
			String title,
			String description,
			String learningObjective,
			StudyBlockStatus status,
			long sourceCount,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {

		static StudyBlockResponse from(StudyBlock block, long sourceCount) {
			return new StudyBlockResponse(
					block.getId(),
					block.getTitle(),
					block.getDescription(),
					block.getLearningObjective(),
					block.getStatus(),
					sourceCount,
					block.getCreatedAt(),
					block.getUpdatedAt());
		}
	}
}
