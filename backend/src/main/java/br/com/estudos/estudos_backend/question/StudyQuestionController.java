package br.com.estudos.estudos_backend.question;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.common.exception.InvalidInputException;
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

@RestController
@RequestMapping("/api/study-blocks/{studyBlockId}/questions")
public class StudyQuestionController {
	private final StudyQuestionService service;
	private final AiQuestionGeneratorService aiGeneratorService;
	private final DiscursiveAnswerService discursiveAnswerService;

	public StudyQuestionController(StudyQuestionService service, AiQuestionGeneratorService aiGeneratorService,
			DiscursiveAnswerService discursiveAnswerService) {
		this.discursiveAnswerService = discursiveAnswerService;
		this.service = service;
		this.aiGeneratorService = aiGeneratorService;
	}

	@PostMapping
	public ResponseEntity<StudyQuestionResponse> create(@PathVariable UUID studyBlockId,
			@Valid @RequestBody QuestionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(StudyQuestionResponse.from(service.create(
				studyBlockId,
				request.materialId(),
				request.prompt(),
				request.explanation(),
				request.difficulty(),
				request.format(),
				request.expectedAnswer(),
				request.options())));
	}

	@PostMapping("/generate")
	public ResponseEntity<StudyQuestionResponse> generateWithAi(@PathVariable UUID studyBlockId,
			@RequestBody(required = false) GenerateQuestionRequest request) {
		StudyQuestionDifficulty difficulty = request == null ? null : request.difficulty();
		StudyQuestionFormat format = request == null ? null : request.format();
		StudyQuestion generated = aiGeneratorService.generateFromMaterials(studyBlockId, difficulty, format);
		return ResponseEntity.status(HttpStatus.CREATED).body(StudyQuestionResponse.from(generated));
	}

	@PostMapping("/generate-simulado")
	public ResponseEntity<List<StudyQuestionResponse>> generateSimulado(@PathVariable UUID studyBlockId,
			@Valid @RequestBody(required = false) SimuladoRequest request) {
		SimuladoRequest resolved = request == null ? new SimuladoRequest(null, null, null, null) : request;
		List<StudyQuestion> generated = aiGeneratorService.generateSimulado(studyBlockId, resolved.quantity(),
				resolved.difficulty(), resolved.format(), resolved.topicId());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(generated.stream().map(StudyQuestionResponse::from).toList());
	}

	@GetMapping
	public List<StudyQuestionResponse> list(@PathVariable UUID studyBlockId,
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.list(studyBlockId, includeArchived).stream().map(StudyQuestionResponse::from).toList();
	}

	@GetMapping("/stats")
	public StudyQuestionService.StudyQuestionStats stats(@PathVariable UUID studyBlockId,
			@RequestParam(required = false) Integer days) {
		if (days == null) return service.stats(studyBlockId);
		if (days < 1 || days > 365) {
			throw new InvalidInputException("O número de dias deve estar entre 1 e 365.");
		}
		return service.stats(studyBlockId, days);
	}

	@GetMapping("/review")
	public List<StudyQuestionResponse> dueForReview(@PathVariable UUID studyBlockId) {
		return service.dueForReview(studyBlockId).stream().map(StudyQuestionResponse::from).toList();
	}

	@PostMapping("/{id}/answers")
	public StudyQuestionService.StudyQuestionAnswer answer(@PathVariable UUID studyBlockId, @PathVariable UUID id,
			@Valid @RequestBody AnswerRequest request) {
		return discursiveAnswerService.answer(studyBlockId, id, request.optionId(), request.discursiveAnswer());
	}

	/** Modo estudo: mostra a resposta sem avaliar (não grava tentativa nem mexe na revisão). */
	@GetMapping("/{id}/study-view")
	public StudyQuestionService.StudyView studyView(@PathVariable UUID studyBlockId, @PathVariable UUID id) {
		return service.studyView(studyBlockId, id);
	}

	/** Tentativas desta questão que aguardam correção (IA estava indisponível). */
	@GetMapping("/{id}/attempts/pending")
	public List<PendingAttemptResponse> pendingAttempts(@PathVariable UUID studyBlockId, @PathVariable UUID id) {
		return service.pendingAttempts(studyBlockId, id).stream()
				.map(a -> new PendingAttemptResponse(a.getId(), a.getUserAnswerText(), a.getAnsweredAt()))
				.toList();
	}

	/** Reavalia uma tentativa pendente quando a IA volta. */
	@PostMapping("/{id}/attempts/{attemptId}/evaluate")
	public StudyQuestionService.StudyQuestionAnswer reevaluate(@PathVariable UUID studyBlockId, @PathVariable UUID id,
			@PathVariable UUID attemptId) {
		return discursiveAnswerService.reevaluate(studyBlockId, id, attemptId);
	}

	public record PendingAttemptResponse(UUID attemptId, String answer, OffsetDateTime answeredAt) {}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> archive(@PathVariable UUID studyBlockId, @PathVariable UUID id) {
		service.archive(studyBlockId, id);
		return ResponseEntity.noContent().build();
	}

	public record QuestionRequest(
			UUID materialId,
			@NotBlank @Size(max = 2000) String prompt,
			@Size(max = 2000) String explanation,
			StudyQuestionDifficulty difficulty,
			StudyQuestionFormat format,
			@Size(max = 4000) String expectedAnswer,
			@Size(max = 6) List<@Valid OptionRequest> options) {}

	public record SimuladoRequest(
			@Min(1) @Max(10) Integer quantity,
			StudyQuestionDifficulty difficulty,
			StudyQuestionFormat format,
			UUID topicId) {}

	public record GenerateQuestionRequest(
			StudyQuestionDifficulty difficulty,
			StudyQuestionFormat format) {}

	public record OptionRequest(@NotBlank @Size(max = 500) String text, boolean correct) {}

	public record AnswerRequest(UUID optionId, @Size(max = 10000) String discursiveAnswer) {}

	public record StudyQuestionResponse(
			UUID id,
			UUID studyBlockId,
			UUID materialId,
			String materialTitle,
			String sourceExcerpt,
			String prompt,
			String explanation,
			StudyQuestionDifficulty difficulty,
			StudyQuestionFormat format,
			String expectedAnswer,
			StudyQuestionStatus status,
			List<OptionResponse> options,
			OffsetDateTime updatedAt,
			OffsetDateTime nextReviewAt,
			int reviewIntervalDays,
			int correctStreak) {
		static StudyQuestionResponse from(StudyQuestion question) {
			return new StudyQuestionResponse(
					question.getId(),
					question.getStudyBlock().getId(),
					question.getStudyMaterial() == null ? null : question.getStudyMaterial().getId(),
					question.getStudyMaterial() == null ? null : question.getStudyMaterial().getTitle(),
					question.getSourceExcerpt(),
					question.getPrompt(),
					StudyQuestionVisibility.explanationBeforeAnswer(question),
					question.getDifficulty(),
					question.getFormat(),
					StudyQuestionVisibility.expectedAnswerBeforeAnswer(question),
					question.getStatus(),
					question.getOptions().stream().map(option -> new OptionResponse(
							option.getId(), option.getText(), option.getOrder())).toList(),
					question.getUpdatedAt(),
					question.getNextReviewAt(),
					question.getReviewIntervalDays(),
					question.getCorrectStreak());
		}
	}

	public record OptionResponse(UUID id, String text, int order) {}
}
