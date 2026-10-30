package br.com.estudos.estudos_backend.question;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.common.exception.InvalidInputException;
import br.com.estudos.estudos_backend.common.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.knowledge.KnowledgeTopic;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicRepository;
import br.com.estudos.estudos_backend.learning.ActivityOriginType;
import br.com.estudos.estudos_backend.learning.AssistanceLevel;
import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialSourceType;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;
import br.com.estudos.estudos_backend.studyblock.StudyBlockStatus;

@Service
public class StudyQuestionService {
	private final StudyQuestionRepository repository;
	private final StudyBlockService blockService;
	private final StudyMaterialRepository materialRepository;
	private final StudyQuestionAttemptRepository attemptRepository;
	private final KnowledgeTopicRepository knowledgeTopicRepository;
	private final CurrentUserResolver currentUserResolver;

	public StudyQuestionService(StudyQuestionRepository repository, StudyBlockService blockService,
			StudyMaterialRepository materialRepository, StudyQuestionAttemptRepository attemptRepository) {
		this(repository, blockService, materialRepository, attemptRepository, null, new CurrentUserResolver());
	}

	public StudyQuestionService(
			StudyQuestionRepository repository,
			StudyBlockService blockService,
			StudyMaterialRepository materialRepository,
			StudyQuestionAttemptRepository attemptRepository,
			CurrentUserResolver currentUserResolver) {
		this(repository, blockService, materialRepository, attemptRepository, null, currentUserResolver);
	}

	@Autowired
	public StudyQuestionService(
			StudyQuestionRepository repository,
			StudyBlockService blockService,
			StudyMaterialRepository materialRepository,
			StudyQuestionAttemptRepository attemptRepository,
			KnowledgeTopicRepository knowledgeTopicRepository,
			CurrentUserResolver currentUserResolver) {
		this.repository = repository;
		this.blockService = blockService;
		this.materialRepository = materialRepository;
		this.attemptRepository = attemptRepository;
		this.knowledgeTopicRepository = knowledgeTopicRepository;
		this.currentUserResolver = currentUserResolver == null ? new CurrentUserResolver() : currentUserResolver;
	}

