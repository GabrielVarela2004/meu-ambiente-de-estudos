package br.com.estudos.estudos_backend.knowledge;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Vínculo entre um tópico e uma questão. Fica no módulo de conhecimento para que o módulo de questões
 * não precise depender de tópicos.
 */
@Entity
@Table(name = "knowledge_topic_questions")
@IdClass(KnowledgeTopicQuestion.Key.class)
public class KnowledgeTopicQuestion {

	@Id
	@Column(name = "topic_id")
	private UUID topicId;

	@Id
	@Column(name = "question_id")
	private UUID questionId;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected KnowledgeTopicQuestion() {}

	public KnowledgeTopicQuestion(UUID topicId, UUID questionId) {
		this.topicId = topicId;
		this.questionId = questionId;
		this.createdAt = OffsetDateTime.now();
	}

	public UUID getTopicId() { return topicId; }
	public UUID getQuestionId() { return questionId; }
	public OffsetDateTime getCreatedAt() { return createdAt; }

	public static class Key implements Serializable {
		private UUID topicId;
		private UUID questionId;

		public Key() {}

		public Key(UUID topicId, UUID questionId) {
			this.topicId = topicId;
			this.questionId = questionId;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) return true;
			if (!(other instanceof Key key)) return false;
			return Objects.equals(topicId, key.topicId) && Objects.equals(questionId, key.questionId);
		}

		@Override
		public int hashCode() { return Objects.hash(topicId, questionId); }
	}
}
