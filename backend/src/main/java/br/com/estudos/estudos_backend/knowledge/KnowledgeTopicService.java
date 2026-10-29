package br.com.estudos.estudos_backend.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.question.StudyQuestion;
import br.com.estudos.estudos_backend.question.StudyQuestionAttempt;
import br.com.estudos.estudos_backend.question.StudyQuestionAttemptRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionStatus;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;
import br.com.estudos.estudos_backend.studyblock.StudyBlockStatus;

@Service
public class KnowledgeTopicService {

	private final KnowledgeTopicRepository topicRepository;
	private final KnowledgeTopicQuestionRepository linkRepository;
	private final StudyBlockService blockService;
	private final StudyQuestionRepository questionRepository;
	private final StudyQuestionAttemptRepository attemptRepository;
	private final TopicLearningStateEvaluator evaluator = new TopicLearningStateEvaluator();

	public KnowledgeTopicService(KnowledgeTopicRepository topicRepository,
			KnowledgeTopicQuestionRepository linkRepository, StudyBlockService blockService,
			StudyQuestionRepository questionRepository, StudyQuestionAttemptRepository attemptRepository) {
		this.topicRepository = topicRepository;
		this.linkRepository = linkRepository;
		this.blockService = blockService;
		this.questionRepository = questionRepository;
		this.attemptRepository = attemptRepository;
	}

	@Transactional
	public KnowledgeTopic create(UUID blockId, String title, String description, UUID parentTopicId) {
		return create(blockId, title, description, parentTopicId, null);
	}

