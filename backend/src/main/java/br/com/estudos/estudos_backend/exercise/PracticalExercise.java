package br.com.estudos.estudos_backend.exercise;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.question.StudyQuestionDifficulty;
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

@Entity
@Table(name = "practical_exercises")
public class PracticalExercise {
	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "study_block_id", nullable = false)
	private StudyBlock studyBlock;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_material_id")
	private StudyMaterial studyMaterial;

	@Column(name = "topic_id")
	private UUID topicId;

	@Column(name = "source_excerpt", length = 2000)
	private String sourceExcerpt;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String prompt;

	@Column(columnDefinition = "TEXT")
	private String guidance;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StudyQuestionDifficulty difficulty;

	@Enumerated(EnumType.STRING)
	@Column(name = "exercise_type", nullable = false, length = 30)
	private PracticalExerciseType exerciseType;

	@Column(name = "starter_code", columnDefinition = "TEXT")
	private String starterCode;

	@Column(name = "solution_code", columnDefinition = "TEXT")
	private String solutionCode;

	@Column(name = "hints_json", columnDefinition = "TEXT")
	private String hintsJson;

	@Column(name = "test_cases_json", columnDefinition = "TEXT")
	private String testCasesJson;

	@Column(name = "steps_json", columnDefinition = "TEXT")
	private String stepsJson;

	@Column(length = 20)
	private String language;

	@Column(name = "test_code", columnDefinition = "TEXT")
	private String testCode;

	/** Checagens de conceito, uma por linha no formato TIPO|arg1|arg2. */
	@Column(name = "concept_checks", columnDefinition = "TEXT")
	private String conceptChecks;

	@Column(name = "auto_evaluable")
	private Boolean autoEvaluable;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PracticalExerciseStatus status;

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

	protected PracticalExercise() {}

	public PracticalExercise(StudyBlock studyBlock, String title, String prompt, String guidance) {
		this(
				studyBlock,
				null,
				null,
				null,
				title,
				prompt,
				guidance,
				StudyQuestionDifficulty.INTERMEDIATE,
				PracticalExerciseType.PROBLEM_SOLVING,
				null,
				null,
				null,
				null);
	}

	public PracticalExercise(
			StudyBlock studyBlock,
			StudyMaterial studyMaterial,
			UUID topicId,
			String title,
			String prompt,
			String guidance,
			StudyQuestionDifficulty difficulty,
			PracticalExerciseType exerciseType,
			String starterCode,
			String solutionCode,
			String hintsJson,
			String testCasesJson) {
		this(
				studyBlock,
				studyMaterial,
				topicId,
				null,
				title,
				prompt,
				guidance,
				difficulty,
				exerciseType,
				starterCode,
				solutionCode,
				hintsJson,
				testCasesJson);
	}

	public PracticalExercise(
			StudyBlock studyBlock,
			StudyMaterial studyMaterial,
			UUID topicId,
			String sourceExcerpt,
			String title,
			String prompt,
			String guidance,
			StudyQuestionDifficulty difficulty,
			PracticalExerciseType exerciseType,
			String starterCode,
			String solutionCode,
			String hintsJson,
			String testCasesJson) {
		this.studyBlock = studyBlock;
		this.studyMaterial = studyMaterial;
		this.topicId = topicId;
		this.sourceExcerpt = sourceExcerpt;
		this.title = title;
		this.prompt = prompt;
		this.guidance = guidance;
		this.difficulty = difficulty == null ? StudyQuestionDifficulty.INTERMEDIATE : difficulty;
		this.exerciseType = exerciseType == null ? PracticalExerciseType.PROBLEM_SOLVING : exerciseType;
		this.starterCode = starterCode;
		this.solutionCode = solutionCode;
		this.hintsJson = hintsJson;
		this.testCasesJson = testCasesJson;
		this.status = PracticalExerciseStatus.ACTIVE;
	}

	@PrePersist
	void onCreate() {
		if (id == null) id = UUID.randomUUID();
		if (difficulty == null) difficulty = StudyQuestionDifficulty.INTERMEDIATE;
		if (exerciseType == null) exerciseType = PracticalExerciseType.PROBLEM_SOLVING;
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
		if (nextReviewAt == null) nextReviewAt = now;
		if (reviewIntervalDays < 1) reviewIntervalDays = 1;
	}

	@PreUpdate
	void onUpdate() { updatedAt = OffsetDateTime.now(); }

	public void scheduleNextReview(boolean passedIndependently) {
		if (passedIndependently) {
			correctStreak++;
			StudyQuestionDifficulty diff = this.difficulty == null ? StudyQuestionDifficulty.INTERMEDIATE : this.difficulty;
			switch (diff) {
				case EASY -> {
					if (correctStreak == 1) {
						reviewIntervalDays = 4;
					} else if (correctStreak == 2) {
						reviewIntervalDays = 9;
					} else if (correctStreak == 3) {
						reviewIntervalDays = 20;
					} else {
						reviewIntervalDays = Math.min(365, (int) Math.round(reviewIntervalDays * 2.5));
					}
				}
				case HARD -> {
					if (correctStreak == 1) {
						reviewIntervalDays = 1;
					} else if (correctStreak == 2) {
						reviewIntervalDays = 2;
					} else if (correctStreak == 3) {
						reviewIntervalDays = 4;
					} else {
						reviewIntervalDays = Math.min(365, (int) Math.round(reviewIntervalDays * 1.5));
					}
				}
				case INTERMEDIATE -> {
					if (correctStreak == 1) {
						reviewIntervalDays = 3;
					} else if (correctStreak == 2) {
						reviewIntervalDays = 6;
					} else if (correctStreak == 3) {
						reviewIntervalDays = 12;
					} else {
						reviewIntervalDays = Math.min(365, reviewIntervalDays * 2);
					}
				}
			}
		} else {
			correctStreak = 0;
			reviewIntervalDays = 1;
		}
		nextReviewAt = OffsetDateTime.now().plusDays(reviewIntervalDays);
	}

	/** Etapas do exercício (lista JSON de textos) usadas pela tutoria para localizar a dificuldade. */
	public void defineSteps(String stepsJson) { this.stepsJson = stepsJson; }

	public void configureAutoEvaluation(String language, String testCode, String conceptChecks) {
		this.language = language;
		this.testCode = testCode;
		this.conceptChecks = conceptChecks;
		this.autoEvaluable = null;
	}

	/** Resultado da validação: a solução de referência passou (true) ou não (false) nos próprios testes. */
	public void markAutoEvaluable(boolean autoEvaluable) { this.autoEvaluable = autoEvaluable; }

	public boolean hasAutomaticTests() {
		return "JAVA".equals(language) && testCode != null && !testCode.isBlank();
	}

	public void archive() { status = PracticalExerciseStatus.ARCHIVED; }
	public UUID getId() { return id; }
	public StudyBlock getStudyBlock() { return studyBlock; }
	public StudyMaterial getStudyMaterial() { return studyMaterial; }
	public UUID getTopicId() { return topicId; }
	public String getSourceExcerpt() { return sourceExcerpt; }
	public String getTitle() { return title; }
	public String getPrompt() { return prompt; }
	public String getGuidance() { return guidance; }
	public StudyQuestionDifficulty getDifficulty() { return difficulty; }
	public PracticalExerciseType getExerciseType() { return exerciseType; }
	public String getStarterCode() { return starterCode; }
	public String getSolutionCode() { return solutionCode; }
	public String getHintsJson() { return hintsJson; }
	public String getTestCasesJson() { return testCasesJson; }
	public String getStepsJson() { return stepsJson; }
	public String getLanguage() { return language; }
	public String getTestCode() { return testCode; }
	public String getConceptChecks() { return conceptChecks; }
	public Boolean getAutoEvaluable() { return autoEvaluable; }
	public PracticalExerciseStatus getStatus() { return status; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
	public OffsetDateTime getNextReviewAt() { return nextReviewAt; }
	public int getReviewIntervalDays() { return reviewIntervalDays; }
	public int getCorrectStreak() { return correctStreak; }
}
