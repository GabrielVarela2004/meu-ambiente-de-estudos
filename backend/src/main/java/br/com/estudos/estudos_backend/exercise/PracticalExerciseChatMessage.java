package br.com.estudos.estudos_backend.exercise;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Uma troca (pergunta do estudante + resposta da tutora) no chat de um exercício prático. */
@Entity
@Table(name = "practical_exercise_chat_messages")
public class PracticalExerciseChatMessage {
	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, length = 120)
	private String ownerId;

	@Column(name = "exercise_id", nullable = false)
	private UUID exerciseId;

	@Column(name = "step_index")
	private Integer stepIndex;

	@Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
	private String userMessage;

	@Column(name = "assistant_answer", nullable = false, columnDefinition = "TEXT")
	private String assistantAnswer;

	@Enumerated(EnumType.STRING)
	@Column(name = "help_level", nullable = false, length = 30)
	private ExerciseHelpLevel helpLevel;

	@Column(name = "provider_name", length = 60)
	private String providerName;

	@Column(name = "model_name", length = 120)
	private String modelName;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected PracticalExerciseChatMessage() {}

	public PracticalExerciseChatMessage(String ownerId, UUID exerciseId, Integer stepIndex, String userMessage,
			String assistantAnswer, ExerciseHelpLevel helpLevel, String providerName, String modelName) {
		this.ownerId = ownerId;
		this.exerciseId = exerciseId;
		this.stepIndex = stepIndex;
		this.userMessage = userMessage;
		this.assistantAnswer = assistantAnswer;
		this.helpLevel = helpLevel;
		this.providerName = providerName;
		this.modelName = modelName;
	}

	@PrePersist
	void onCreate() {
		if (id == null) id = UUID.randomUUID();
		if (createdAt == null) createdAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public String getOwnerId() { return ownerId; }
	public UUID getExerciseId() { return exerciseId; }
	public Integer getStepIndex() { return stepIndex; }
	public String getUserMessage() { return userMessage; }
	public String getAssistantAnswer() { return assistantAnswer; }
	public ExerciseHelpLevel getHelpLevel() { return helpLevel; }
	public String getProviderName() { return providerName; }
	public String getModelName() { return modelName; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
