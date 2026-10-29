package br.com.estudos.estudos_backend.learning;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.exercise.ExerciseEvaluationStatus;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseAttempt;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseAttemptRepository;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopic;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicQuestion;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicQuestionRepository;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicRepository;
import br.com.estudos.estudos_backend.question.QuestionEvaluationStatus;
import br.com.estudos.estudos_backend.question.StudyQuestionAttempt;
import br.com.estudos.estudos_backend.question.StudyQuestionAttemptRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionDifficulty;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;

/**
 * Fonte central e consistente para consulta do histórico de tentativas
 * (teóricas e práticas) e indicadores básicos de desempenho, evitando
 * divergência de regras entre telas de bloco, revisão espaçada e painel global.
 */
@Service
public class LearningHistoryService {

	private final StudyBlockService studyBlockService;
	private final StudyQuestionAttemptRepository questionAttemptRepository;
	private final PracticalExerciseAttemptRepository exerciseAttemptRepository;
	private final KnowledgeTopicRepository topicRepository;
	private final KnowledgeTopicQuestionRepository topicQuestionRepository;
	private final CurrentUserResolver currentUserResolver;
	private final LearningCriteriaProperties criteriaProperties;

	public LearningHistoryService(
			StudyBlockService studyBlockService,
			StudyQuestionAttemptRepository questionAttemptRepository,
			PracticalExerciseAttemptRepository exerciseAttemptRepository,
			KnowledgeTopicRepository topicRepository,
			KnowledgeTopicQuestionRepository topicQuestionRepository,
			CurrentUserResolver currentUserResolver,
			LearningCriteriaProperties criteriaProperties) {
		this.studyBlockService = studyBlockService;
		this.questionAttemptRepository = questionAttemptRepository;
		this.exerciseAttemptRepository = exerciseAttemptRepository;
		this.topicRepository = topicRepository;
		this.topicQuestionRepository = topicQuestionRepository;
		this.currentUserResolver = currentUserResolver;
		this.criteriaProperties = criteriaProperties;
	}

	@Transactional(readOnly = true)
	public List<LearningAttemptRecord> listBlockHistory(UUID studyBlockId) {
		StudyBlock block = studyBlockService.get(studyBlockId);
		Map<UUID, UUID> questionTopicFallback = loadQuestionTopicFallback(List.of(block.getId()));

		List<LearningAttemptRecord> records = new ArrayList<>();
		for (StudyQuestionAttempt qAttempt : questionAttemptRepository.findAllByStudyBlockIdOrderByAnsweredAtDesc(studyBlockId)) {
			records.add(fromQuestionAttempt(qAttempt, questionTopicFallback));
		}
		for (PracticalExerciseAttempt eAttempt : exerciseAttemptRepository.findAllByStudyBlockIdOrderByCreatedAtDesc(studyBlockId)) {
			records.add(fromExerciseAttempt(eAttempt));
		}
		records.sort(Comparator.comparing(LearningAttemptRecord::timestamp).reversed());
		return List.copyOf(records);
	}

	@Transactional(readOnly = true)
	public List<LearningAttemptRecord> listUserHistory() {
		String ownerId = currentUserResolver.currentUserId();
		List<StudyBlock> ownedBlocks = studyBlockService.list(true);
		if (ownedBlocks.isEmpty()) {
			return List.of();
		}
		List<UUID> blockIds = ownedBlocks.stream().map(StudyBlock::getId).toList();
		Map<UUID, UUID> questionTopicFallback = loadQuestionTopicFallback(blockIds);

		List<LearningAttemptRecord> records = new ArrayList<>();
		for (StudyQuestionAttempt qAttempt : questionAttemptRepository.findAllByStudyBlockIdInOrderByAnsweredAtDesc(blockIds)) {
			if (ownerId.equals(qAttempt.getOwnerId())) {
				records.add(fromQuestionAttempt(qAttempt, questionTopicFallback));
			}
		}
		for (PracticalExerciseAttempt eAttempt : exerciseAttemptRepository.findAllByStudyBlockIdInOrderByCreatedAtDesc(blockIds)) {
			if (ownerId.equals(eAttempt.getOwnerId())) {
				records.add(fromExerciseAttempt(eAttempt));
			}
		}
		records.sort(Comparator.comparing(LearningAttemptRecord::timestamp).reversed());
		return List.copyOf(records);
	}

