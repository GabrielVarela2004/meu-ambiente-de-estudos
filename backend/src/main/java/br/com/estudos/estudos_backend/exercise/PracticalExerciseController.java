package br.com.estudos.estudos_backend.exercise;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.estudos_backend.question.StudyQuestionDifficulty;

@RestController
@RequestMapping("/api/study-blocks/{blockId}/exercises")
public class PracticalExerciseController {
	private final PracticalExerciseService service;
	private final PracticalExerciseTutorService tutorService;

	public PracticalExerciseController(PracticalExerciseService service, PracticalExerciseTutorService tutorService) {
		this.service = service;
		this.tutorService = tutorService;
	}

	@PostMapping
	public ResponseEntity<ExerciseResponse> create(
			@PathVariable UUID blockId,
			@Valid @RequestBody ExerciseRequest request) {
		PracticalExercise created = service.create(
				blockId,
				request.materialId(),
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
		StudyQuestionDifficulty difficulty = request == null ? null : request.difficulty();
		PracticalExerciseType exerciseType = request == null ? null : request.exerciseType();
		PracticalExercise generated = service.generateFromMaterials(blockId, materialId, difficulty, exerciseType);
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
				service.submit(blockId, exerciseId, request.answer(), request.files(), request.selfRating(), request.feedback())));
	}

	/** "Executar": compila e roda o main; não é tentativa e não usa IA. */
	@PostMapping("/{exerciseId}/run")
	public CodeExecutionResult run(@PathVariable UUID blockId, @PathVariable UUID exerciseId,
			@Valid @RequestBody RunRequest request) {
		return service.execute(blockId, exerciseId, request.files(), request.stdin());
	}

	/** Rascunho salvo automaticamente pelo editor; 204 quando ainda não existe. */
	@GetMapping("/{exerciseId}/draft")
	public ResponseEntity<DraftResponse> draft(@PathVariable UUID blockId, @PathVariable UUID exerciseId) {
		return service.draft(blockId, exerciseId)
				.map(d -> ResponseEntity.ok(DraftResponse.from(d)))
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PutMapping("/{exerciseId}/draft")
	public DraftResponse saveDraft(@PathVariable UUID blockId, @PathVariable UUID exerciseId,
			@Valid @RequestBody DraftRequest request) {
		return DraftResponse.from(service.saveDraft(blockId, exerciseId, request.files()));
	}

	/** "Restaurar código inicial". */
	@DeleteMapping("/{exerciseId}/draft")
	public ResponseEntity<Void> deleteDraft(@PathVariable UUID blockId, @PathVariable UUID exerciseId) {
		service.deleteDraft(blockId, exerciseId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{exerciseId}/attempts")
	public List<AttemptResponse> attempts(@PathVariable UUID blockId, @PathVariable UUID exerciseId) {
		return service.attempts(blockId, exerciseId).stream().map(AttemptResponse::from).toList();
	}

	@GetMapping("/{exerciseId}/tutor")
	public TutorSessionResponse tutorSession(@PathVariable UUID blockId, @PathVariable UUID exerciseId) {
		return TutorSessionResponse.from(tutorService.session(blockId, exerciseId));
	}

	@PostMapping("/{exerciseId}/tutor/messages")
	public TutorSessionResponse askTutor(@PathVariable UUID blockId, @PathVariable UUID exerciseId,
			@Valid @RequestBody TutorMessageRequest request) {
		tutorService.ask(blockId, exerciseId, request.message(), request.currentCode(), request.stepIndex());
		return TutorSessionResponse.from(tutorService.session(blockId, exerciseId));
	}

	@PostMapping("/{exerciseId}/tutor/reveal-solution")
	public TutorSessionResponse revealSolution(@PathVariable UUID blockId, @PathVariable UUID exerciseId) {
		tutorService.revealSolution(blockId, exerciseId);
		return TutorSessionResponse.from(tutorService.session(blockId, exerciseId));
	}

	public record TutorMessageRequest(
			@NotBlank @Size(max = 4000) String message,
			@Size(max = 120000) String currentCode,
			Integer stepIndex) {}

	public record TutorMessageResponse(
			UUID id,
			Integer stepIndex,
			String userMessage,
			String assistantAnswer,
			ExerciseHelpLevel helpLevel,
			String helpLevelLabel,
			int penaltyPercent,
			OffsetDateTime createdAt) {
		static TutorMessageResponse from(PracticalExerciseChatMessage m) {
			return new TutorMessageResponse(m.getId(), m.getStepIndex(), m.getUserMessage(), m.getAssistantAnswer(),
					m.getHelpLevel(), m.getHelpLevel().label(), m.getHelpLevel().penaltyPercent(), m.getCreatedAt());
		}
	}

	/** Histórico do chat do exercício e a autonomia acumulada desde a última tentativa. */
	public record TutorSessionResponse(
			List<TutorMessageResponse> messages,
			int autonomyPercent,
			int hintsUsed,
			boolean solutionRevealed,
			Map<Integer, Integer> penaltyByStep) {
		static TutorSessionResponse from(PracticalExerciseTutorService.TutorSession session) {
			return new TutorSessionResponse(
					session.messages().stream().map(TutorMessageResponse::from).toList(),
					session.autonomy().autonomyPercent(),
					session.autonomy().hintsUsed(),
					session.autonomy().solutionRevealed(),
					session.autonomy().penaltyByStep());
		}
	}

	public record ExerciseRequest(
			@NotBlank @Size(max = 160) String title,
			@NotBlank @Size(max = 10000) String prompt,
			@Size(max = 5000) String guidance,
			UUID materialId,
			String sourceExcerpt,
			StudyQuestionDifficulty difficulty,
			PracticalExerciseType exerciseType,
			String starterCode,
			String solutionCode,
			String hintsJson,
			String testCasesJson) {}

	public record GenerateExerciseRequest(
			UUID materialId,
			StudyQuestionDifficulty difficulty,
			PracticalExerciseType exerciseType) {}

	/** selfRating só é obrigatório quando o exercício não tem correção automática. */
	public record AttemptRequest(
			@Size(max = 20000) String answer,
			@Size(max = ExerciseFiles.MAX_FILES) List<ExerciseFile> files,
			@Min(1) @Max(5) Integer selfRating,
			@Size(max = 5000) String feedback) {}

	/** Projeto inteiro e entrada opcional (stdin) do "Executar". */
	public record RunRequest(@Size(max = ExerciseFiles.MAX_FILES) List<ExerciseFile> files, String stdin) {}

	public record DraftRequest(@Size(max = ExerciseFiles.MAX_FILES) List<ExerciseFile> files) {}

	public record DraftResponse(List<ExerciseFile> files, OffsetDateTime updatedAt) {
		static DraftResponse from(PracticalExerciseDraft draft) {
			return new DraftResponse(ExerciseFiles.fromJson(draft.getFilesJson()), draft.getUpdatedAt());
		}
	}

	public record ExerciseResponse(
			UUID id,
			UUID studyBlockId,
			UUID studyMaterialId,
			String studyMaterialTitle,
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
			List<String> steps,
			String language,
			String testCode,
			Boolean autoEvaluable,
			PracticalExerciseStatus status,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt,
			/** Projeto inicial (vários arquivos) ou o starterCode como arquivo único. */
			List<ExerciseFile> starterFiles) {
		static ExerciseResponse from(PracticalExercise e) {
			return new ExerciseResponse(
					e.getId(),
					e.getStudyBlock().getId(),
					e.getStudyMaterial() == null ? null : e.getStudyMaterial().getId(),
					e.getStudyMaterial() == null ? null : e.getStudyMaterial().getTitle(),
					e.getSourceExcerpt(),
					e.getTitle(),
					e.getPrompt(),
					e.getGuidance(),
					e.getDifficulty(),
					e.getExerciseType(),
					e.getStarterCode(),
					// A solução de referência só é entregue pelo endpoint de tutoria "reveal-solution".
					null,
					e.getHintsJson(),
					e.getTestCasesJson(),
					PracticalExerciseSteps.parse(e.getStepsJson()),
					e.getLanguage(),
					e.getTestCode(),
					e.getAutoEvaluable(),
					e.getStatus(),
					e.getCreatedAt(),
					e.getUpdatedAt(),
					PracticalExerciseService.starterFiles(e));
		}
	}

	/**
	 * @param report relatório do avaliador (testes, checagens de conceito e boas práticas); {@code null} na autoavaliação
	 */
	public record AttemptResponse(UUID id, String answer, int selfRating, String feedback, int scorePercent,
			Integer autonomyPercent, int hintsUsedCount, boolean solutionRevealed, boolean passed,
			ExerciseEvaluationStatus evaluationStatus, Integer correctnessPercent, Integer conceptPercent,
			Integer qualityPercent, int testsTotal, int testsPassed, Map<String, Object> report, OffsetDateTime createdAt,
			List<ExerciseFile> files) {
		private static final ObjectMapper MAPPER = new ObjectMapper();

		static AttemptResponse from(PracticalExerciseAttempt a) {
			boolean passed = a.getEvaluationStatus() == ExerciseEvaluationStatus.SELF_RATED
					? a.isMandatoryTestsPassed()
					: a.getEvaluationStatus() == ExerciseEvaluationStatus.PASSED;
			return new AttemptResponse(a.getId(), a.getAnswer(), a.getSelfRating(), a.getFeedback(), a.getScorePercent(),
					a.getAutonomyPercent(), a.getHintsUsedCount(), a.isSolutionRevealed(), passed,
					a.getEvaluationStatus(), a.getCorrectnessPercent(), a.getConceptPercent(), a.getQualityPercent(),
					a.getTestsTotal(), a.getTestsPassed(), parse(a.getTestResultsJson()), a.getCreatedAt(),
					ExerciseFiles.fromJson(a.getSubmittedFilesJson()));
		}

		@SuppressWarnings("unchecked")
		private static Map<String, Object> parse(String json) {
			if (json == null || json.isBlank()) {
				return null;
			}
			try {
				return MAPPER.readValue(json, Map.class);
			} catch (Exception e) {
				return null;
			}
		}
	}
}
