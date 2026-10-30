package br.com.estudos.estudos_backend.knowledge;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.common.exception.InvalidInputException;
import br.com.estudos.estudos_backend.common.exception.NotFoundException;
import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialSourceType;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;

/**
 * Validação manual dos tópicos sugeridos (aba Mapeamento de Fontes): aprovar, excluir, comentar, revisar com IA
 * numa nova rodada e oficializar. Só o que o usuário aprova vira {@link KnowledgeTopicStatus#ACTIVE}.
 */
@Service
public class TopicReviewService {

	private static final int MEMORY_SIZE = 20;

	private final KnowledgeTopicRepository topicRepository;
	private final TopicReviewDecisionRepository decisionRepository;
	private final StudyBlockService blockService;
	private final StudyMaterialRepository materialRepository;
	private final SourceTopicExtractor extractor;
	private final CurrentUserResolver currentUserResolver;

	public TopicReviewService(KnowledgeTopicRepository topicRepository, TopicReviewDecisionRepository decisionRepository,
			StudyBlockService blockService, StudyMaterialRepository materialRepository, SourceTopicExtractor extractor,
			CurrentUserResolver currentUserResolver) {
		this.topicRepository = topicRepository;
		this.decisionRepository = decisionRepository;
		this.blockService = blockService;
		this.materialRepository = materialRepository;
		this.extractor = extractor;
		this.currentUserResolver = currentUserResolver;
	}

	/** O que o usuário já rejeitou e comentou: vai no prompt da próxima extração. */
	@Transactional(readOnly = true)
	public SourceTopicExtractor.ReviewMemory memory() {
		String owner = currentUserResolver.currentUserId();
		List<String> rejected = decisionRepository
				.findTop40ByOwnerIdAndActionOrderByCreatedAtDesc(owner, TopicReviewState.REJECTED).stream()
				.map(TopicReviewDecision::getTopicTitle)
				.distinct().limit(MEMORY_SIZE).toList();
		List<String> notes = decisionRepository
				.findTop40ByOwnerIdAndActionOrderByCreatedAtDesc(owner, TopicReviewState.FEEDBACK).stream()
				.filter(d -> d.getFeedback() != null)
				.map(d -> "\"" + d.getTopicTitle() + "\": " + d.getFeedback())
				.distinct().limit(MEMORY_SIZE).toList();
		return new SourceTopicExtractor.ReviewMemory(rejected, notes);
	}

	@Transactional(readOnly = true)
	public ReviewBoard board(UUID blockId) {
		blockService.get(blockId);
		List<KnowledgeTopic> pending = pending(blockId);
		Map<UUID, StudyMaterial> materials = new HashMap<>();
		for (StudyMaterial material : materialRepository.findAllByStudyBlockIdOrderByUpdatedAtDesc(blockId)) {
			materials.put(material.getId(), material);
		}
		Set<UUID> pendingIds = new HashSet<>();
		pending.forEach(t -> pendingIds.add(t.getId()));
		Map<UUID, String> activeTitles = new HashMap<>();
		for (KnowledgeTopic active : topicRepository.findAllByStudyBlockIdAndStatusOrderByCreatedAtAsc(blockId,
				KnowledgeTopicStatus.ACTIVE)) {
			activeTitles.put(active.getId(), active.getTitle());
		}

		List<ReviewItem> items = pending.stream()
				.sorted(Comparator.comparing((KnowledgeTopic t) -> t.getRelevanceScore() == null ? 0 : t.getRelevanceScore())
						.reversed())
				.map(topic -> {
					StudyMaterial material = topic.getSourceMaterialId() == null ? null : materials.get(topic.getSourceMaterialId());
					String videoId = material != null && material.getSourceType() == StudyMaterialSourceType.YOUTUBE
							? material.getExternalVideoId() : null;
					// Subtópico de um tópico já oficial: mostra o título do pai para dar contexto.
					String officialParentTitle = topic.getParentTopicId() != null && !pendingIds.contains(topic.getParentTopicId())
							? activeTitles.get(topic.getParentTopicId()) : null;
					return new ReviewItem(topic, material == null ? null : material.getTitle(), videoId, officialParentTitle);
				})
				.toList();

		long approved = pending.stream().filter(t -> t.getReviewState() == TopicReviewState.APPROVED).count();
		long rejected = pending.stream().filter(t -> t.getReviewState() == TopicReviewState.REJECTED).count();
		long feedback = pending.stream().filter(t -> t.getReviewState() == TopicReviewState.FEEDBACK).count();
		int round = pending.stream().mapToInt(KnowledgeTopic::getReviewRound).max().orElse(0);
		return new ReviewBoard(items, new ReviewSummary(pending.size(), approved, rejected, feedback,
				pending.size() - approved - rejected - feedback, round, firstRoundApprovalPercent()));
	}