	@Transactional
	public KnowledgeTopic create(UUID blockId, String title, String description, UUID parentTopicId, UUID prerequisiteTopicId) {
		StudyBlock block = blockService.get(blockId);
		if (block.getStatus() == StudyBlockStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived study blocks cannot receive topics");
		}
		if (parentTopicId != null) {
			KnowledgeTopic parent = topicRepository.findById(parentTopicId)
					.filter(topic -> topic.getStudyBlock().getId().equals(blockId))
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parent topic"));
			if (parent.getStatus() == KnowledgeTopicStatus.ARCHIVED) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived topics cannot receive subtopics");
			}
			if (parent.isSubtopic()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subtopics cannot contain other subtopics");
			}
		}
		if (prerequisiteTopicId != null) {
			validatePrerequisiteTarget(blockId, null, prerequisiteTopicId);
		}
		return topicRepository.save(new KnowledgeTopic(block, parentTopicId, prerequisiteTopicId, title.trim(), normalize(description)));
	}

	@Transactional(readOnly = true)
	public List<TopicView> list(UUID blockId, boolean includeArchived) {
		blockService.get(blockId);
		List<KnowledgeTopic> topics = includeArchived
				? topicRepository.findAllByStudyBlockIdOrderByCreatedAtAsc(blockId)
				: topicRepository.findAllByStudyBlockIdAndStatusOrderByCreatedAtAsc(blockId, KnowledgeTopicStatus.ACTIVE);
		if (topics.isEmpty()) return List.of();

		Map<UUID, KnowledgeTopic> topicsById = new HashMap<>();
		for (KnowledgeTopic topic : topics) {
			topicsById.put(topic.getId(), topic);
		}

		Map<UUID, List<UUID>> questionIdsByTopic = new HashMap<>();
		for (KnowledgeTopicQuestion link : linkRepository.findAllByTopicIdIn(topics.stream().map(KnowledgeTopic::getId).toList())) {
			questionIdsByTopic.computeIfAbsent(link.getTopicId(), key -> new ArrayList<>()).add(link.getQuestionId());
		}
		List<StudyQuestionAttempt> attempts = attemptRepository.findAllByStudyBlockIdOrderByAnsweredAtDesc(blockId);

		Map<UUID, TopicLearningAssessment> assessmentsById = new HashMap<>();
		for (KnowledgeTopic topic : topics) {
			List<UUID> questionIds = List.copyOf(questionIdsByTopic.getOrDefault(topic.getId(), List.of()));
			Set<UUID> linked = new HashSet<>(questionIds);
			List<TopicLearningStateEvaluator.AttemptEvidence> evidence = attempts.stream()
					.filter(attempt -> linked.contains(attempt.getQuestionId()))
					.map(attempt -> new TopicLearningStateEvaluator.AttemptEvidence(attempt.getQuestionId(), attempt.isCorrect()))
					.toList();
			assessmentsById.put(topic.getId(), evaluator.evaluate(evidence, questionIds.size()));
		}

		return topics.stream().map(topic -> {
			List<UUID> questionIds = List.copyOf(questionIdsByTopic.getOrDefault(topic.getId(), List.of()));
			TopicLearningAssessment assessment = assessmentsById.get(topic.getId());
			String prerequisiteWarning = buildPrerequisiteWarning(topic, topicsById, assessmentsById);
			return new TopicView(topic, questionIds, assessment, prerequisiteWarning);
		}).toList();
	}

	@Transactional
	public KnowledgeTopic update(UUID blockId, UUID topicId, String title, String description) {
		KnowledgeTopic topic = getActiveOwned(blockId, topicId);
		topic.update(title.trim(), normalize(description));
		return topicRepository.save(topic);
	}

	@Transactional
	public KnowledgeTopic updatePrerequisite(UUID blockId, UUID topicId, UUID prerequisiteTopicId) {
		KnowledgeTopic topic = getActiveOwned(blockId, topicId);
		if (prerequisiteTopicId != null) {
			validatePrerequisiteTarget(blockId, topicId, prerequisiteTopicId);
		}
		topic.setPrerequisiteTopicId(prerequisiteTopicId);
		return topicRepository.save(topic);
	}

	@Transactional
	public void archive(UUID blockId, UUID topicId) {
		KnowledgeTopic topic = getActiveOwned(blockId, topicId);
		if (topicRepository.existsByParentTopicIdAndStatus(topicId, KnowledgeTopicStatus.ACTIVE)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archive the active subtopics first");
		}
		topic.archive();
		topicRepository.save(topic);
	}

	@Transactional
	public void linkQuestion(UUID blockId, UUID topicId, UUID questionId) {
		KnowledgeTopic topic = getActiveOwned(blockId, topicId);
		StudyQuestion question = questionRepository.findById(questionId)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Question does not belong to this study block"));
		if (question.getStatus() == StudyQuestionStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived questions cannot be linked");
		}
		KnowledgeTopicQuestion.Key key = new KnowledgeTopicQuestion.Key(topic.getId(), questionId);
		if (!linkRepository.existsById(key)) {
			linkRepository.save(new KnowledgeTopicQuestion(topic.getId(), questionId));
		}
	}

	@Transactional
	public void unlinkQuestion(UUID blockId, UUID topicId, UUID questionId) {
		KnowledgeTopic topic = getActiveOwned(blockId, topicId);
		KnowledgeTopicQuestion.Key key = new KnowledgeTopicQuestion.Key(topic.getId(), questionId);
		if (linkRepository.existsById(key)) {
			linkRepository.deleteById(key);
		}
	}

	private void validatePrerequisiteTarget(UUID blockId, UUID sourceTopicId, UUID prerequisiteTopicId) {
		if (sourceTopicId != null && sourceTopicId.equals(prerequisiteTopicId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A topic cannot be its own prerequisite");
		}
		KnowledgeTopic prereq = topicRepository.findById(prerequisiteTopicId)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid prerequisite topic"));
		if (prereq.getStatus() == KnowledgeTopicStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived topics cannot be prerequisites");
		}
		if (sourceTopicId != null) {
			Set<UUID> visited = new HashSet<>();
			visited.add(sourceTopicId);
			UUID cursor = prereq.getPrerequisiteTopicId();
			while (cursor != null) {
				if (!visited.add(cursor)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"Circular prerequisite dependency detected");
				}
				cursor = topicRepository.findById(cursor)
						.map(KnowledgeTopic::getPrerequisiteTopicId)
						.orElse(null);
			}
		}
	}

	private String buildPrerequisiteWarning(KnowledgeTopic topic,
			Map<UUID, KnowledgeTopic> topicsById,
			Map<UUID, TopicLearningAssessment> assessmentsById) {
		UUID prereqId = topic.getPrerequisiteTopicId();
		if (prereqId == null) return null;
		KnowledgeTopic prereq = topicsById.get(prereqId);
		if (prereq == null) return null;
		TopicLearningAssessment prereqAssessment = assessmentsById.get(prereqId);
		if (prereqAssessment == null) return null;
		if (prereqAssessment.state() != TopicLearningState.CONSISTENT) {
			return "Pré-requisito \"" + prereq.getTitle() + "\" ainda não está consolidado ("
					+ prereqAssessment.state().name() + ").";
		}
		return null;
	}

	private KnowledgeTopic getActiveOwned(UUID blockId, UUID topicId) {
		KnowledgeTopic topic = topicRepository.findById(topicId)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));
		if (topic.getStatus() == KnowledgeTopicStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived topics cannot be changed");
		}
		return topic;
	}

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public record TopicView(
			KnowledgeTopic topic,
			List<UUID> questionIds,
			TopicLearningAssessment assessment,
			String prerequisiteWarning) {
		public TopicView(KnowledgeTopic topic, List<UUID> questionIds, TopicLearningAssessment assessment) {
			this(topic, questionIds, assessment, null);
		}
	}
}
