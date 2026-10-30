package br.com.estudos.estudos_backend.question;

import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.common.exception.InvalidInputException;

/**
 * Responde questões: objetivas seguem a correção existente; abertas são corrigidas pela IA com rigor. Sem
 * transação aqui: a questão é lida numa transação curta, a IA é chamada fora dela e a tentativa é gravada noutra.
 */
@Service
public class DiscursiveAnswerService {

	private final StudyQuestionService questionService;
	private final DiscursiveAnswerEvaluator evaluator;
	private final EvaluationPreferenceService preferenceService;

	public DiscursiveAnswerService(StudyQuestionService questionService, DiscursiveAnswerEvaluator evaluator,
			EvaluationPreferenceService preferenceService) {
		this.questionService = questionService;
		this.evaluator = evaluator;
		this.preferenceService = preferenceService;
	}

	public StudyQuestionService.StudyQuestionAnswer answer(UUID blockId, UUID questionId, UUID optionId,
			String discursiveAnswer) {
		StudyQuestionService.AnswerContext context = questionService.answerContext(blockId, questionId);
		if (context.format() != StudyQuestionFormat.DISCURSIVE) {
			return questionService.answer(blockId, questionId, optionId, discursiveAnswer);
		}
		if (context.status() == StudyQuestionStatus.ARCHIVED) {
			throw new BusinessRuleException("Questões arquivadas não podem ser respondidas.");
		}
		if (discursiveAnswer == null || discursiveAnswer.isBlank()) {
			throw new InvalidInputException("Questões discursivas exigem uma resposta preenchida.");
		}

		EvaluationRigor rigor = preferenceService.currentRigor();
		DiscursiveEvaluationRules.Result result = null;
		String pendingReason = null;
		try {
			result = evaluator.evaluate(input(context, discursiveAnswer.strip()), rigor);
		} catch (DiscursiveAnswerEvaluator.EvaluationUnavailableException e) {
			pendingReason = e.getMessage();
		}

		StudyQuestionAttempt attempt = questionService.recordDiscursiveAttempt(blockId, questionId, discursiveAnswer,
				result, rigor, null, null, null, null);
		return result == null ? pendingAnswer(attempt.getId(), pendingReason) : graded(attempt.getId(), context, result, rigor);
	}

	/** Reavalia uma tentativa pendente (ex.: a IA voltou). Continua pendente se a IA ainda não estiver disponível. */
	public StudyQuestionService.StudyQuestionAnswer reevaluate(UUID blockId, UUID questionId, UUID attemptId) {
		StudyQuestionAttempt pending = questionService.pendingAttempt(blockId, questionId, attemptId);
		StudyQuestionService.AnswerContext context = questionService.answerContext(blockId, questionId);
		EvaluationRigor rigor = preferenceService.currentRigor();
		DiscursiveEvaluationRules.Result result;
		try {
			result = evaluator.evaluate(input(context, pending.getUserAnswerText()), rigor);
		} catch (DiscursiveAnswerEvaluator.EvaluationUnavailableException e) {
			return pendingAnswer(attemptId, e.getMessage());
		}
		questionService.completePendingAttempt(blockId, questionId, attemptId, result, rigor);
		return graded(attemptId, context, result, rigor);
	}

	private static DiscursiveAnswerEvaluator.Input input(StudyQuestionService.AnswerContext context, String answer) {
		return new DiscursiveAnswerEvaluator.Input(context.prompt(), context.modelAnswer(), context.rubric(),
				context.sourceExcerpt(), answer);
	}

	private static StudyQuestionService.StudyQuestionAnswer pendingAnswer(UUID attemptId, String reason) {
		String message = StudyQuestionService.PENDING_MESSAGE + (reason == null ? "" : " Motivo: " + reason);
		return StudyQuestionService.StudyQuestionAnswer.pending(attemptId, message);
	}

	private static StudyQuestionService.StudyQuestionAnswer graded(UUID attemptId,
			StudyQuestionService.AnswerContext context, DiscursiveEvaluationRules.Result result, EvaluationRigor rigor) {
		StudyQuestionService.DiscursiveFeedback feedback = new StudyQuestionService.DiscursiveFeedback(
				attemptId,
				result.classification(),
				result.overallScore(),
				result.criteriaScores(),
				result.correctPoints(),
				result.missingEssentialConcepts(),
				result.missingTerms(),
				result.errors(),
				result.improvementTip(),
				context.modelAnswer(),
				context.sourceExcerpt(),
				context.sourceTimestamp(),
				context.sourceMaterialId(),
				context.sourceMaterialTitle(),
				context.sourceVideoId(),
				rigor,
				false,
				null);
		return new StudyQuestionService.StudyQuestionAnswer(
				result.classification() == QuestionEvaluationStatus.CORRECT,
				EvaluationJson.summary(result),
				result.classification(),
				result.overallScore(),
				feedback);
	}
}
