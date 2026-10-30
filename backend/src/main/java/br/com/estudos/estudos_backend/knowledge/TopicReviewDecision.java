package br.com.estudos.estudos_backend.knowledge;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Decisão do usuário sobre um tópico sugerido; memória usada nas próximas extrações. */
@Entity
@Table(name = "topic_review_decisions")
public class TopicReviewDecision {

	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, length = 120)
	private String ownerId;

	@Column(name = "study_block_id", nullable = false)
	private UUID studyBlockId;

	@Column(name = "topic_title", nullable = false, length = 160)
	private String topicTitle;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TopicReviewState action;

	@Column(length = 1000)
	private String feedback;

	@Column(name = "review_round", nullable = false)
	private int reviewRound;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected TopicReviewDecision() {
	}

	public TopicReviewDecision(String ownerId, UUID studyBlockId, String topicTitle, TopicReviewState action,
			String feedback, int reviewRound) {
		this.id = UUID.randomUUID();
		this.ownerId = ownerId;
		this.studyBlockId = studyBlockId;
		this.topicTitle = topicTitle;
		this.action = action;
		this.feedback = feedback;
		this.reviewRound = reviewRound;
		this.createdAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public String getOwnerId() { return ownerId; }
	public UUID getStudyBlockId() { return studyBlockId; }
	public String getTopicTitle() { return topicTitle; }
	public TopicReviewState getAction() { return action; }
	public String getFeedback() { return feedback; }
	public int getReviewRound() { return reviewRound; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