	@Transactional(readOnly = true)
	public ConsolidatedIndicators calculateBlockIndicators(UUID studyBlockId) {
		return aggregateIndicators(listBlockHistory(studyBlockId));
	}

	@Transactional(readOnly = true)
	public ConsolidatedIndicators calculateGlobalIndicators() {
		return aggregateIndicators(listUserHistory());
	}

	public ConsolidatedIndicators aggregateIndicators(List<LearningAttemptRecord> attemptsNewestFirst) {
		List<LearningAttemptRecord> theoreticalEvaluable = attemptsNewestFirst.stream()
				.filter(a -> a.modality() == LearningModality.THEORETICAL && a.evaluable())
				.toList();
		long theoreticalTotal = theoreticalEvaluable.size();
		long theoreticalCorrect = theoreticalEvaluable.stream().filter(LearningAttemptRecord::passedOrCorrect).count();
		Integer theoreticalAccuracy = theoreticalTotal == 0
				? null
				: (int) Math.round(theoreticalCorrect * 100.0 / theoreticalTotal);

		int recentWindow = Math.max(1, criteriaProperties.getRecentWindowSize());
		List<LearningAttemptRecord> recentTheoretical = theoreticalEvaluable.subList(
				0, Math.min(recentWindow, theoreticalEvaluable.size()));
		Integer recentTheoreticalAccuracy = recentTheoretical.isEmpty()
				? null
				: (int) Math.round(recentTheoretical.stream().filter(LearningAttemptRecord::passedOrCorrect).count()
						* 100.0 / recentTheoretical.size());

		List<LearningAttemptRecord> practicalEvaluable = attemptsNewestFirst.stream()
				.filter(a -> a.modality() == LearningModality.PRACTICAL && a.evaluable())
				.toList();
		long practicalAttempts = practicalEvaluable.size();
		long distinctPracticalExercises = practicalEvaluable.stream()
				.map(LearningAttemptRecord::activityId)
				.distinct()
				.count();
		long practicalCompleted = practicalEvaluable.stream()
				.filter(LearningAttemptRecord::passedOrCorrect)
				.count();
		long practicalIndependent = practicalEvaluable.stream()
				.filter(a -> a.passedOrCorrect()
						&& (a.assistanceLevel() == AssistanceLevel.INDEPENDENT || a.assistanceLevel() == AssistanceLevel.NONE)
						&& !a.solutionRevealed())
				.count();

		return new ConsolidatedIndicators(
				theoreticalTotal,
				theoreticalCorrect,
				theoreticalAccuracy,
				recentTheoreticalAccuracy,
				practicalAttempts,
				distinctPracticalExercises,
				practicalCompleted,
				practicalIndependent);
	}

