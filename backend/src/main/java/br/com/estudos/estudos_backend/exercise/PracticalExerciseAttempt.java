package br.com.estudos.estudos_backend.exercise;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "practical_exercise_attempts")
public class PracticalExerciseAttempt {
	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "exercise_id", nullable = false)
	private PracticalExercise exercise;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String answer;

	@Column(name = "self_rating", nullable = false)
	private int selfRating;

	@Column(columnDefinition = "TEXT")
	private String feedback;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected PracticalExerciseAttempt() {}

	public PracticalExerciseAttempt(PracticalExercise exercise, String answer, int selfRating, String feedback) {
		this.exercise = exercise;
		this.answer = answer;
		this.selfRating = selfRating;
		this.feedback = feedback;
	}

	@PrePersist
	void onCreate() {
		if (id == null) id = UUID.randomUUID();
		createdAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public PracticalExercise getExercise() { return exercise; }
	public String getAnswer() { return answer; }
	public int getSelfRating() { return selfRating; }
	public String getFeedback() { return feedback; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
