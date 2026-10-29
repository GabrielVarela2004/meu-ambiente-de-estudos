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
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