	/** Enunciados mais recentes do bloco (só do tópico, se informado), enviados à IA para ela não repetir perguntas. */
	@Transactional(readOnly = true)
	public List<String> recentPrompts(UUID blockId, UUID topicId, int limit) {
		return repository.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyQuestionStatus.ACTIVE).stream()
				.filter(question -> topicId == null || topicId.equals(question.getTopicId()))
				.map(StudyQuestion::getPrompt)
				.filter(prompt -> prompt != null && !prompt.isBlank())
				.limit(limit)
				.toList();
	}

	@Transactional
	public StudyQuestion create(UUID blockId, UUID materialId, String prompt, String explanation,
			List<StudyQuestionController.OptionRequest> options) {
		return create(blockId, materialId, null, null, prompt, explanation,
				StudyQuestionDifficulty.INTERMEDIATE, StudyQuestionFormat.MULTIPLE_CHOICE, null, options);
	}

	@Transactional
	public StudyQuestion create(UUID blockId, UUID materialId, String prompt, String explanation,
			StudyQuestionDifficulty difficulty, StudyQuestionFormat format, String expectedAnswer,
			List<StudyQuestionController.OptionRequest> options) {
		return create(blockId, materialId, null, null, prompt, explanation, difficulty, format, expectedAnswer, options);
	}

	@Transactional
	public StudyQuestion create(UUID blockId, UUID materialId, UUID topicId, String prompt, String explanation,
			StudyQuestionDifficulty difficulty, StudyQuestionFormat format, String expectedAnswer,
			List<StudyQuestionController.OptionRequest> options) {
		return create(blockId, materialId, topicId, null, prompt, explanation, difficulty, format, expectedAnswer, options);
	}

	@Transactional
	public StudyQuestion create(UUID blockId, UUID materialId, UUID topicId, String sourceExcerpt,
			String prompt, String explanation, StudyQuestionDifficulty difficulty,
			StudyQuestionFormat format, String expectedAnswer,
			List<StudyQuestionController.OptionRequest> options) {
		StudyBlock block = blockService.get(blockId);
		if (block.getStatus() == StudyBlockStatus.ARCHIVED) {
			throw new BusinessRuleException("Blocos de estudo arquivados não podem receber questões.");
		}
		StudyMaterial material = null;
		if (materialId != null) {
			material = materialRepository.findById(materialId)
					.filter(item -> item.getStudyBlock().getId().equals(blockId))
					.filter(item -> item.getStatus() == StudyMaterialStatus.ACTIVE)
					.orElseThrow(() -> new InvalidInputException("Material de estudo inválido."));
		}

		StudyQuestionDifficulty resolvedDifficulty = difficulty == null ? StudyQuestionDifficulty.INTERMEDIATE : difficulty;
		StudyQuestionFormat resolvedFormat = format == null ? StudyQuestionFormat.MULTIPLE_CHOICE : format;
		String normalizedExpected = expectedAnswer == null || expectedAnswer.isBlank() ? null : expectedAnswer.trim();
		String normalizedExcerpt = sourceExcerpt == null || sourceExcerpt.isBlank() ? null : sourceExcerpt.trim();

		validateFormatAndInputs(resolvedFormat, normalizedExpected, options);

		StudyQuestion question = new StudyQuestion(block, material, topicId, normalizedExcerpt, prompt.trim(), explanation,
				resolvedDifficulty, resolvedFormat, normalizedExpected);
		if (resolvedFormat != StudyQuestionFormat.DISCURSIVE && options != null) {
			for (int i = 0; i < options.size(); i++) {
				StudyQuestionController.OptionRequest option = options.get(i);
				question.addOption(option.text().trim(), i, option.correct());
			}
		}
		return repository.save(question);
	}

	@Transactional(readOnly = true)
	public Map<UUID, String> getTopicTitlesForBlock(UUID blockId) {
		if (knowledgeTopicRepository == null) {
			return Map.of();
		}
		List<KnowledgeTopic> topics = knowledgeTopicRepository.findAllByStudyBlockIdOrderByCreatedAtAsc(blockId);
		Map<UUID, String> result = new LinkedHashMap<>();
		for (KnowledgeTopic topic : topics) {
			result.put(topic.getId(), topic.getTitle());
		}
		return result;
	}

	@Transactional(readOnly = true)
	public List<StudyQuestion> list(UUID blockId, boolean includeArchived) {
		blockService.get(blockId);
		return includeArchived
				? repository.findAllByStudyBlockIdOrderByUpdatedAtDesc(blockId)
				: repository.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyQuestionStatus.ACTIVE);
	}

	@Transactional
	public void archive(UUID blockId, UUID id) {
		StudyQuestion question = getOwned(blockId, id);
		question.archive();
		repository.save(question);
	}

	@Transactional
	public StudyQuestionAnswer answer(UUID blockId, UUID id, UUID optionId) {
		return answer(blockId, id, optionId, null, null, null, null, null);
	}

	@Transactional
	public StudyQuestionAnswer answer(UUID blockId, UUID id, UUID optionId, String discursiveAnswer) {
		return answer(blockId, id, optionId, discursiveAnswer, null, null, null, null);
	}

	@Transactional
	public StudyQuestionAnswer answer(
			UUID blockId,
			UUID id,
			UUID optionId,
			String discursiveAnswer,
			UUID studySessionId,
			ActivityOriginType originType,
			Integer timeSpentSeconds,
			AssistanceLevel helpLevel) {
		StudyQuestion question = getOwned(blockId, id);
		String ownerId = currentUserResolver.currentUserId();
		int attemptNumber = (int) attemptRepository.countByOwnerIdAndQuestionId(ownerId, id) + 1;
		UUID sourceMaterialId = question.getStudyMaterial() == null ? null : question.getStudyMaterial().getId();
		ActivityOriginType resolvedOrigin = originType == null ? ActivityOriginType.BLOCK : originType;
		AssistanceLevel resolvedHelp = helpLevel == null ? AssistanceLevel.NONE : helpLevel;

		String blockTitle = question.getStudyBlock().getTitle();
		UUID topicId = question.getTopicId();
		String topicTitle = null;
		if (topicId != null && knowledgeTopicRepository != null) {
			topicTitle = knowledgeTopicRepository.findById(topicId)
					.map(KnowledgeTopic::getTitle)
					.orElse(null);
		}
		String sourceTitle = question.getStudyMaterial() != null ? question.getStudyMaterial().getTitle() : null;
		String sourceExcerpt = question.getSourceExcerpt();
		String difficultyName = question.getDifficulty().name();
		String formatName = question.getFormat().name();

		if (question.getFormat() == StudyQuestionFormat.DISCURSIVE) {
			// Questões abertas são corrigidas pela IA (DiscursiveAnswerService, fora de transação). Chamado direto
			// aqui, sem correção, a tentativa fica pendente: nunca se corrige por palavras-chave.
			StudyQuestionAttempt attempt = recordDiscursiveAttempt(blockId, id, discursiveAnswer, null,
					EvaluationPreferenceService.DEFAULT_RIGOR, studySessionId, resolvedOrigin, timeSpentSeconds, resolvedHelp);
			return StudyQuestionAnswer.pending(attempt.getId(), PENDING_MESSAGE);
		}

		if (optionId == null) {
			throw new InvalidInputException("Questões objetivas exigem a escolha de uma alternativa.");
		}
		StudyQuestionOption option = question.getOptions().stream()
				.filter(item -> item.getId().equals(optionId))
				.findFirst()
				.orElseThrow(() -> new InvalidInputException("A alternativa não pertence a esta questão."));
		boolean correct = option.isCorrect();
		question.scheduleNextReview(correct);
		repository.save(question);

		String expectedOptionText = question.getOptions().stream()
				.filter(StudyQuestionOption::isCorrect)
				.map(StudyQuestionOption::getText)
				.findFirst()
				.orElse(null);

		StudyQuestionAttempt attempt = new StudyQuestionAttempt(
				ownerId,
				blockId,
				id,
				topicId,
				studySessionId,
				sourceMaterialId,
				optionId,
				null,
				correct,
				resolvedOrigin,
				correct ? QuestionEvaluationStatus.CORRECT : QuestionEvaluationStatus.INCORRECT,
				correct ? 100 : 0,
				timeSpentSeconds,
				attemptNumber,
				resolvedHelp,
				0,
				question.getExplanation());
		attempt.populateSnapshots(
				question.getPrompt(),
				option.getText(),
				expectedOptionText,
				question.getExplanation(),
				blockTitle,
				topicTitle,
				difficultyName,
				formatName,
				sourceTitle,
				sourceExcerpt);
		attemptRepository.save(attempt);
		return new StudyQuestionAnswer(correct, question.getExplanation());
	}

	@Transactional(readOnly = true)
	public List<StudyQuestion> dueForReview(UUID blockId) {
		blockService.get(blockId);
		return repository.findAllByStudyBlockIdAndStatusAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
				blockId, StudyQuestionStatus.ACTIVE, OffsetDateTime.now());
	}

	@Transactional(readOnly = true)
	public List<StudyQuestion> dueForGlobalReview() {
		String ownerId = currentUserResolver.currentUserId();
		return repository.findGlobalDueForReviewByOwner(
				ownerId, StudyQuestionStatus.ACTIVE, StudyBlockStatus.ACTIVE, OffsetDateTime.now());
	}

	@Transactional(readOnly = true)
	public List<StudyQuestion> batteryForGlobalReview(Integer limit, StudyQuestionDifficulty difficulty, UUID studyBlockId) {
		String ownerId = currentUserResolver.currentUserId();
		int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 100));
		List<StudyQuestion> questions = repository.findGlobalDueForReviewByOwner(
				ownerId, StudyQuestionStatus.ACTIVE, StudyBlockStatus.ACTIVE, OffsetDateTime.now());
		return questions.stream()
				.filter(q -> difficulty == null || q.getDifficulty() == difficulty)
				.filter(q -> studyBlockId == null || q.getStudyBlock().getId().equals(studyBlockId))
				.limit(safeLimit)
				.toList();
	}

	@Transactional(readOnly = true)
	public GlobalReviewSummary getGlobalReviewSummary() {
		String ownerId = currentUserResolver.currentUserId();
		List<StudyQuestion> due = dueForGlobalReview();
		List<StudyQuestion> active = repository.findAllByOwnerIdAndStatus(ownerId, StudyQuestionStatus.ACTIVE);

		long memorized = active.stream().filter(q -> "MEMORIZED".equals(q.getRetentionLevel())).count();
		long learning = active.stream().filter(q -> "LEARNING".equals(q.getRetentionLevel())).count();
		long needsReinforcement = active.stream().filter(q -> "NEEDS_REINFORCEMENT".equals(q.getRetentionLevel())).count();

		java.util.Map<String, Long> dueByDifficulty = new java.util.LinkedHashMap<>();
		dueByDifficulty.put("EASY", due.stream().filter(q -> q.getDifficulty() == StudyQuestionDifficulty.EASY).count());
		dueByDifficulty.put("INTERMEDIATE", due.stream().filter(q -> q.getDifficulty() == StudyQuestionDifficulty.INTERMEDIATE).count());
		dueByDifficulty.put("HARD", due.stream().filter(q -> q.getDifficulty() == StudyQuestionDifficulty.HARD).count());

		return new GlobalReviewSummary(
				due.size(),
				memorized,
				learning,
				learning,
				needsReinforcement,
				needsReinforcement,
				active.size(),
				dueByDifficulty);
	}

	@Transactional
	public StudyQuestion rescheduleReview(UUID questionId, int days) {
		if (days < 1 || days > 365) {
			throw new InvalidInputException("O número de dias deve estar entre 1 e 365.");
		}
		String ownerId = currentUserResolver.currentUserId();
		StudyQuestion question = repository.findById(questionId)
				.filter(item -> item.getStudyBlock().getOwnerId().equals(ownerId))
				.orElseThrow(() -> new NotFoundException("Questão não encontrada."));
		if (question.getStatus() == StudyQuestionStatus.ARCHIVED) {
			throw new BusinessRuleException("Questões arquivadas não podem ser reagendadas.");
		}
		question.rescheduleManual(days);
		return repository.save(question);
	}

	@Transactional(readOnly = true)
	public StudyQuestionStats stats(UUID blockId) {
		blockService.get(blockId);
		List<StudyQuestionAttempt> attempts = attemptRepository.findAllByStudyBlockIdOrderByAnsweredAtDesc(blockId)
				.stream()
				.filter(attempt -> attempt.getEvaluationStatus() == null || attempt.getEvaluationStatus().isGraded())
				.toList();
		long correct = attempts.stream().filter(StudyQuestionAttempt::isCorrect).count();
		return new StudyQuestionStats(attempts.size(), correct,
				attempts.isEmpty() ? 0 : Math.round(correct * 100.0 / attempts.size()));
	}

	@Transactional(readOnly = true)
	public StudyQuestionStats stats(UUID blockId, int days) {
		blockService.get(blockId);
		OffsetDateTime since = OffsetDateTime.now().minusDays(days);
		List<StudyQuestionAttempt> attempts = attemptRepository
				.findAllByStudyBlockIdAndAnsweredAtGreaterThanEqualOrderByAnsweredAtDesc(blockId, since)
				.stream()
				.filter(attempt -> attempt.getEvaluationStatus() == null || attempt.getEvaluationStatus().isGraded())
				.toList();
		long correct = attempts.stream().filter(StudyQuestionAttempt::isCorrect).count();
		return new StudyQuestionStats(attempts.size(), correct,
				attempts.isEmpty() ? 0 : Math.round(correct * 100.0 / attempts.size()));
	}

	private StudyQuestion getOwned(UUID blockId, UUID id) {
		blockService.get(blockId);
		StudyQuestion question = repository.findById(id)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new NotFoundException("Questão não encontrada."));
		if (question.getStatus() == StudyQuestionStatus.ARCHIVED) {
			throw new BusinessRuleException("Questões arquivadas não podem ser respondidas.");
		}
		return question;
	}

	private void validateFormatAndInputs(StudyQuestionFormat format, String expectedAnswer,
			List<StudyQuestionController.OptionRequest> options) {
		if (format == StudyQuestionFormat.DISCURSIVE) {
			if (expectedAnswer == null || expectedAnswer.isBlank()) {
				throw new InvalidInputException(
						"Questões discursivas exigem uma resposta esperada com critérios de avaliação.");
			}
			return;
		}

		if (format == StudyQuestionFormat.TRUE_FALSE) {
			if (options == null || options.size() != 2
					|| options.stream().filter(StudyQuestionController.OptionRequest::correct).count() != 1
					|| options.stream().anyMatch(option -> option.text() == null || option.text().isBlank())) {
				throw new InvalidInputException(
						"Questões de verdadeiro ou falso exigem exatamente 2 alternativas e 1 correta.");
			}
			return;
		}

		if (options == null || options.size() < 2 || options.size() > 6
				|| options.stream().filter(StudyQuestionController.OptionRequest::correct).count() != 1
				|| options.stream().anyMatch(option -> option.text() == null || option.text().isBlank())) {
			throw new InvalidInputException(
					"Questões objetivas exigem de 2 a 6 alternativas e exatamente uma correta.");
		}
	}

	/** Grava a resposta-modelo, a rubrica e o minuto do trecho de uma questão aberta recém-gerada. */
	@Transactional
	public StudyQuestion attachRubric(UUID blockId, UUID questionId, String modelAnswer, String rubricJson,
			String sourceTimestamp) {
		StudyQuestion question = getOwned(blockId, questionId);
		question.applyRubric(modelAnswer, rubricJson, sourceTimestamp);
		return repository.save(question);
	}

	/** Dados de uma questão aberta para a correção (lidos numa transação curta, antes da chamada à IA). */
	@Transactional(readOnly = true)
	public AnswerContext answerContext(UUID blockId, UUID questionId) {
		StudyQuestion question = getOwned(blockId, questionId);
		StudyMaterial material = question.getStudyMaterial();
		String modelAnswer = question.getModelAnswer() != null && !question.getModelAnswer().isBlank()
				? question.getModelAnswer() : question.getExpectedAnswer();
		return new AnswerContext(
				question.getId(),
				question.getFormat(),
				question.getStatus(),
				question.getPrompt(),
				modelAnswer,
				QuestionRubric.parse(question.getRubricJson()),
				question.getSourceExcerpt(),
				question.getSourceTimestamp(),
				material == null ? null : material.getId(),
				material == null ? null : material.getTitle(),
				material == null || material.getSourceType() != StudyMaterialSourceType.YOUTUBE ? null : material.getExternalVideoId());
	}

	/**
	 * Grava a tentativa de uma questão aberta. Com {@code result}, a tentativa já sai corrigida e a revisão espaçada
	 * é atualizada pela classificação; sem ele, fica PENDENTE (fora das estatísticas e sem mexer na agenda).
	 */
	@Transactional
	public StudyQuestionAttempt recordDiscursiveAttempt(UUID blockId, UUID questionId, String answerText,
			DiscursiveEvaluationRules.Result result, EvaluationRigor rigor, UUID studySessionId,
			ActivityOriginType originType, Integer timeSpentSeconds, AssistanceLevel helpLevel) {
		StudyQuestion question = getOwned(blockId, questionId);
		if (question.getStatus() == StudyQuestionStatus.ARCHIVED) {
			throw new BusinessRuleException("Questões arquivadas não podem ser respondidas.");
		}
		if (answerText == null || answerText.isBlank()) {
			throw new InvalidInputException("Questões discursivas exigem uma resposta preenchida.");
		}
		String cleanAnswer = answerText.strip();
		String ownerId = currentUserResolver.currentUserId();
		int attemptNumber = (int) attemptRepository.countByOwnerIdAndQuestionId(ownerId, questionId) + 1;
		StudyMaterial material = question.getStudyMaterial();
		String topicTitle = question.getTopicId() == null || knowledgeTopicRepository == null ? null
				: knowledgeTopicRepository.findById(question.getTopicId()).map(KnowledgeTopic::getTitle).orElse(null);
		String modelAnswer = question.getModelAnswer() != null && !question.getModelAnswer().isBlank()
				? question.getModelAnswer() : question.getExpectedAnswer();

		StudyQuestionAttempt attempt = new StudyQuestionAttempt(ownerId, blockId, questionId, question.getTopicId(),
				studySessionId, material == null ? null : material.getId(), null, cleanAnswer, false,
				originType == null ? ActivityOriginType.BLOCK : originType, QuestionEvaluationStatus.PENDING_EVALUATION,
				0, timeSpentSeconds, attemptNumber, helpLevel == null ? AssistanceLevel.NONE : helpLevel, 0,
				PENDING_MESSAGE);
		attempt.populateSnapshots(question.getPrompt(), cleanAnswer, modelAnswer, question.getExplanation(),
				question.getStudyBlock().getTitle(), topicTitle, question.getDifficulty().name(),
				question.getFormat().name(), material == null ? null : material.getTitle(), question.getSourceExcerpt());
		if (result != null) {
			applyResult(attempt, result, rigor);
			question.scheduleNextReview(result.classification());
			repository.save(question);
		}
		return attemptRepository.save(attempt);
	}

	/** Tentativa pendente do usuário atual, para reavaliar quando a IA voltar. */
	@Transactional(readOnly = true)
	public StudyQuestionAttempt pendingAttempt(UUID blockId, UUID questionId, UUID attemptId) {
		getOwned(blockId, questionId);
		StudyQuestionAttempt attempt = attemptRepository.findById(attemptId)
				.filter(a -> questionId.equals(a.getQuestionId()))
				.filter(a -> a.getOwnerId().equals(currentUserResolver.currentUserId()))
				.orElseThrow(() -> new NotFoundException("Tentativa não encontrada."));
		if (attempt.getEvaluationStatus() != QuestionEvaluationStatus.PENDING_EVALUATION) {
			throw new BusinessRuleException("Esta tentativa já foi corrigida.");
		}
		return attempt;
	}

	/** Aplica a correção a uma tentativa pendente e atualiza a revisão espaçada. */
	@Transactional
	public StudyQuestionAttempt completePendingAttempt(UUID blockId, UUID questionId, UUID attemptId,
			DiscursiveEvaluationRules.Result result, EvaluationRigor rigor) {
		StudyQuestionAttempt attempt = pendingAttempt(blockId, questionId, attemptId);
		StudyQuestion question = getOwned(blockId, questionId);
		applyResult(attempt, result, rigor);
		question.scheduleNextReview(result.classification());
		repository.save(question);
		return attemptRepository.save(attempt);
	}

	/** Tentativas pendentes de uma questão (para o botão "Reavaliar"). */
	@Transactional(readOnly = true)
	public List<StudyQuestionAttempt> pendingAttempts(UUID blockId, UUID questionId) {
		getOwned(blockId, questionId);
		return attemptRepository.findAllByOwnerIdAndQuestionIdAndEvaluationStatusOrderByAnsweredAtDesc(
				currentUserResolver.currentUserId(), questionId, QuestionEvaluationStatus.PENDING_EVALUATION);
	}

	private void applyResult(StudyQuestionAttempt attempt, DiscursiveEvaluationRules.Result result, EvaluationRigor rigor) {
		attempt.applyEvaluation(result.classification(), result.overallScore(),
				EvaluationJson.criteria(result), EvaluationJson.details(result), rigor, EvaluationJson.summary(result));
	}

	static final String PENDING_MESSAGE = "Resposta registrada, mas ainda não corrigida: nenhum provedor de IA estava "
			+ "disponível. Ela fica pendente, fora das estatísticas, e pode ser reavaliada quando a IA voltar.";

	public record AnswerContext(
			UUID questionId,
			StudyQuestionFormat format,
			StudyQuestionStatus status,
			String prompt,
			String modelAnswer,
			QuestionRubric rubric,
			String sourceExcerpt,
			String sourceTimestamp,
			UUID sourceMaterialId,
			String sourceMaterialTitle,
			String sourceVideoId) {}

	/** Feedback de uma questão aberta: só existe depois da resposta (a resposta-modelo nunca sai antes). */
	public record DiscursiveFeedback(
			UUID attemptId,
			QuestionEvaluationStatus classification,
			Integer scorePercent,
			Map<String, Integer> criteriaScores,
			List<String> correctPoints,
			List<String> missingEssentialConcepts,
			List<String> missingTerms,
			List<String> errors,
			String improvementTip,
			String modelAnswer,
			String sourceExcerpt,
			String sourceTimestamp,
			UUID sourceMaterialId,
			String sourceMaterialTitle,
			String sourceVideoId,
			EvaluationRigor rigorLevel,
			boolean pending,
			String pendingMessage) {}

	/**
	 * Resultado de uma resposta. {@code correct} só é verdadeiro para CORRETA (PARCIAL não conta como acerto);
	 * {@code feedback} vem preenchido nas questões abertas.
	 */
	public record StudyQuestionAnswer(
			boolean correct,
			String explanation,
			QuestionEvaluationStatus classification,
			Integer scorePercent,
			DiscursiveFeedback feedback) {

		public StudyQuestionAnswer(boolean correct, String explanation) {
			this(correct, explanation, correct ? QuestionEvaluationStatus.CORRECT : QuestionEvaluationStatus.INCORRECT,
					correct ? 100 : 0, null);
		}

		static StudyQuestionAnswer pending(UUID attemptId, String message) {
			DiscursiveFeedback feedback = new DiscursiveFeedback(attemptId, QuestionEvaluationStatus.PENDING_EVALUATION,
					null, Map.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, null, null, null, null,
					null, true, message);
			return new StudyQuestionAnswer(false, message, QuestionEvaluationStatus.PENDING_EVALUATION, null, feedback);
		}
	}
	public record StudyQuestionStats(long attempts, long correct, long accuracyPercent) {}
	public record GlobalReviewSummary(
			long totalDue,
			long memorizedCount,
			long learningCount,
			long totalLearning,
			long needsReinforcementCount,
			long totalNeedsReinforcement,
			long totalActiveQuestions,
			java.util.Map<String, Long> dueByDifficulty) {}
}