	@Transactional(readOnly = true)
	public GlobalPerformanceDashboardResponse getGlobalPerformance(
			Integer days, UUID filterBlockId, UUID filterTopicId, StudyQuestionDifficulty filterDifficulty) {
		String ownerId = currentUserResolver.currentUserId();
		List<StudyBlock> ownedBlocks = studyBlockService.list(true);
		if (ownedBlocks.isEmpty()) {
			return new GlobalPerformanceDashboardResponse(
					new GlobalPerformanceDashboardResponse.OverallPerformanceSummary(0, 0, 0, 0, 0, null, null),
					List.of(),
					List.of(),
					List.of());
		}

		Map<UUID, StudyBlock> blockMap = new HashMap<>();
		for (StudyBlock b : ownedBlocks) {
			blockMap.put(b.getId(), b);
		}

		List<UUID> blockIds = ownedBlocks.stream().map(StudyBlock::getId).toList();
		Map<UUID, UUID> questionTopicFallback = loadQuestionTopicFallback(blockIds);

		List<StudyQuestionAttempt> allAttempts = questionAttemptRepository.findAllByOwnerIdOrderByAnsweredAtDesc(ownerId);
		OffsetDateTime cutoff = (days != null && days > 0) ? OffsetDateTime.now().minusDays(days) : null;

		List<StudyQuestionAttempt> filtered = allAttempts.stream()
				.filter(a -> blockMap.containsKey(a.getStudyBlockId()))
				.filter(a -> cutoff == null || !a.getAnsweredAt().isBefore(cutoff))
				.filter(a -> filterBlockId == null || a.getStudyBlockId().equals(filterBlockId))
				.filter(a -> {
					if (filterTopicId == null) return true;
					UUID resolvedTopic = a.getTopicId() != null ? a.getTopicId() : questionTopicFallback.get(a.getQuestionId());
					return filterTopicId.equals(resolvedTopic);
				})
				.filter(a -> filterDifficulty == null || (a.getDifficultySnapshot() != null
						&& a.getDifficultySnapshot().equalsIgnoreCase(filterDifficulty.name())))
				.toList();

		// Overall Summary
		long totalAttempts = filtered.size();
		long uniqueQuestionsAnswered = filtered.stream()
				.map(a -> a.getQuestionId() != null ? a.getQuestionId().toString() : a.getPromptSnapshot())
				.filter(Objects::nonNull)
				.distinct()
				.count();

		List<StudyQuestionAttempt> evaluable = filtered.stream()
				.filter(a -> a.getEvaluationStatus() != QuestionEvaluationStatus.UNGRADABLE)
				.toList();
		long evaluatedAttempts = evaluable.size();
		long correctAttempts = evaluable.stream().filter(StudyQuestionAttempt::isCorrect).count();
		long incorrectAttempts = evaluatedAttempts - correctAttempts;
		Integer accuracyPercent = evaluatedAttempts == 0 ? null : (int) Math.round(correctAttempts * 100.0 / evaluatedAttempts);
		Integer errorPercent = evaluatedAttempts == 0 ? null : (int) Math.round(incorrectAttempts * 100.0 / evaluatedAttempts);

		GlobalPerformanceDashboardResponse.OverallPerformanceSummary overall =
				new GlobalPerformanceDashboardResponse.OverallPerformanceSummary(
						totalAttempts,
						uniqueQuestionsAnswered,
						evaluatedAttempts,
						correctAttempts,
						incorrectAttempts,
						accuracyPercent,
						errorPercent);

		// Performance by Block and Topic
		List<GlobalPerformanceDashboardResponse.BlockPerformanceSummary> blockSummaries = new ArrayList<>();
		List<StudyBlock> targetBlocks = filterBlockId != null
				? (blockMap.containsKey(filterBlockId) ? List.of(blockMap.get(filterBlockId)) : List.of())
				: ownedBlocks;

		for (StudyBlock block : targetBlocks) {
			List<StudyQuestionAttempt> blockAttempts = filtered.stream()
					.filter(a -> a.getStudyBlockId().equals(block.getId()))
					.toList();

			long bTotal = blockAttempts.size();
			long bUnique = blockAttempts.stream()
					.map(a -> a.getQuestionId() != null ? a.getQuestionId().toString() : a.getPromptSnapshot())
					.filter(Objects::nonNull).distinct().count();
			List<StudyQuestionAttempt> bEval = blockAttempts.stream()
					.filter(a -> a.getEvaluationStatus() != QuestionEvaluationStatus.UNGRADABLE).toList();
			long bEvalCount = bEval.size();
			long bCorrect = bEval.stream().filter(StudyQuestionAttempt::isCorrect).count();
			long bIncorrect = bEvalCount - bCorrect;
			Integer bAcc = bEvalCount == 0 ? null : (int) Math.round(bCorrect * 100.0 / bEvalCount);
			Integer bErr = bEvalCount == 0 ? null : (int) Math.round(bIncorrect * 100.0 / bEvalCount);

			List<KnowledgeTopic> topics = topicRepository.findAllByStudyBlockIdOrderByCreatedAtAsc(block.getId());
			List<GlobalPerformanceDashboardResponse.TopicPerformanceSummary> topicSummaries = new ArrayList<>();

			for (KnowledgeTopic topic : topics) {
				if (filterTopicId != null && !topic.getId().equals(filterTopicId)) {
					continue;
				}
				List<StudyQuestionAttempt> tAttempts = blockAttempts.stream()
						.filter(a -> {
							UUID rTopic = a.getTopicId() != null ? a.getTopicId() : questionTopicFallback.get(a.getQuestionId());
							return topic.getId().equals(rTopic);
						})
						.toList();

				long tTotal = tAttempts.size();
				long tUnique = tAttempts.stream()
						.map(a -> a.getQuestionId() != null ? a.getQuestionId().toString() : a.getPromptSnapshot())
						.filter(Objects::nonNull).distinct().count();
				List<StudyQuestionAttempt> tEval = tAttempts.stream()
						.filter(a -> a.getEvaluationStatus() != QuestionEvaluationStatus.UNGRADABLE).toList();
				long tEvalCount = tEval.size();
				long tCorrect = tEval.stream().filter(StudyQuestionAttempt::isCorrect).count();
				long tIncorrect = tEvalCount - tCorrect;
				Integer tAcc = tEvalCount == 0 ? null : (int) Math.round(tCorrect * 100.0 / tEvalCount);
				Integer tErr = tEvalCount == 0 ? null : (int) Math.round(tIncorrect * 100.0 / tEvalCount);

				topicSummaries.add(new GlobalPerformanceDashboardResponse.TopicPerformanceSummary(
						topic.getId(),
						topic.getTitle(),
						tTotal,
						tUnique,
						tEvalCount,
						tCorrect,
						tIncorrect,
						tAcc,
						tErr));
			}

			blockSummaries.add(new GlobalPerformanceDashboardResponse.BlockPerformanceSummary(
					block.getId(),
					block.getTitle(),
					bTotal,
					bUnique,
					bEvalCount,
					bCorrect,
					bIncorrect,
					bAcc,
					bErr,
					topicSummaries));
		}

		// Timeline aggregation (chronological ascending)
		Map<String, List<StudyQuestionAttempt>> byDate = new TreeMap<>();
		for (StudyQuestionAttempt a : filtered) {
			String dateKey = a.getAnsweredAt().toLocalDate().toString();
			byDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(a);
		}

		List<GlobalPerformanceDashboardResponse.TimelinePoint> timeline = new ArrayList<>();
		for (Map.Entry<String, List<StudyQuestionAttempt>> entry : byDate.entrySet()) {
			List<StudyQuestionAttempt> dAttempts = entry.getValue();
			long dTotal = dAttempts.size();
			List<StudyQuestionAttempt> dEval = dAttempts.stream()
					.filter(a -> a.getEvaluationStatus() != QuestionEvaluationStatus.UNGRADABLE).toList();
			long dCorrect = dEval.stream().filter(StudyQuestionAttempt::isCorrect).count();
			long dIncorrect = dEval.size() - dCorrect;
			Integer dAcc = dEval.isEmpty() ? null : (int) Math.round(dCorrect * 100.0 / dEval.size());
			timeline.add(new GlobalPerformanceDashboardResponse.TimelinePoint(
					entry.getKey(), dTotal, dCorrect, dIncorrect, dAcc));
		}

		// Detailed Attempts (newest first)
		List<GlobalPerformanceDashboardResponse.HistoricalAttemptDetail> recentAttempts = filtered.stream()
				.map(a -> {
					UUID resolvedTopic = a.getTopicId() != null ? a.getTopicId() : questionTopicFallback.get(a.getQuestionId());
					String bTitle = a.getStudyBlockTitleSnapshot() != null
							? a.getStudyBlockTitleSnapshot()
							: (blockMap.containsKey(a.getStudyBlockId()) ? blockMap.get(a.getStudyBlockId()).getTitle() : "Bloco");
					String diff = a.getDifficultySnapshot() != null ? a.getDifficultySnapshot() : "INTERMEDIATE";
					String fmt = a.getQuestionFormatSnapshot() != null
							? a.getQuestionFormatSnapshot()
							: (a.getDiscursiveAnswer() != null ? "DISCURSIVE" : "MULTIPLE_CHOICE");
					String uAns = a.getUserAnswerText() != null ? a.getUserAnswerText() : a.getDiscursiveAnswer();
					String evalStatus = a.getEvaluationStatus() != null ? a.getEvaluationStatus().name() : (a.isCorrect() ? "CORRECT" : "INCORRECT");

					return new GlobalPerformanceDashboardResponse.HistoricalAttemptDetail(
							a.getId(),
							a.getAnsweredAt(),
							a.getStudyBlockId(),
							bTitle,
							resolvedTopic,
							a.getTopicTitleSnapshot(),
							a.getQuestionId(),
							diff,
							fmt,
							a.getPromptSnapshot(),
							uAns,
							a.getExpectedAnswerSnapshot(),
							a.getExplanationSnapshot(),
							a.isCorrect(),
							evalStatus,
							a.getScorePercent(),
							a.getSourceTitleSnapshot(),
							a.getSourceExcerptSnapshot());
				})
				.toList();

		return new GlobalPerformanceDashboardResponse(overall, blockSummaries, timeline, recentAttempts);
	}

