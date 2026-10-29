package br.com.estudos.estudos_backend.exercise;

import java.time.OffsetDateTime;
import java.util.UUID;

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

	@Column(nullable = false, length = 160)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String prompt;

	@Column(columnDefinition = "TEXT")
	private String guidance;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PracticalExerciseStatus status;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected PracticalExercise() {}

	public PracticalExercise(StudyBlock studyBlock, String title, String prompt, String guidance) {
		this.studyBlock = studyBlock;
		this.title = title;
		this.prompt = prompt;
		this.guidance = guidance;
		this.status = PracticalExerciseStatus.ACTIVE;
	}

	@PrePersist
	void onCreate() {
		if (id == null) id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() { updatedAt = OffsetDateTime.now(); }

	public void archive() { status = PracticalExerciseStatus.ARCHIVED; }
	public UUID getId() { return id; }
	public StudyBlock getStudyBlock() { return studyBlock; }
	public String getTitle() { return title; }
	public String getPrompt() { return prompt; }
	public String getGuidance() { return guidance; }
	public PracticalExerciseStatus getStatus() { return status; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
