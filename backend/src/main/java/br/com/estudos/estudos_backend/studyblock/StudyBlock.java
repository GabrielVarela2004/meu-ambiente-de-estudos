package br.com.estudos.estudos_backend.studyblock;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "study_blocks")
public class StudyBlock {

	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, length = 120)
	private String ownerId;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(length = 2000)
	private String description;

	@Column(name = "learning_objective", length = 2000)
	private String learningObjective;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StudyBlockStatus status;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected StudyBlock() {
	}

	public StudyBlock(String title, String description, String learningObjective) {
		this(CurrentUserResolver.DEFAULT_USER_ID, title, description, learningObjective);
	}

	public StudyBlock(String ownerId, String title, String description, String learningObjective) {
		this.ownerId = (ownerId == null || ownerId.isBlank()) ? CurrentUserResolver.DEFAULT_USER_ID : ownerId;
		this.title = title;
		this.description = description;
		this.learningObjective = learningObjective;
		this.status = StudyBlockStatus.ACTIVE;
	}

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (ownerId == null || ownerId.isBlank()) {
			ownerId = CurrentUserResolver.DEFAULT_USER_ID;
		}
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = OffsetDateTime.now();
	}

	public void update(String title, String description, String learningObjective) {
		this.title = title;
		this.description = description;
		this.learningObjective = learningObjective;
	}

	public void touch() {
		this.updatedAt = OffsetDateTime.now();
	}

	public void archive() {
		status = StudyBlockStatus.ARCHIVED;
	}

	public void unarchive() {
		status = StudyBlockStatus.ACTIVE;
		touch();
	}

	public UUID getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getLearningObjective() {
		return learningObjective;
	}

	public StudyBlockStatus getStatus() {
		return status;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
