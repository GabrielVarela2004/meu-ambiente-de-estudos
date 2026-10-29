package br.com.estudos.estudos_backend.question;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class GlobalReviewController {

	private final StudyQuestionService questionService;

	public GlobalReviewController(StudyQuestionService questionService) {
		this.questionService = questionService;
	}

	@GetMapping("/due")
	public List<DueReviewQuestionResponse> listGlobalDueQuestions() {
		return questionService.dueForGlobalReview().stream()
				.map(DueReviewQuestionResponse::from)
				.toList();
	}

	@PostMapping("/questions/{questionId}/reschedule")
	public ResponseEntity<DueReviewQuestionResponse> reschedule(
			@PathVariable UUID questionId,
			@RequestParam(defaultValue = "1") int days) {
		StudyQuestion updated = questionService.rescheduleReview(questionId, days);
		return ResponseEntity.ok(DueReviewQuestionResponse.from(updated));
	}

	public record DueReviewQuestionResponse(
			UUID id,
			UUID studyBlockId,
			String studyBlockTitle,
			String prompt,
			String explanation,
			StudyQuestionDifficulty difficulty,
			StudyQuestionFormat format,
			List<StudyQuestionController.OptionResponse> options,
			OffsetDateTime nextReviewAt,
			int reviewIntervalDays,
			int correctStreak,
			String recommendationReason) {

		static DueReviewQuestionResponse from(StudyQuestion question) {
			String reason = question.getCorrectStreak() == 0
					? "Prioridade alta: erro recente ou questão ainda não consolidada."
					: "Revisão programada após " + question.getCorrectStreak()
							+ " acerto(s) consecutivo(s) (intervalo de " + question.getReviewIntervalDays() + " dias).";

			return new DueReviewQuestionResponse(
					question.getId(),
					question.getStudyBlock().getId(),
					question.getStudyBlock().getTitle(),
					question.getPrompt(),
					question.getExplanation(),
					question.getDifficulty(),
					question.getFormat(),
					question.getOptions().stream()
							.map(o -> new StudyQuestionController.OptionResponse(o.getId(), o.getText(), o.getOrder()))
							.toList(),
					question.getNextReviewAt(),
					question.getReviewIntervalDays(),
					question.getCorrectStreak(),
					reason);
		}
	}
}