	/** Aprova, exclui ou comenta um tópico sugerido (e opcionalmente o renomeia). */
	@Transactional
	public KnowledgeTopic review(UUID blockId, UUID topicId, TopicReviewState action, String feedback, String newTitle) {
		KnowledgeTopic topic = pendingOwned(blockId, topicId);
		if (action == null || action == TopicReviewState.PENDING) {
			throw new InvalidInputException("Escolha aprovar, excluir ou enviar um comentário.");
		}
		if (action == TopicReviewState.FEEDBACK && (feedback == null || feedback.isBlank())) {
			throw new InvalidInputException("Escreva o que pode ser melhorado ou acrescentado neste tópico.");
		}
		if (newTitle != null && !newTitle.isBlank()) {
			topic.renameInReview(newTitle.strip());
		}
		topic.review(action, feedback);
		topicRepository.save(topic);
		record(topic, action, feedback);
		if (action == TopicReviewState.REJECTED && !topic.isSubtopic()) {
			// Excluir o tópico principal exclui as sugestões filhas.
			for (KnowledgeTopic child : topicRepository.findAllByParentTopicIdAndStatus(topic.getId(),
					KnowledgeTopicStatus.PENDING_REVIEW)) {
				child.review(TopicReviewState.REJECTED, null);
				topicRepository.save(child);
			}
		}
		return topic;
	}

	/** Aprova de uma vez as sugestões ainda sem decisão com relevância igual ou acima do mínimo. */
	@Transactional
	public int approveRelevant(UUID blockId, int minRelevance) {
		blockService.get(blockId);
		int count = 0;
		for (KnowledgeTopic topic : pending(blockId)) {
			if (topic.getReviewState() == TopicReviewState.PENDING && topic.getRelevanceScore() != null
					&& topic.getRelevanceScore() >= minRelevance) {
				topic.review(TopicReviewState.APPROVED, null);
				topicRepository.save(topic);
				record(topic, TopicReviewState.APPROVED, null);
				count++;
			}
		}
		return count;
	}

	/**
	 * Revisa com IA os tópicos com comentário e devolve-os numa nova rodada. Sem transação: a chamada de IA não segura
	 * conexão com o banco; cada gravação é feita pelo repositório.
	 */
	public int refine(UUID blockId) {
		StudyBlock block = blockService.get(blockId);
		List<KnowledgeTopic> withFeedback = pending(blockId).stream()
				.filter(t -> t.getReviewState() == TopicReviewState.FEEDBACK)
				.toList();
		if (withFeedback.isEmpty()) {
			throw new BusinessRuleException("Nenhum tópico com comentário para revisar.");
		}
		List<StudyMaterial> materials = materialRepository
				.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyMaterialStatus.ACTIVE).stream()
				.filter(StudyMaterial::isEnabledForChat)
				.toList();
		List<SourceTopicExtractor.RefineRequest> requests = withFeedback.stream()
				.map(t -> new SourceTopicExtractor.RefineRequest(t.getId(), t.getTitle(), t.getDescription(),
						t.getSupportingExcerpt(), t.getUserFeedback()))
				.toList();

