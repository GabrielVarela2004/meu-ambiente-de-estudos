package br.com.estudos.estudos_backend.knowledge;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.common.exception.InvalidInputException;
import br.com.estudos.estudos_backend.common.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialProcessingStatus;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
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
	private final StudyMaterialRepository materialRepository;
	private final SourceTopicExtractor sourceTopicExtractor;
	private final TopicLearningStateEvaluator evaluator = new TopicLearningStateEvaluator();

	public KnowledgeTopicService(KnowledgeTopicRepository topicRepository,
			KnowledgeTopicQuestionRepository linkRepository, StudyBlockService blockService,
			StudyQuestionRepository questionRepository, StudyQuestionAttemptRepository attemptRepository) {
		this(topicRepository, linkRepository, blockService, questionRepository, attemptRepository, null, null);
	}

	@Autowired
	public KnowledgeTopicService(KnowledgeTopicRepository topicRepository,
			KnowledgeTopicQuestionRepository linkRepository, StudyBlockService blockService,
			StudyQuestionRepository questionRepository, StudyQuestionAttemptRepository attemptRepository,
			StudyMaterialRepository materialRepository, SourceTopicExtractor sourceTopicExtractor) {
		this.topicRepository = topicRepository;
		this.linkRepository = linkRepository;
		this.blockService = blockService;
		this.questionRepository = questionRepository;
		this.attemptRepository = attemptRepository;
		this.materialRepository = materialRepository;
		this.sourceTopicExtractor = sourceTopicExtractor;
	}

	@Transactional
	public KnowledgeTopic create(UUID blockId, String title, String description, UUID parentTopicId) {
		return create(blockId, title, description, parentTopicId, null);
	}

	@Transactional
	public KnowledgeTopic create(UUID blockId, String title, String description, UUID parentTopicId, UUID prerequisiteTopicId) {
		StudyBlock block = blockService.get(blockId);
		if (block.getStatus() == StudyBlockStatus.ARCHIVED) {
			throw new BusinessRuleException("Blocos de estudo arquivados não podem receber tópicos.");
		}
		if (parentTopicId != null) {
			KnowledgeTopic parent = topicRepository.findById(parentTopicId)
					.filter(topic -> topic.getStudyBlock().getId().equals(blockId))
					.orElseThrow(() -> new InvalidInputException("Tópico pai inválido."));
			if (parent.getStatus() == KnowledgeTopicStatus.ARCHIVED) {
				throw new BusinessRuleException("Tópicos arquivados não podem receber subtópicos.");
			}
			if (parent.isSubtopic()) {
				throw new InvalidInputException("Subtópicos não podem conter outros subtópicos.");
			}
		}
		if (prerequisiteTopicId != null) {
			validatePrerequisiteTarget(blockId, null, prerequisiteTopicId);
		}
		return topicRepository.save(new KnowledgeTopic(block, parentTopicId, prerequisiteTopicId, title.trim(), normalize(description)));
	}

	@Transactional
	public List<TopicView> extractFromSources(UUID blockId, boolean requireSources) {
		StudyBlock block = blockService.get(blockId);
		if (block.getStatus() == StudyBlockStatus.ARCHIVED) {
			throw new BusinessRuleException("Não é possível extrair tópicos de um bloco de estudo arquivado.");
		}
		if (materialRepository == null || sourceTopicExtractor == null) {
			return list(blockId, false);
		}

		List<StudyMaterial> activeMaterials = materialRepository
				.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyMaterialStatus.ACTIVE)
				.stream()
				.filter(m -> m.getProcessingStatus() == null
						|| m.getProcessingStatus() == StudyMaterialProcessingStatus.COMPLETED)
				.toList();

		Set<UUID> activeMaterialIds = new HashSet<>();
		for (StudyMaterial material : activeMaterials) {
			if (material.getId() != null) {
				activeMaterialIds.add(material.getId());
			}
		}

		// Arquiva subtópicos e tópicos extraídos por IA (não validados manualmente) cujas fontes foram arquivadas/removidas
		List<KnowledgeTopic> allBlockTopics = topicRepository.findAllByStudyBlockIdOrderByCreatedAtAsc(blockId);
		for (KnowledgeTopic topic : allBlockTopics) {
			if (topic.getStatus() == KnowledgeTopicStatus.ACTIVE
					&& topic.isSubtopic()
					&& topic.getOrigin() == KnowledgeTopicOrigin.AI_EXTRACTED
					&& !topic.isUserValidated()
					&& topic.getSourceMaterialId() != null
					&& !activeMaterialIds.contains(topic.getSourceMaterialId())) {
				topic.archive();
				topicRepository.save(topic);
			}
		}
		for (KnowledgeTopic topic : allBlockTopics) {
			if (topic.getStatus() == KnowledgeTopicStatus.ACTIVE
					&& !topic.isSubtopic()
					&& topic.getOrigin() == KnowledgeTopicOrigin.AI_EXTRACTED
					&& !topic.isUserValidated()
					&& topic.getSourceMaterialId() != null
					&& !activeMaterialIds.contains(topic.getSourceMaterialId())
					&& !topicRepository.existsByParentTopicIdAndStatus(topic.getId(), KnowledgeTopicStatus.ACTIVE)) {
				topic.archive();
				topicRepository.save(topic);
			}
		}

		// A extração usa só as fontes ativas no chat; a limpeza acima considera todas as ativas, para que
		// desligar uma fonte no chat não arquive os tópicos dela.
		List<StudyMaterial> chatMaterials = activeMaterials.stream().filter(StudyMaterial::isEnabledForChat).toList();
		if (chatMaterials.isEmpty()) {
			if (requireSources) {
				throw new InvalidInputException(activeMaterials.isEmpty()
						? "Adicione ao menos uma fonte concluída ao bloco antes de extrair assuntos e tópicos."
						: "Ative no chat ao menos uma fonte do bloco antes de extrair assuntos e tópicos.");
			}
			return list(blockId, false);
		}

		List<SourceTopicExtractor.ExtractedTopic> extractedTopics = sourceTopicExtractor.extractFromMaterials(block, chatMaterials);
		OffsetDateTime now = OffsetDateTime.now();

		Map<String, KnowledgeTopic> existingMainByNormTitle = new LinkedHashMap<>();
		Map<String, KnowledgeTopic> existingSubByParentAndNormTitle = new LinkedHashMap<>();
		for (KnowledgeTopic existing : topicRepository.findAllByStudyBlockIdOrderByCreatedAtAsc(blockId)) {
			String key = SourceTopicExtractor.normalizeKey(existing.getTitle());
			if (!existing.isSubtopic()) {
				existingMainByNormTitle.putIfAbsent(key, existing);
			} else {
				existingSubByParentAndNormTitle.putIfAbsent(existing.getParentTopicId() + "::" + key, existing);
			}
		}

		for (SourceTopicExtractor.ExtractedTopic extractedMain : extractedTopics) {
			String mainKey = SourceTopicExtractor.normalizeKey(extractedMain.title());
			if (mainKey.isBlank()) {
				continue;
			}
			KnowledgeTopic mainTopic = existingMainByNormTitle.get(mainKey);
			if (mainTopic == null) {
				mainTopic = topicRepository.save(new KnowledgeTopic(
						block,
						null,
						null,
						extractedMain.title(),
						normalize(extractedMain.description()),
						extractedMain.sourceMaterialId(),
						normalize(extractedMain.supportingExcerpt()),
						KnowledgeTopicOrigin.AI_EXTRACTED,
						false,
						now));
				existingMainByNormTitle.put(mainKey, mainTopic);
			} else {
				mainTopic.updateExtractedEvidence(
						normalize(extractedMain.description()),
						extractedMain.sourceMaterialId(),
						normalize(extractedMain.supportingExcerpt()),
						now);
				mainTopic = topicRepository.save(mainTopic);
			}

			for (SourceTopicExtractor.ExtractedSubtopic extractedSub : extractedMain.subtopics()) {
				String subKey = SourceTopicExtractor.normalizeKey(extractedSub.title());
				if (subKey.isBlank() || subKey.equals(mainKey)) {
					continue;
				}
				String compositeKey = mainTopic.getId() + "::" + subKey;
				KnowledgeTopic subTopic = existingSubByParentAndNormTitle.get(compositeKey);
				if (subTopic == null) {
					subTopic = topicRepository.save(new KnowledgeTopic(
							block,
							mainTopic.getId(),
							null,
							extractedSub.title(),
							normalize(extractedSub.description()),
							extractedSub.sourceMaterialId(),
							normalize(extractedSub.supportingExcerpt()),
							KnowledgeTopicOrigin.AI_EXTRACTED,
							false,
							now));
					existingSubByParentAndNormTitle.put(compositeKey, subTopic);
				} else {
					subTopic.updateExtractedEvidence(
							normalize(extractedSub.description()),
							extractedSub.sourceMaterialId(),
							normalize(extractedSub.supportingExcerpt()),
							now);
					topicRepository.save(subTopic);
				}
			}
		}

		return list(blockId, false);
	}

	@Transactional
	public TopicView validateTopic(UUID blockId, UUID topicId) {
		KnowledgeTopic topic = getActiveOwned(blockId, topicId);
		topic.markUserValidated();
		topicRepository.save(topic);
		return list(blockId, false).stream()
				.filter(view -> view.topic().getId().equals(topicId))
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Tópico não encontrado."));
	}

	@Transactional(readOnly = true)
	public List<TopicView> list(UUID blockId, boolean includeArchived) {
		blockService.get(blockId);
		List<KnowledgeTopic> topics = includeArchived
				? topicRepository.findAllByStudyBlockIdOrderByCreatedAtAsc(blockId)
				: topicRepository.findAllByStudyBlockIdAndStatusOrderByCreatedAtAsc(blockId, KnowledgeTopicStatus.ACTIVE);
		if (topics.isEmpty()) return List.of();

		Map<UUID, String> materialTitlesById = new HashMap<>();
		if (materialRepository != null) {
			for (StudyMaterial mat : materialRepository.findAllByStudyBlockIdOrderByUpdatedAtDesc(blockId)) {
				materialTitlesById.put(mat.getId(), mat.getTitle());
			}
		}

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
			String sourceMaterialTitle = topic.getSourceMaterialId() == null
					? null
					: materialTitlesById.get(topic.getSourceMaterialId());
			return new TopicView(topic, questionIds, assessment, prerequisiteWarning, sourceMaterialTitle);
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
			throw new BusinessRuleException("Arquive primeiro os subtópicos ativos.");
		}
		topic.archive();
		topicRepository.save(topic);
	}

	@Transactional
	public void linkQuestion(UUID blockId, UUID topicId, UUID questionId) {
		KnowledgeTopic topic = getActiveOwned(blockId, topicId);
		StudyQuestion question = questionRepository.findById(questionId)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new InvalidInputException(
						"A questão não pertence a este bloco de estudo."));
		if (question.getStatus() == StudyQuestionStatus.ARCHIVED) {
			throw new BusinessRuleException("Questões arquivadas não podem ser vinculadas.");
		}
		KnowledgeTopicQuestion.Key key = new KnowledgeTopicQuestion.Key(topic.getId(), questionId);
		if (!linkRepository.existsById(key)) {
			linkRepository.save(new KnowledgeTopicQuestion(topic.getId(), questionId));
		}
		if (question.getTopicId() == null) {
			question.setTopicId(topic.getId());
			questionRepository.save(question);
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
			throw new InvalidInputException("Um tópico não pode ser pré-requisito de si mesmo.");
		}
		KnowledgeTopic prereq = topicRepository.findById(prerequisiteTopicId)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new InvalidInputException("Tópico de pré-requisito inválido."));
		if (prereq.getStatus() == KnowledgeTopicStatus.ARCHIVED) {
			throw new BusinessRuleException("Tópicos arquivados não podem ser pré-requisitos.");
		}
		if (sourceTopicId != null) {
			Set<UUID> visited = new HashSet<>();
			visited.add(sourceTopicId);
			UUID cursor = prereq.getPrerequisiteTopicId();
			while (cursor != null) {
				if (!visited.add(cursor)) {
					throw new InvalidInputException(
							"Dependência circular de pré-requisitos detectada.");
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

	public KnowledgeTopic getActiveOwned(UUID blockId, UUID topicId) {
		blockService.get(blockId);
		KnowledgeTopic topic = topicRepository.findById(topicId)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new NotFoundException("Tópico não encontrado."));
		if (topic.getStatus() == KnowledgeTopicStatus.ARCHIVED) {
			throw new BusinessRuleException("Tópicos arquivados não podem ser modificados.");
		}
		return topic;
	}

	private String normalize(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

	public record TopicView(
			KnowledgeTopic topic,
			List<UUID> questionIds,
			TopicLearningAssessment assessment,
			String prerequisiteWarning,
			String sourceMaterialTitle) {

		public TopicView(KnowledgeTopic topic, List<UUID> questionIds,
				TopicLearningAssessment assessment, String prerequisiteWarning) {
			this(topic, questionIds, assessment, prerequisiteWarning, null);
		}
	}
}
