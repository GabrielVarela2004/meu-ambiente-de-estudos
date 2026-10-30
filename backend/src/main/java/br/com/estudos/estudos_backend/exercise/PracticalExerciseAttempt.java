package br.com.estudos.estudos_backend.exercise;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.estudos.estudos_backend.learning.ActivityOriginType;
import br.com.estudos.estudos_backend.learning.AssistanceLevel;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	@Column(name = "owner_id", nullable = false, length = 120)
	private String ownerId;

	@Column(name = "study_block_id")
	private UUID studyBlockId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "exercise_id", nullable = false)
	private PracticalExercise exercise;

	@Column(name = "topic_id")
	private UUID topicId;

	@Column(name = "study_session_id")
	private UUID studySessionId;

	@Column(name = "source_material_id")
	private UUID sourceMaterialId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String answer;

	@Column(name = "self_rating", nullable = false)
	private int selfRating;

	@Column(columnDefinition = "TEXT")
	private String feedback;

	@Enumerated(EnumType.STRING)
	@Column(name = "origin_type", nullable = false, length = 20)
	private ActivityOriginType originType;

	@Enumerated(EnumType.STRING)
	@Column(name = "evaluation_status", nullable = false, length = 25)
	private ExerciseEvaluationStatus evaluationStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "assistance_level", nullable = false, length = 25)
	private AssistanceLevel assistanceLevel;

	@Column(name = "hints_used_count", nullable = false)
	private int hintsUsedCount;

	@Column(name = "solution_revealed", nullable = false)
	private boolean solutionRevealed;

	@Column(name = "attempt_number", nullable = false)
	private int attemptNumber;

	@Column(name = "time_spent_seconds")
	private Integer timeSpentSeconds;

	@Column(name = "tests_total", nullable = false)
	private int testsTotal;

	@Column(name = "tests_passed", nullable = false)
	private int testsPassed;

	@Column(name = "mandatory_tests_passed", nullable = false)
	private boolean mandatoryTestsPassed;

	@Column(name = "score_percent", nullable = false)
	private int scorePercent;

	@Column(name = "execution_status", nullable = false, length = 30)
	private String executionStatus;

	@Column(name = "test_results_json", columnDefinition = "TEXT")
	private String testResultsJson;

	@Column(name = "error_category", length = 60)
	private String errorCategory;

	@Column(name = "autonomy_percent")
	private Integer autonomyPercent;

	@Column(name = "correctness_percent")
	private Integer correctnessPercent;

	@Column(name = "concept_percent")
	private Integer conceptPercent;

	@Column(name = "quality_percent")
	private Integer qualityPercent;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected PracticalExerciseAttempt() {}

	public PracticalExerciseAttempt(PracticalExercise exercise, String answer, int selfRating, String feedback) {
		this(
				CurrentUserResolver.DEFAULT_USER_ID,
				exercise != null && exercise.getStudyBlock() != null ? exercise.getStudyBlock().getId() : null,
				exercise,
				exercise != null ? exercise.getTopicId() : null,
				null,
				exercise != null && exercise.getStudyMaterial() != null ? exercise.getStudyMaterial().getId() : null,
				answer,
				selfRating,
				feedback,
				ActivityOriginType.BLOCK,
				ExerciseEvaluationStatus.SELF_RATED,
				AssistanceLevel.INDEPENDENT,
				0,
				false,
				1,
				null,
				0,
				0,
				selfRating >= 4,
				Math.max(0, Math.min(100, selfRating * 20)),
				"NOT_EXECUTED",
				null,
				null);
	}

	public PracticalExerciseAttempt(
			String ownerId,
			UUID studyBlockId,
			PracticalExercise exercise,
			UUID topicId,
			UUID studySessionId,
			UUID sourceMaterialId,
			String answer,
			int selfRating,
			String feedback,
			ActivityOriginType originType,
			ExerciseEvaluationStatus evaluationStatus,
			AssistanceLevel assistanceLevel,
			int hintsUsedCount,
			boolean solutionRevealed,
			int attemptNumber,
			Integer timeSpentSeconds,
			int testsTotal,
			int testsPassed,
			boolean mandatoryTestsPassed,
			int scorePercent,
			String executionStatus,
			String testResultsJson,
			String errorCategory) {
		this.ownerId = (ownerId == null || ownerId.isBlank()) ? CurrentUserResolver.DEFAULT_USER_ID : ownerId;
		this.studyBlockId = studyBlockId != null
				? studyBlockId
				: (exercise != null && exercise.getStudyBlock() != null ? exercise.getStudyBlock().getId() : null);
		this.exercise = exercise;
		this.topicId = topicId;
		this.studySessionId = studySessionId;
		this.sourceMaterialId = sourceMaterialId;
		this.answer = answer;
		this.selfRating = Math.max(1, Math.min(5, selfRating));
		this.feedback = feedback;
		this.originType = originType == null ? ActivityOriginType.BLOCK : originType;
		this.evaluationStatus = evaluationStatus == null ? ExerciseEvaluationStatus.SELF_RATED : evaluationStatus;
		this.assistanceLevel = normalizeAssistanceLevel(assistanceLevel, hintsUsedCount, solutionRevealed);
		this.hintsUsedCount = Math.max(0, hintsUsedCount);
		this.solutionRevealed = solutionRevealed;
		this.attemptNumber = Math.max(1, attemptNumber);
		this.timeSpentSeconds = timeSpentSeconds;
		this.testsTotal = Math.max(0, testsTotal);
		this.testsPassed = Math.max(0, Math.min(this.testsTotal, testsPassed));
		this.mandatoryTestsPassed = mandatoryTestsPassed;
		this.scorePercent = Math.max(0, Math.min(100, scorePercent));
		this.executionStatus = (executionStatus == null || executionStatus.isBlank()) ? "NOT_EXECUTED" : executionStatus;
		this.testResultsJson = testResultsJson;
		this.errorCategory = errorCategory;
	}

	private static AssistanceLevel normalizeAssistanceLevel(
			AssistanceLevel raw, int hintsUsedCount, boolean solutionRevealed) {
		if (solutionRevealed || raw == AssistanceLevel.SOLUTION_REVEALED) {
			return AssistanceLevel.SOLUTION_REVEALED;
		}
		if (hintsUsedCount > 0 || raw == AssistanceLevel.WITH_HINTS) {
			return AssistanceLevel.WITH_HINTS;
		}
		if (raw == AssistanceLevel.NOT_COMPLETED) {
			return AssistanceLevel.NOT_COMPLETED;
		}
		return AssistanceLevel.INDEPENDENT;
	}

	/** Autonomia (0–100) calculada a partir das ajudas recebidas no chat do exercício. */
	public void recordAutonomy(int autonomyPercent) {
		this.autonomyPercent = Math.max(0, Math.min(100, autonomyPercent));
	}

	/** Notas parciais da correção automática (0–100 cada); {@code null} quando não houve execução. */
	public void recordBreakdown(Integer correctnessPercent, Integer conceptPercent, Integer qualityPercent) {
		this.correctnessPercent = correctnessPercent;
		this.conceptPercent = conceptPercent;
		this.qualityPercent = qualityPercent;
	}

	@PrePersist
	void onCreate() {
		if (id == null) id = UUID.randomUUID();
		if (ownerId == null || ownerId.isBlank()) ownerId = CurrentUserResolver.DEFAULT_USER_ID;
		if (studyBlockId == null && exercise != null && exercise.getStudyBlock() != null) {
			studyBlockId = exercise.getStudyBlock().getId();
		}
		if (originType == null) originType = ActivityOriginType.BLOCK;
		if (evaluationStatus == null) evaluationStatus = ExerciseEvaluationStatus.SELF_RATED;
		if (assistanceLevel == null || assistanceLevel == AssistanceLevel.NONE) {
			assistanceLevel = normalizeAssistanceLevel(AssistanceLevel.INDEPENDENT, hintsUsedCount, solutionRevealed);
		}
		if (attemptNumber < 1) attemptNumber = 1;
		if (executionStatus == null || executionStatus.isBlank()) executionStatus = "NOT_EXECUTED";
		if (createdAt == null) createdAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public String getOwnerId() { return ownerId; }
	public UUID getStudyBlockId() { return studyBlockId; }
	public PracticalExercise getExercise() { return exercise; }
	public UUID getTopicId() { return topicId; }
	public UUID getStudySessionId() { return studySessionId; }
	public UUID getSourceMaterialId() { return sourceMaterialId; }
	public String getAnswer() { return answer; }
	public int getSelfRating() { return selfRating; }
	public String getFeedback() { return feedback; }
	public ActivityOriginType getOriginType() { return originType; }
	public ExerciseEvaluationStatus getEvaluationStatus() { return evaluationStatus; }
	public AssistanceLevel getAssistanceLevel() { return assistanceLevel; }
	public int getHintsUsedCount() { return hintsUsedCount; }
	public boolean isSolutionRevealed() { return solutionRevealed; }
	public int getAttemptNumber() { return attemptNumber; }
	public Integer getTimeSpentSeconds() { return timeSpentSeconds; }
	public int getTestsTotal() { return testsTotal; }
	public int getTestsPassed() { return testsPassed; }
	public boolean isMandatoryTestsPassed() { return mandatoryTestsPassed; }
	public int getScorePercent() { return scorePercent; }
	public String getExecutionStatus() { return executionStatus; }
	public String getTestResultsJson() { return testResultsJson; }
	public String getErrorCategory() { return errorCategory; }
	public Integer getAutonomyPercent() { return autonomyPercent; }
	public Integer getCorrectnessPercent() { return correctnessPercent; }
	public Integer getConceptPercent() { return conceptPercent; }
	public Integer getQualityPercent() { return qualityPercent; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
