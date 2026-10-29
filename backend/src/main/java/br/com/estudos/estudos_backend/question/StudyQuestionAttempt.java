package br.com.estudos.estudos_backend.question;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.estudos.estudos_backend.learning.ActivityOriginType;
import br.com.estudos.estudos_backend.learning.AssistanceLevel;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "study_question_attempts")
public class StudyQuestionAttempt {
	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, length = 120)
	private String ownerId;

	@Column(name = "study_block_id", nullable = false)
	private UUID studyBlockId;

	@Column(name = "question_id")
	private UUID questionId;

	@Column(name = "topic_id")
	private UUID topicId;

	@Column(name = "study_session_id")
	private UUID studySessionId;

	@Column(name = "source_material_id")
	private UUID sourceMaterialId;

	@Column(name = "option_id")
	private UUID optionId;

	@Column(name = "discursive_answer", length = 10000)
	private String discursiveAnswer;

	@Column(nullable = false)
	private boolean correct;

	@Enumerated(EnumType.STRING)
	@Column(name = "origin_type", nullable = false, length = 20)
	private ActivityOriginType originType;

	@Enumerated(EnumType.STRING)
	@Column(name = "evaluation_status", nullable = false, length = 20)
	private QuestionEvaluationStatus evaluationStatus;

	@Column(name = "score_percent", nullable = false)
	private int scorePercent;

	@Column(name = "time_spent_seconds")
	private Integer timeSpentSeconds;

	@Column(name = "attempt_number", nullable = false)
	private int attemptNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "help_level", nullable = false, length = 25)
	private AssistanceLevel helpLevel;

	@Column(name = "hints_used_count", nullable = false)
	private int hintsUsedCount;

	@Column(name = "evaluator_feedback", columnDefinition = "TEXT")
	private String evaluatorFeedback;

	@Column(name = "prompt_snapshot", columnDefinition = "TEXT")
	private String promptSnapshot;

	@Column(name = "user_answer_text", columnDefinition = "TEXT")
	private String userAnswerText;

	@Column(name = "expected_answer_snapshot", columnDefinition = "TEXT")
	private String expectedAnswerSnapshot;

	@Column(name = "explanation_snapshot", columnDefinition = "TEXT")
	private String explanationSnapshot;

	@Column(name = "study_block_title_snapshot")
	private String studyBlockTitleSnapshot;

	@Column(name = "topic_title_snapshot")
	private String topicTitleSnapshot;

	@Column(name = "difficulty_snapshot", length = 20)
	private String difficultySnapshot;

	@Column(name = "question_format_snapshot", length = 25)
	private String questionFormatSnapshot;

	@Column(name = "source_title_snapshot")
	private String sourceTitleSnapshot;

	@Column(name = "source_excerpt_snapshot", columnDefinition = "TEXT")
	private String sourceExcerptSnapshot;

	@Column(name = "answered_at", nullable = false)
	private OffsetDateTime answeredAt;

	protected StudyQuestionAttempt() {}

	public StudyQuestionAttempt(UUID studyBlockId, UUID questionId, UUID optionId, boolean correct) {
		this(studyBlockId, questionId, optionId, null, correct);
	}

	public StudyQuestionAttempt(UUID studyBlockId, UUID questionId, UUID optionId, String discursiveAnswer, boolean correct) {
		this(
				CurrentUserResolver.DEFAULT_USER_ID,
				studyBlockId,
				questionId,
				null,
				null,
				null,
				optionId,
				discursiveAnswer,
				correct,
				ActivityOriginType.BLOCK,
				correct ? QuestionEvaluationStatus.CORRECT : QuestionEvaluationStatus.INCORRECT,
				correct ? 100 : 0,
				null,
				1,
				AssistanceLevel.NONE,
				0,
				null);
	}

	public StudyQuestionAttempt(
			String ownerId,
			UUID studyBlockId,
			UUID questionId,
			UUID topicId,
			UUID studySessionId,
			UUID sourceMaterialId,
			UUID optionId,
			String discursiveAnswer,
			boolean correct,
			ActivityOriginType originType,
			QuestionEvaluationStatus evaluationStatus,
			int scorePercent,
			Integer timeSpentSeconds,
			int attemptNumber,
			AssistanceLevel helpLevel,
			int hintsUsedCount,
			String evaluatorFeedback) {
		this.ownerId = (ownerId == null || ownerId.isBlank()) ? CurrentUserResolver.DEFAULT_USER_ID : ownerId;
		this.studyBlockId = studyBlockId;
		this.questionId = questionId;
		this.topicId = topicId;
		this.studySessionId = studySessionId;
		this.sourceMaterialId = sourceMaterialId;
		this.optionId = optionId;
		this.discursiveAnswer = discursiveAnswer;
		this.correct = correct;
		this.originType = originType == null ? ActivityOriginType.BLOCK : originType;
		this.evaluationStatus = evaluationStatus == null
				? (correct ? QuestionEvaluationStatus.CORRECT : QuestionEvaluationStatus.INCORRECT)
				: evaluationStatus;
		this.scorePercent = Math.max(0, Math.min(100, scorePercent));
		this.timeSpentSeconds = timeSpentSeconds;
		this.attemptNumber = Math.max(1, attemptNumber);
		this.helpLevel = helpLevel == null ? AssistanceLevel.NONE : helpLevel;
		this.hintsUsedCount = Math.max(0, hintsUsedCount);
		this.evaluatorFeedback = evaluatorFeedback;
	}

	public void populateSnapshots(
			String promptSnapshot,
			String userAnswerText,
			String expectedAnswerSnapshot,
			String explanationSnapshot,
			String studyBlockTitleSnapshot,
			String topicTitleSnapshot,
			String difficultySnapshot,
			String questionFormatSnapshot,
			String sourceTitleSnapshot,
			String sourceExcerptSnapshot) {
		this.promptSnapshot = promptSnapshot;
		this.userAnswerText = userAnswerText;
		this.expectedAnswerSnapshot = expectedAnswerSnapshot;
		this.explanationSnapshot = explanationSnapshot;
		this.studyBlockTitleSnapshot = studyBlockTitleSnapshot;
		this.topicTitleSnapshot = topicTitleSnapshot;
		this.difficultySnapshot = difficultySnapshot;
		this.questionFormatSnapshot = questionFormatSnapshot;
		this.sourceTitleSnapshot = sourceTitleSnapshot;
		this.sourceExcerptSnapshot = sourceExcerptSnapshot;
	}

	@PrePersist
	void onCreate() {
		if (id == null) id = UUID.randomUUID();
		if (ownerId == null || ownerId.isBlank()) ownerId = CurrentUserResolver.DEFAULT_USER_ID;
		if (originType == null) originType = ActivityOriginType.BLOCK;
		if (evaluationStatus == null) {
			evaluationStatus = correct ? QuestionEvaluationStatus.CORRECT : QuestionEvaluationStatus.INCORRECT;
		}
		if (helpLevel == null) helpLevel = AssistanceLevel.NONE;
		if (attemptNumber < 1) attemptNumber = 1;
		if (answeredAt == null) answeredAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public String getOwnerId() { return ownerId; }
	public UUID getStudyBlockId() { return studyBlockId; }
	public UUID getQuestionId() { return questionId; }
	public UUID getTopicId() { return topicId; }
	public UUID getStudySessionId() { return studySessionId; }
	public UUID getSourceMaterialId() { return sourceMaterialId; }
	public UUID getOptionId() { return optionId; }
	public String getDiscursiveAnswer() { return discursiveAnswer; }
	public boolean isCorrect() { return correct; }
	public ActivityOriginType getOriginType() { return originType; }
	public QuestionEvaluationStatus getEvaluationStatus() { return evaluationStatus; }
	public int getScorePercent() { return scorePercent; }
	public Integer getTimeSpentSeconds() { return timeSpentSeconds; }
	public int getAttemptNumber() { return attemptNumber; }
	public AssistanceLevel getHelpLevel() { return helpLevel; }
	public int getHintsUsedCount() { return hintsUsedCount; }
	public String getEvaluatorFeedback() { return evaluatorFeedback; }
	public String getPromptSnapshot() { return promptSnapshot; }
	public String getUserAnswerText() { return userAnswerText; }
	public String getExpectedAnswerSnapshot() { return expectedAnswerSnapshot; }
	public String getExplanationSnapshot() { return explanationSnapshot; }
	public String getStudyBlockTitleSnapshot() { return studyBlockTitleSnapshot; }
	public String getTopicTitleSnapshot() { return topicTitleSnapshot; }
	public String getDifficultySnapshot() { return difficultySnapshot; }
	public String getQuestionFormatSnapshot() { return questionFormatSnapshot; }
	public String getSourceTitleSnapshot() { return sourceTitleSnapshot; }
	public String getSourceExcerptSnapshot() { return sourceExcerptSnapshot; }
	public OffsetDateTime getAnsweredAt() { return answeredAt; }
}