	private Map<UUID, UUID> loadQuestionTopicFallback(List<UUID> blockIds) {
		Map<UUID, UUID> fallback = new HashMap<>();
		for (UUID blockId : blockIds) {
			List<KnowledgeTopic> topics = topicRepository.findAllByStudyBlockIdOrderByCreatedAtAsc(blockId);
			if (!topics.isEmpty()) {
				List<UUID> topicIds = topics.stream().map(KnowledgeTopic::getId).toList();
				for (KnowledgeTopicQuestion link : topicQuestionRepository.findAllByTopicIdIn(topicIds)) {
					fallback.putIfAbsent(link.getQuestionId(), link.getTopicId());
				}
			}
		}
		return fallback;
	}

	private LearningAttemptRecord fromQuestionAttempt(
			StudyQuestionAttempt attempt, Map<UUID, UUID> questionTopicFallback) {
		UUID resolvedTopicId = attempt.getTopicId() != null
				? attempt.getTopicId()
				: questionTopicFallback.get(attempt.getQuestionId());
		QuestionEvaluationStatus status = attempt.getEvaluationStatus() == null
				? (attempt.isCorrect() ? QuestionEvaluationStatus.CORRECT : QuestionEvaluationStatus.INCORRECT)
				: attempt.getEvaluationStatus();
		boolean evaluable = status != QuestionEvaluationStatus.UNGRADABLE;
		return new LearningAttemptRecord(
				attempt.getId(),
				attempt.getOwnerId(),
				attempt.getAnsweredAt(),
				attempt.getStudyBlockId(),
				resolvedTopicId,
				attempt.getStudySessionId(),
				LearningModality.THEORETICAL,
				attempt.getQuestionId(),
				attempt.getSourceMaterialId(),
				attempt.getOriginType() == null ? ActivityOriginType.BLOCK : attempt.getOriginType(),
				evaluable,
				attempt.isCorrect(),
				attempt.getScorePercent(),
				attempt.getTimeSpentSeconds(),
				attempt.getAttemptNumber(),
				attempt.getHelpLevel() == null ? AssistanceLevel.NONE : attempt.getHelpLevel(),
				attempt.getHintsUsedCount(),
				attempt.getHelpLevel() == AssistanceLevel.SOLUTION_REVEALED,
				0,
				0,
				attempt.isCorrect(),
				status.name());
	}