		List<SourceTopicExtractor.RevisedTopic> revised = extractor.refine(block, materials, requests);
		OffsetDateTime now = OffsetDateTime.now();
		int count = 0;
		for (SourceTopicExtractor.RevisedTopic revision : revised) {
			KnowledgeTopic topic = topicRepository.findById(revision.id()).filter(KnowledgeTopic::isPendingReview).orElse(null);
			if (topic == null) {
				continue;
			}
			topic.applyRevision(revision.title(), revision.description(), revision.supportingExcerpt(),
					revision.relevance(), revision.relevanceReason());
			topicRepository.save(topic);
			count++;
			// Subtópicos pedidos no comentário entram como novas sugestões (só sob tópico principal).
			UUID parentId = topic.isSubtopic() ? topic.getParentTopicId() : topic.getId();
			for (SourceTopicExtractor.ExtractedSubtopic sub : revision.newSubtopics()) {
				KnowledgeTopic suggestion = KnowledgeTopic.suggestion(block, parentId, sub.title(),
						sub.description() == null || sub.description().isBlank() ? null : sub.description(),
						topic.getSourceMaterialId(), sub.supportingExcerpt(), sub.relevance(), sub.relevanceReason(), now);
				suggestion.startAtRound(topic.getReviewRound());
				topicRepository.save(suggestion);
			}
		}
		return count;
	}

	/**
	 * Oficializa os aprovados (viram ACTIVE) e arquiva os excluídos. Subtópico aprovado cujo tópico principal ainda não
	 * foi aprovado espera na revisão.
	 */
	@Transactional
	public PublishResult publish(UUID blockId) {
		blockService.get(blockId);
		List<KnowledgeTopic> pending = pending(blockId);
		int published = 0;
		int archived = 0;
		int waiting = 0;
		Set<UUID> publishedMains = new LinkedHashSet<>();
		for (KnowledgeTopic topic : pending) {
			if (!topic.isSubtopic() && topic.getReviewState() == TopicReviewState.APPROVED) {
				topic.publish();
				topicRepository.save(topic);
				publishedMains.add(topic.getId());
				published++;
			}
		}
		for (KnowledgeTopic topic : pending) {
			if (topic.getReviewState() == TopicReviewState.REJECTED) {
				topic.archive();
				topicRepository.save(topic);
				archived++;
			} else if (topic.isSubtopic() && topic.getReviewState() == TopicReviewState.APPROVED) {
				boolean parentOfficial = publishedMains.contains(topic.getParentTopicId())
						|| topicRepository.findById(topic.getParentTopicId())
								.map(parent -> parent.getStatus() == KnowledgeTopicStatus.ACTIVE).orElse(false);
				if (parentOfficial) {
					topic.publish();
					topicRepository.save(topic);
					published++;
				} else {
					waiting++;
				}
			}
		}
		if (published == 0 && archived == 0) {
			throw new BusinessRuleException(waiting > 0
					? "Aprove também o tópico principal dos subtópicos aprovados antes de oficializar."
					: "Aprove ou exclua ao menos um tópico antes de oficializar.");
		}
		return new PublishResult(published, archived, waiting);
	}

	private List<KnowledgeTopic> pending(UUID blockId) {
		return topicRepository.findAllByStudyBlockIdAndStatusOrderByCreatedAtAsc(blockId, KnowledgeTopicStatus.PENDING_REVIEW);
	}

	private KnowledgeTopic pendingOwned(UUID blockId, UUID topicId) {
		blockService.get(blockId);
		KnowledgeTopic topic = topicRepository.findById(topicId)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new NotFoundException("Tópico não encontrado."));
		if (!topic.isPendingReview()) {
			throw new BusinessRuleException("Este tópico não está aguardando validação.");
		}
		return topic;
	}

	private void record(KnowledgeTopic topic, TopicReviewState action, String feedback) {
		decisionRepository.save(new TopicReviewDecision(currentUserResolver.currentUserId(), topic.getStudyBlock().getId(),
				topic.getTitle(), action, feedback == null || feedback.isBlank() ? null : feedback.strip(),
				Math.max(1, topic.getReviewRound())));
	}

	/** Aprovações na primeira rodada sobre todas as decisões da primeira rodada (precisão da extração). */
	private Integer firstRoundApprovalPercent() {
		List<TopicReviewDecision> firstRound = decisionRepository.findAllByOwnerIdAndReviewRound(
				currentUserResolver.currentUserId(), 1);
		if (firstRound.isEmpty()) {
			return null;
		}
		long approved = firstRound.stream().filter(d -> d.getAction() == TopicReviewState.APPROVED).count();
		return (int) Math.round(approved * 100.0 / firstRound.size());
	}

	public record ReviewItem(KnowledgeTopic topic, String sourceMaterialTitle, String sourceVideoId,
			String officialParentTitle) {}

	public record ReviewSummary(long total, long approved, long rejected, long withFeedback, long undecided, int round,
			Integer firstRoundApprovalPercent) {}

	public record ReviewBoard(List<ReviewItem> items, ReviewSummary summary) {}

	public record PublishResult(int published, int archived, int waitingForParent) {}
}
