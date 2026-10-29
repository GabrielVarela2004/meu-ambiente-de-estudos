package br.com.estudos.estudos_backend.chat;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_messages")
public class ChatConversationEntry {

	@Id
	private UUID id;

	@Column(name = "study_block_id")
	private UUID studyBlockId;

	@Column(name = "user_message", nullable = false, length = 10000)
	private String userMessage;

	@Column(name = "assistant_answer", nullable = false, columnDefinition = "TEXT")
	private String assistantAnswer;

	@Column(name = "provider_name", nullable = false, length = 60)
	private String providerName;

	@Column(name = "model_name", nullable = false, length = 120)
	private String modelName;

	@Column(name = "referenced_materials", length = 2000)
	private String referencedMaterials;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected ChatConversationEntry() {}

	public ChatConversationEntry(UUID studyBlockId, String userMessage, String assistantAnswer,
			String providerName, String modelName, String referencedMaterials) {
		this.studyBlockId = studyBlockId;
		this.userMessage = userMessage;
		this.assistantAnswer = assistantAnswer;
		this.providerName = providerName;
		this.modelName = modelName;
		this.referencedMaterials = referencedMaterials;
	}

	@PrePersist
	void onCreate() {
		if (id == null) id = UUID.randomUUID();
		createdAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public UUID getStudyBlockId() { return studyBlockId; }
	public String getUserMessage() { return userMessage; }
	public String getAssistantAnswer() { return assistantAnswer; }
	public String getProviderName() { return providerName; }
	public String getModelName() { return modelName; }
	public String getReferencedMaterials() { return referencedMaterials; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
