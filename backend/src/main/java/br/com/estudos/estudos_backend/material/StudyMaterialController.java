package br.com.estudos.estudos_backend.material;

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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-blocks/{studyBlockId}/materials")
public class StudyMaterialController {

	private final StudyMaterialService service;

	public StudyMaterialController(StudyMaterialService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<StudyMaterialResponse> create(
			@PathVariable UUID studyBlockId, @Valid @RequestBody StudyMaterialRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(StudyMaterialResponse.from(service.create(studyBlockId, request.title(), request.content())));
	}

	@PostMapping(path = "/import", consumes = "multipart/form-data")
	public ResponseEntity<StudyMaterialResponse> importFile(
			@PathVariable UUID studyBlockId, @RequestPart("file") MultipartFile file) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(StudyMaterialResponse.from(service.importTextFile(studyBlockId, file)));
	}

	@GetMapping
	public List<StudyMaterialResponse> list(
			@PathVariable UUID studyBlockId, @RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.list(studyBlockId, includeArchived).stream().map(StudyMaterialResponse::from).toList();
	}

	@PutMapping("/{id}")
	public StudyMaterialResponse update(
			@PathVariable UUID studyBlockId, @PathVariable UUID id,
			@Valid @RequestBody StudyMaterialRequest request) {
		return StudyMaterialResponse.from(service.update(
				studyBlockId, id, request.title(), request.content()));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> archive(@PathVariable UUID studyBlockId, @PathVariable UUID id) {
		service.archive(studyBlockId, id);
		return ResponseEntity.noContent().build();
	}

	public record StudyMaterialRequest(
			@NotBlank @Size(max = 160) String title,
			@NotBlank @Size(max = 100000) String content) {
	}

	public record StudyMaterialResponse(
			UUID id,
			UUID studyBlockId,
			String title,
			String content,
			StudyMaterialStatus status,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {

		static StudyMaterialResponse from(StudyMaterial material) {
			return new StudyMaterialResponse(
					material.getId(),
					material.getStudyBlock().getId(),
					material.getTitle(),
					material.getContent(),
					material.getStatus(),
					material.getCreatedAt(),
					material.getUpdatedAt());
		}
	}
}