	private LearningAttemptRecord fromExerciseAttempt(PracticalExerciseAttempt attempt) {
		ExerciseEvaluationStatus status = attempt.getEvaluationStatus() == null
				? ExerciseEvaluationStatus.SELF_RATED
				: attempt.getEvaluationStatus();
		boolean evaluable = status != ExerciseEvaluationStatus.UNGRADABLE;
		boolean passed = status == ExerciseEvaluationStatus.PASSED
				|| (status == ExerciseEvaluationStatus.SELF_RATED && attempt.isMandatoryTestsPassed());
		UUID blockId = attempt.getStudyBlockId() != null
				? attempt.getStudyBlockId()
				: attempt.getExercise().getStudyBlock().getId();
		UUID topicId = attempt.getTopicId() != null
				? attempt.getTopicId()
				: attempt.getExercise().getTopicId();
		return new LearningAttemptRecord(
				attempt.getId(),
				attempt.getOwnerId(),
				attempt.getCreatedAt(),
				blockId,
				topicId,
				attempt.getStudySessionId(),
				LearningModality.PRACTICAL,
				attempt.getExercise().getId(),
				attempt.getSourceMaterialId(),
				attempt.getOriginType() == null ? ActivityOriginType.BLOCK : attempt.getOriginType(),
				evaluable,
				passed,
				attempt.getScorePercent(),
				attempt.getTimeSpentSeconds(),
				attempt.getAttemptNumber(),
				attempt.getAssistanceLevel() == null ? AssistanceLevel.INDEPENDENT : attempt.getAssistanceLevel(),
				attempt.getHintsUsedCount(),
				attempt.isSolutionRevealed(),
				attempt.getTestsTotal(),
				attempt.getTestsPassed(),
				attempt.isMandatoryTestsPassed(),
				status.name());
	}

	public record ConsolidatedIndicators(
			long theoreticalEvaluatedAttempts,
			long theoreticalCorrectAttempts,
			Integer theoreticalAccuracyPercent,
			Integer recentTheoreticalAccuracyPercent,
			long practicalEvaluatedAttempts,
			long distinctPracticalExercises,
			long practicalCompletedAttempts,
			long practicalIndependentAttempts) {
	}
}
