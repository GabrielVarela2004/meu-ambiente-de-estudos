package br.com.estudos.estudos_backend.question;

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
@Table(name = "study_question_options")
public class StudyQuestionOption {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_id", nullable = false)
	private StudyQuestion question;

	@Column(name = "option_text", nullable = false, length = 500)
	private String text;

	@Column(name = "option_order", nullable = false)
	private int order;

	@Column(nullable = false)
	private boolean correct;

	protected StudyQuestionOption() {
	}

	public StudyQuestionOption(StudyQuestion question, String text, int order, boolean correct) {
		this.question = question;
		this.text = text;
		this.order = order;
		this.correct = correct;
	}

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public UUID getId() { return id; }
	public String getText() { return text; }
	public int getOrder() { return order; }
	public boolean isCorrect() { return correct; }
}
