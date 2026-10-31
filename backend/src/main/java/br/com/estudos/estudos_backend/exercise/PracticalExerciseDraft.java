package br.com.estudos.estudos_backend.exercise;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Rascunho do projeto de um exercício (salvo automaticamente pelo editor), um por usuário e exercício. */
@Entity
@Table(name = "practical_exercise_drafts")
public class PracticalExerciseDraft {
	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, length = 120)
	private String ownerId;

	@Column(name = "exercise_id", nullable = false)
	private UUID exerciseId;

	@Column(name = "files_json", nullable = false, columnDefinition = "TEXT")
	private String filesJson;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected PracticalExerciseDraft() {}

	public PracticalExerciseDraft(String ownerId, UUID exerciseId, String filesJson) {
		this.ownerId = ownerId;
		this.exerciseId = exerciseId;
		this.filesJson = filesJson;
	}

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		updatedAt = OffsetDateTime.now();
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = OffsetDateTime.now();
	}

	public void replaceFiles(String filesJson) {
		this.filesJson = filesJson;
		this.updatedAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public String getOwnerId() { return ownerId; }
	public UUID getExerciseId() { return exerciseId; }
	public String getFilesJson() { return filesJson; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
