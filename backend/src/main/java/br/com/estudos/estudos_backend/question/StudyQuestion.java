package br.com.estudos.estudos_backend.question;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "study_questions")
public class StudyQuestion {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "study_block_id", nullable = false)
	private StudyBlock studyBlock;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_material_id")
	private StudyMaterial studyMaterial;

	@Column(nullable = false, length = 2000)
	private String prompt;

	@Column(length = 2000)
	private String explanation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StudyQuestionDifficulty difficulty;

	@Enumerated(EnumType.STRING)
	@Column(name = "question_format", nullable = false, length = 25)
	private StudyQuestionFormat format;

	@Column(name = "expected_answer", length = 4000)
	private String expectedAnswer;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StudyQuestionStatus status;

	@OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<StudyQuestionOption> options = new ArrayList<>();

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@Column(name = "next_review_at", nullable = false)
	private OffsetDateTime nextReviewAt;

	@Column(name = "review_interval_days", nullable = false)
	private int reviewIntervalDays;

	@Column(name = "correct_streak", nullable = false)
	private int correctStreak;

	protected StudyQuestion() {}

	public StudyQuestion(StudyBlock block, StudyMaterial material, String prompt, String explanation) {
		this(block, material, prompt, explanation, StudyQuestionDifficulty.INTERMEDIATE,
				StudyQuestionFormat.MULTIPLE_CHOICE, null);
	}

	public StudyQuestion(StudyBlock block, StudyMaterial material, String prompt, String explanation,
			StudyQuestionDifficulty difficulty, StudyQuestionFormat format, String expectedAnswer) {
		this.studyBlock = block;
		this.studyMaterial = material;
		this.prompt = prompt;
		this.explanation = explanation;
		this.difficulty = difficulty == null ? StudyQuestionDifficulty.INTERMEDIATE : difficulty;
		this.format = format == null ? StudyQuestionFormat.MULTIPLE_CHOICE : format;
		this.expectedAnswer = expectedAnswer;
		this.status = StudyQuestionStatus.ACTIVE;
	}

	public void addOption(String text, int order, boolean correct) {
		options.add(new StudyQuestionOption(this, text, order, correct));
	}

	public void archive() { status = StudyQuestionStatus.ARCHIVED; }

	@PrePersist
	void onCreate() {
		if (id == null) id = UUID.randomUUID();
		if (difficulty == null) difficulty = StudyQuestionDifficulty.INTERMEDIATE;
		if (format == null) format = StudyQuestionFormat.MULTIPLE_CHOICE;
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
		nextReviewAt = now;
		reviewIntervalDays = 1;
		correctStreak = 0;
	}

	@PreUpdate
	void onUpdate() { updatedAt = OffsetDateTime.now(); }

	public void scheduleNextReview(boolean correct) {
		if (correct) {
			correctStreak++;
			reviewIntervalDays = Math.min(365, correctStreak == 1 ? 3 : reviewIntervalDays * 2);
		} else {
			correctStreak = 0;
			reviewIntervalDays = 1;
		}
		nextReviewAt = OffsetDateTime.now().plusDays(reviewIntervalDays);
	}

	public void rescheduleManual(int days) {
		this.reviewIntervalDays = days;
		this.nextReviewAt = OffsetDateTime.now().plusDays(days);
	}

	public UUID getId() { return id; }
	public StudyBlock getStudyBlock() { return studyBlock; }
	public StudyMaterial getStudyMaterial() { return studyMaterial; }
	public String getPrompt() { return prompt; }
	public String getExplanation() { return explanation; }
	public StudyQuestionDifficulty getDifficulty() { return difficulty; }
	public StudyQuestionFormat getFormat() { return format; }
	public String getExpectedAnswer() { return expectedAnswer; }
	public StudyQuestionStatus getStatus() { return status; }
	public List<StudyQuestionOption> getOptions() { return options; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
	public OffsetDateTime getNextReviewAt() { return nextReviewAt; }
	public int getReviewIntervalDays() { return reviewIntervalDays; }
	public int getCorrectStreak() { return correctStreak; }
}
