package br.com.estudos.estudos_backend.knowledge;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Tópico ou subtópico de um bloco de estudo. Um subtópico é um tópico com {@code parentTopicId} preenchido.
 * A hierarquia é limitada a dois níveis (tópico → subtópico) e suporta relação de pré-requisito
 * e metadados de extração automática a partir das fontes reais do bloco.
 */
@Entity
@Table(name = "knowledge_topics")
public class KnowledgeTopic {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "study_block_id", nullable = false)
	private StudyBlock studyBlock;

	@Column(name = "parent_topic_id")
	private UUID parentTopicId;

	@Column(name = "prerequisite_topic_id")
	private UUID prerequisiteTopicId;

	@Column(name = "source_material_id")
	private UUID sourceMaterialId;

	@Column(name = "supporting_excerpt", length = 2000)
	private String supportingExcerpt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 25)
	private KnowledgeTopicOrigin origin;

	@Column(name = "user_validated", nullable = false)
	private boolean userValidated;

	@Column(name = "extracted_at")
	private OffsetDateTime extractedAt;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(length = 2000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private KnowledgeTopicStatus status;

	/** Relevância técnica estimada na extração (0–100); null em tópicos manuais e antigos. */
	@Column(name = "relevance_score")
	private Integer relevanceScore;

	@Column(name = "relevance_reason", length = 500)
	private String relevanceReason;

	/** Rodada de validação (1 = primeira sugestão; cada revisão com IA soma 1); 0 = fora do fluxo de revisão. */
	@Column(name = "review_round", nullable = false)
	private int reviewRound;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_state", length = 20)
	private TopicReviewState reviewState;

	@Column(name = "user_feedback", length = 1000)
	private String userFeedback;

	/** Título antes da revisão com IA, para mostrar o "antes → depois". */
	@Column(name = "previous_title", length = 160)
	private String previousTitle;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected KnowledgeTopic() {}

	public KnowledgeTopic(StudyBlock studyBlock, UUID parentTopicId, String title, String description) {
		this(studyBlock, parentTopicId, null, title, description);
	}

	public KnowledgeTopic(StudyBlock studyBlock, UUID parentTopicId, UUID prerequisiteTopicId, String title, String description) {
		this(studyBlock, parentTopicId, prerequisiteTopicId, title, description,
				null, null, KnowledgeTopicOrigin.MANUAL, true, null);
	}

	public KnowledgeTopic(StudyBlock studyBlock, UUID parentTopicId, UUID prerequisiteTopicId,
			String title, String description, UUID sourceMaterialId, String supportingExcerpt,
			KnowledgeTopicOrigin origin, boolean userValidated, OffsetDateTime extractedAt) {
		this.studyBlock = studyBlock;
		this.parentTopicId = parentTopicId;
		this.prerequisiteTopicId = prerequisiteTopicId;
		this.title = title;
		this.description = description;
		this.sourceMaterialId = sourceMaterialId;
		this.supportingExcerpt = supportingExcerpt;
		this.origin = origin == null ? KnowledgeTopicOrigin.MANUAL : origin;
		this.userValidated = userValidated;
		this.extractedAt = extractedAt;
		this.status = KnowledgeTopicStatus.ACTIVE;
	}

	@PrePersist
	void onCreate() {
		if (id == null) id = UUID.randomUUID();
		if (origin == null) origin = KnowledgeTopicOrigin.MANUAL;
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() { updatedAt = OffsetDateTime.now(); }

	public void update(String title, String description) {
		// Tópico extraído e renomeado: a próxima extração ainda o reconhece pelo nome sugerido.
		if (origin == KnowledgeTopicOrigin.AI_EXTRACTED && previousTitle == null && title != null
				&& !title.equals(this.title)) {
			this.previousTitle = this.title;
		}
		this.title = title;
		this.description = description;
		this.userValidated = true;
	}

	public void updateExtractedEvidence(String description, UUID sourceMaterialId,
			String supportingExcerpt, OffsetDateTime extractedAt) {
		if (description != null && !description.isBlank() && (this.description == null || this.description.isBlank())) {
			this.description = description;
		}
		if (sourceMaterialId != null) {
			this.sourceMaterialId = sourceMaterialId;
		}
		if (supportingExcerpt != null && !supportingExcerpt.isBlank()) {
			this.supportingExcerpt = supportingExcerpt;
		}
		this.extractedAt = extractedAt;
		if (this.status == KnowledgeTopicStatus.ARCHIVED) {
			this.status = KnowledgeTopicStatus.ACTIVE;
		}
	}

	public void markUserValidated() {
		this.userValidated = true;
	}

	/** Sugestão da extração: aguarda validação manual (rodada 1). */
	public static KnowledgeTopic suggestion(StudyBlock studyBlock, UUID parentTopicId, String title, String description,
			UUID sourceMaterialId, String supportingExcerpt, Integer relevanceScore, String relevanceReason,
			OffsetDateTime extractedAt) {
		KnowledgeTopic topic = new KnowledgeTopic(studyBlock, parentTopicId, null, title, description, sourceMaterialId,
				supportingExcerpt, KnowledgeTopicOrigin.AI_EXTRACTED, false, extractedAt);
		topic.status = KnowledgeTopicStatus.PENDING_REVIEW;
		topic.reviewState = TopicReviewState.PENDING;
		topic.reviewRound = 1;
		topic.updateRelevance(relevanceScore, relevanceReason);
		return topic;
	}

	/** Sugestão criada numa revisão (ex.: subtópico pedido no comentário) entra na rodada atual. */
	public void startAtRound(int round) {
		this.reviewRound = Math.max(1, round);
	}

	/** Um tópico arquivado que reaparece na extração volta como sugestão, nunca direto como oficial. */
	public void resubmitForReview() {
		status = KnowledgeTopicStatus.PENDING_REVIEW;
		reviewState = TopicReviewState.PENDING;
		reviewRound = Math.max(1, reviewRound);
		userFeedback = null;
	}

	public void updateRelevance(Integer relevanceScore, String relevanceReason) {
		this.relevanceScore = relevanceScore == null ? null : Math.max(0, Math.min(100, relevanceScore));
		if (relevanceReason != null && !relevanceReason.isBlank()) {
			this.relevanceReason = relevanceReason.length() > 500 ? relevanceReason.substring(0, 500) : relevanceReason;
		}
	}

	/** Renomeado na revisão: guarda o nome sugerido para a próxima extração reconhecer o mesmo tópico. */
	public void renameInReview(String newTitle) {
		if (newTitle != null && !newTitle.isBlank() && !newTitle.equals(title)) {
			if (previousTitle == null) {
				previousTitle = title;
			}
			title = newTitle;
		}
	}

	public void review(TopicReviewState state, String feedback) {
		this.reviewState = state;
		this.userFeedback = feedback == null || feedback.isBlank() ? null : feedback.strip();
	}

	/** Versão revisada pela IA a partir do comentário: nova rodada, de novo pendente. */
	public void applyRevision(String title, String description, String supportingExcerpt, Integer relevanceScore,
			String relevanceReason) {
		if (title != null && !title.isBlank() && !title.equals(this.title)) {
			this.previousTitle = this.title;
			this.title = title;
		}
		if (description != null && !description.isBlank()) {
			this.description = description;
		}
		if (supportingExcerpt != null && !supportingExcerpt.isBlank()) {
			this.supportingExcerpt = supportingExcerpt;
		}
		updateRelevance(relevanceScore, relevanceReason);
		this.reviewRound++;
		this.reviewState = TopicReviewState.PENDING;
	}

	/** Aprovação final: o tópico passa a valer em questões e desempenho. */
	public void publish() {
		status = KnowledgeTopicStatus.ACTIVE;
		reviewState = null;
		userValidated = true;
	}

	public boolean isPendingReview() { return status == KnowledgeTopicStatus.PENDING_REVIEW; }

	public void setPrerequisiteTopicId(UUID prerequisiteTopicId) {
		this.prerequisiteTopicId = prerequisiteTopicId;
	}

	public void archive() { status = KnowledgeTopicStatus.ARCHIVED; }

	public boolean isSubtopic() { return parentTopicId != null; }

	public UUID getId() { return id; }
	public StudyBlock getStudyBlock() { return studyBlock; }
	public UUID getParentTopicId() { return parentTopicId; }
	public UUID getPrerequisiteTopicId() { return prerequisiteTopicId; }
	public UUID getSourceMaterialId() { return sourceMaterialId; }
	public String getSupportingExcerpt() { return supportingExcerpt; }
	public KnowledgeTopicOrigin getOrigin() { return origin; }
	public boolean isUserValidated() { return userValidated; }
	public OffsetDateTime getExtractedAt() { return extractedAt; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public KnowledgeTopicStatus getStatus() { return status; }
	public Integer getRelevanceScore() { return relevanceScore; }
	public String getRelevanceReason() { return relevanceReason; }
	public int getReviewRound() { return reviewRound; }
	public TopicReviewState getReviewState() { return reviewState; }
	public String getUserFeedback() { return userFeedback; }
	public String getPreviousTitle() { return previousTitle; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
