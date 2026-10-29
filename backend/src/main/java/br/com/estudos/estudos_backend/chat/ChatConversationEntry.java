package br.com.estudos.estudos_backend.chat;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.estudos.estudos_backend.learning.ActivityOriginType;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_messages")
public class ChatConversationEntry {

	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, length = 120)
	private String ownerId;

	@Column(name = "study_block_id")
	private UUID studyBlockId;

	@Enumerated(EnumType.STRING)
	@Column(name = "scope_type", nullable = false, length = 20)
	private ActivityOriginType scopeType;

	@Column(name = "scoped_block_ids", length = 2000)
	private String scopedBlockIds;

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
		this(CurrentUserResolver.DEFAULT_USER_ID, studyBlockId,
				studyBlockId == null ? ActivityOriginType.UNIVERSAL : ActivityOriginType.BLOCK,
				null, userMessage, assistantAnswer, providerName, modelName, referencedMaterials);
	}

	public ChatConversationEntry(String ownerId, UUID studyBlockId, ActivityOriginType scopeType,
			String scopedBlockIds, String userMessage, String assistantAnswer,
			String providerName, String modelName, String referencedMaterials) {
		this.ownerId = (ownerId == null || ownerId.isBlank()) ? CurrentUserResolver.DEFAULT_USER_ID : ownerId;
		this.studyBlockId = studyBlockId;
		this.scopeType = scopeType == null
				? (studyBlockId == null ? ActivityOriginType.UNIVERSAL : ActivityOriginType.BLOCK)
				: scopeType;
		this.scopedBlockIds = scopedBlockIds;
		this.userMessage = userMessage;
		this.assistantAnswer = assistantAnswer;
		this.providerName = providerName;
		this.modelName = modelName;
		this.referencedMaterials = referencedMaterials;
	}

	@PrePersist
	void onCreate() {
		if (id == null) id = UUID.randomUUID();
		if (ownerId == null || ownerId.isBlank()) ownerId = CurrentUserResolver.DEFAULT_USER_ID;
		if (scopeType == null) {
			scopeType = studyBlockId == null ? ActivityOriginType.UNIVERSAL : ActivityOriginType.BLOCK;
		}
		createdAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public String getOwnerId() { return ownerId; }
	public UUID getStudyBlockId() { return studyBlockId; }
	public ActivityOriginType getScopeType() { return scopeType; }
	public String getScopedBlockIds() { return scopedBlockIds; }
	public String getUserMessage() { return userMessage; }
	public String getAssistantAnswer() { return assistantAnswer; }
	public String getProviderName() { return providerName; }
	public String getModelName() { return modelName; }
	public String getReferencedMaterials() { return referencedMaterials; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
