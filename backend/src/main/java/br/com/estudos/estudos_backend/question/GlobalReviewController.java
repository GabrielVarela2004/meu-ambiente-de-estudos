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

import br.com.estudos.estudos_backend.exercise.PracticalExerciseService;

@RestController
@RequestMapping("/api/reviews")
public class GlobalReviewController {

	private final StudyQuestionService questionService;
	private final PracticalExerciseService exerciseService;

	public GlobalReviewController(StudyQuestionService questionService, PracticalExerciseService exerciseService) {
		this.questionService = questionService;
		this.exerciseService = exerciseService;
	}

	@GetMapping("/due")
	public List<DueReviewQuestionResponse> listGlobalDueQuestions() {
		return questionService.dueForGlobalReview().stream()
				.map(DueReviewQuestionResponse::from)
				.toList();
	}

	@GetMapping("/battery")
	public List<DueReviewQuestionResponse> getReviewBattery(
			@RequestParam(required = false, defaultValue = "10") Integer limit,
			@RequestParam(required = false) StudyQuestionDifficulty difficulty,
			@RequestParam(required = false) UUID studyBlockId) {
		return questionService.batteryForGlobalReview(limit, difficulty, studyBlockId).stream()
				.map(DueReviewQuestionResponse::from)
				.toList();
	}

	@GetMapping("/summary")
	public StudyQuestionService.GlobalReviewSummary getReviewSummary() {
		return questionService.getGlobalReviewSummary();
	}

	@PostMapping("/questions/{questionId}/reschedule")
	public ResponseEntity<DueReviewQuestionResponse> reschedule(
			@PathVariable UUID questionId,
			@RequestParam(defaultValue = "1") int days) {
		StudyQuestion updated = questionService.rescheduleReview(questionId, days);
		return ResponseEntity.ok(DueReviewQuestionResponse.from(updated));
	}

	@PostMapping("/exercises/{exerciseId}/reschedule")
	public ResponseEntity<Void> rescheduleExercise(
			@PathVariable UUID exerciseId,
			@RequestParam(defaultValue = "1") int days) {
		exerciseService.rescheduleReview(exerciseId, days);
		return ResponseEntity.ok().build();
	}

	public record DueReviewQuestionResponse(
			UUID id,
			UUID studyBlockId,
			String studyBlockTitle,
			String prompt,
			String explanation,
			StudyQuestionDifficulty difficulty,
			StudyQuestionFormat format,
			String expectedAnswer,
			List<StudyQuestionController.OptionResponse> options,
			OffsetDateTime nextReviewAt,
			int reviewIntervalDays,
			int correctStreak,
			String retentionLevel,
			String recommendationReason) {

		static DueReviewQuestionResponse from(StudyQuestion question) {
			String reason;
			if (question.getCorrectStreak() == 0) {
				if (question.getDifficulty() == StudyQuestionDifficulty.HARD) {
					reason = "Prioridade Máxima: Questão Difícil com erro recente ou sem acertos consolidados.";
				} else {
					reason = "Prioridade Alta: erro recente ou questão ainda em fase de fixação.";
				}
			} else {
				if (question.getDifficulty() == StudyQuestionDifficulty.HARD) {
					reason = "Reforço Frequente: Questão Difícil programada após " + question.getCorrectStreak()
							+ " acerto(s) (intervalo de " + question.getReviewIntervalDays() + " dias).";
				} else if (question.getDifficulty() == StudyQuestionDifficulty.EASY) {
					reason = "Manutenção de Longo Prazo: Questão Fácil consolidada (" + question.getCorrectStreak()
							+ " acertos, intervalo de " + question.getReviewIntervalDays() + " dias).";
				} else {
					reason = "Revisão Espaçada: dificuldade intermediária após " + question.getCorrectStreak()
							+ " acerto(s) consecutivo(s) (intervalo de " + question.getReviewIntervalDays() + " dias).";
				}
			}

			return new DueReviewQuestionResponse(
					question.getId(),
					question.getStudyBlock().getId(),
					question.getStudyBlock().getTitle(),
					question.getPrompt(),
					question.getExplanation(),
					question.getDifficulty(),
					question.getFormat(),
					question.getExpectedAnswer(),
					question.getOptions().stream()
							.map(o -> new StudyQuestionController.OptionResponse(o.getId(), o.getText(), o.getOrder()))
							.toList(),
					question.getNextReviewAt(),
					question.getReviewIntervalDays(),
					question.getCorrectStreak(),
					question.getRetentionLevel(),
					reason);
		}
	}
}
