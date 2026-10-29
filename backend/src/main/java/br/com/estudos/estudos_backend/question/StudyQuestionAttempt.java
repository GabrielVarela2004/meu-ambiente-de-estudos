package br.com.estudos.estudos_backend.question;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "study_question_attempts")
public class StudyQuestionAttempt {
	@Id
	private UUID id;
	@Column(name = "study_block_id", nullable = false)
	private UUID studyBlockId;
	@Column(name = "question_id", nullable = false)
	private UUID questionId;
	@Column(name = "option_id")
	private UUID optionId;
	@Column(name = "discursive_answer", length = 10000)
	private String discursiveAnswer;
	@Column(nullable = false)
	private boolean correct;
	@Column(name = "answered_at", nullable = false)
	private OffsetDateTime answeredAt;

	protected StudyQuestionAttempt() {}

	public StudyQuestionAttempt(UUID studyBlockId, UUID questionId, UUID optionId, boolean correct) {
		this(studyBlockId, questionId, optionId, null, correct);
	}

	public StudyQuestionAttempt(UUID studyBlockId, UUID questionId, UUID optionId, String discursiveAnswer, boolean correct) {
		this.studyBlockId = studyBlockId;
		this.questionId = questionId;
		this.optionId = optionId;
		this.discursiveAnswer = discursiveAnswer;
		this.correct = correct;
	}

	@PrePersist
	void onCreate() {
		if (id == null) id = UUID.randomUUID();
		answeredAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public UUID getStudyBlockId() { return studyBlockId; }
	public UUID getQuestionId() { return questionId; }
	public UUID getOptionId() { return optionId; }
	public String getDiscursiveAnswer() { return discursiveAnswer; }
	public boolean isCorrect() { return correct; }
	public OffsetDateTime getAnsweredAt() { return answeredAt; }
}
