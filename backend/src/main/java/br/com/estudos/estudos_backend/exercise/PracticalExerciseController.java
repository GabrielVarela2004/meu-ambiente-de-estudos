package br.com.estudos.estudos_backend.exercise;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.estudos_backend.question.StudyQuestionDifficulty;

@RestController
@RequestMapping("/api/study-blocks/{blockId}/exercises")
public class PracticalExerciseController {
	private final PracticalExerciseService service;

	public PracticalExerciseController(PracticalExerciseService service) { this.service = service; }

	@PostMapping
	public ResponseEntity<ExerciseResponse> create(
			@PathVariable UUID blockId,
			@Valid @RequestBody ExerciseRequest request) {
		PracticalExercise created = service.create(
				blockId,
				request.materialId(),
				request.topicId(),
				request.sourceExcerpt(),
				request.title(),
				request.prompt(),
				request.guidance(),
				request.difficulty(),
				request.exerciseType(),
				request.starterCode(),
				request.solutionCode(),
				request.hintsJson(),
				request.testCasesJson());
		return ResponseEntity.status(HttpStatus.CREATED).body(ExerciseResponse.from(created));
	}

	@PostMapping("/generate")
	public ResponseEntity<ExerciseResponse> generate(
			@PathVariable UUID blockId,
			@RequestBody(required = false) GenerateExerciseRequest request) {
		UUID materialId = request == null ? null : request.materialId();
		UUID topicId = request == null ? null : request.topicId();
		StudyQuestionDifficulty difficulty = request == null ? null : request.difficulty();
		PracticalExerciseType exerciseType = request == null ? null : request.exerciseType();
		PracticalExercise generated = service.generateFromMaterials(blockId, materialId, topicId, difficulty, exerciseType);
		return ResponseEntity.status(HttpStatus.CREATED).body(ExerciseResponse.from(generated));
	}

	@GetMapping
	public List<ExerciseResponse> list(@PathVariable UUID blockId,
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.list(blockId, includeArchived).stream().map(ExerciseResponse::from).toList();
	}

	@DeleteMapping("/{exerciseId}")
	public ResponseEntity<Void> archive(@PathVariable UUID blockId, @PathVariable UUID exerciseId) {
		service.archive(blockId, exerciseId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{exerciseId}/attempts")
	public ResponseEntity<AttemptResponse> submit(@PathVariable UUID blockId, @PathVariable UUID exerciseId,
			@Valid @RequestBody AttemptRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(AttemptResponse.from(
				service.submit(blockId, exerciseId, request.answer(), request.selfRating(), request.feedback())));
	}

	@GetMapping("/{exerciseId}/attempts")
	public List<AttemptResponse> attempts(@PathVariable UUID blockId, @PathVariable UUID exerciseId) {
		return service.attempts(blockId, exerciseId).stream().map(AttemptResponse::from).toList();
	}

	public record ExerciseRequest(
			@NotBlank @Size(max = 160) String title,
			@NotBlank @Size(max = 10000) String prompt,
			@Size(max = 5000) String guidance,
			UUID materialId,
			UUID topicId,
			String sourceExcerpt,
			StudyQuestionDifficulty difficulty,
			PracticalExerciseType exerciseType,
			String starterCode,
			String solutionCode,
			String hintsJson,
			String testCasesJson) {}

	public record GenerateExerciseRequest(
			UUID materialId,
			UUID topicId,
			StudyQuestionDifficulty difficulty,
			PracticalExerciseType exerciseType) {}

	public record AttemptRequest(
			@NotBlank @Size(max = 20000) String answer,
			@Min(1) @Max(5) int selfRating,
			@Size(max = 5000) String feedback) {}

	public record ExerciseResponse(
			UUID id,
			UUID studyBlockId,
			UUID studyMaterialId,
			String studyMaterialTitle,
			UUID topicId,
			String sourceExcerpt,
			String title,
			String prompt,
			String guidance,
			StudyQuestionDifficulty difficulty,
			PracticalExerciseType exerciseType,
			String starterCode,
			String solutionCode,
			String hintsJson,
			String testCasesJson,
			PracticalExerciseStatus status,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
		static ExerciseResponse from(PracticalExercise e) {
			return new ExerciseResponse(
					e.getId(),
					e.getStudyBlock().getId(),
					e.getStudyMaterial() == null ? null : e.getStudyMaterial().getId(),
					e.getStudyMaterial() == null ? null : e.getStudyMaterial().getTitle(),
					e.getTopicId(),
					e.getSourceExcerpt(),
					e.getTitle(),
					e.getPrompt(),
					e.getGuidance(),
					e.getDifficulty(),
					e.getExerciseType(),
					e.getStarterCode(),
					e.getSolutionCode(),
					e.getHintsJson(),
					e.getTestCasesJson(),
					e.getStatus(),
					e.getCreatedAt(),
					e.getUpdatedAt());
		}
	}

	public record AttemptResponse(UUID id, String answer, int selfRating, String feedback, OffsetDateTime createdAt) {
		static AttemptResponse from(PracticalExerciseAttempt a) {
			return new AttemptResponse(a.getId(), a.getAnswer(), a.getSelfRating(), a.getFeedback(), a.getCreatedAt());
		}
	}
}
