package br.com.estudos.estudos_backend.studysession;

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
@Table(name = "study_sessions")
public class StudySession {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "study_block_id", nullable = false)
	private StudyBlock studyBlock;

	@Column(name = "planned_minutes", nullable = false)
	private int plannedMinutes;

	@Column(length = 2000)
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StudySessionStatus status;

	@Column(name = "started_at")
	private OffsetDateTime startedAt;

	@Column(name = "ended_at")
	private OffsetDateTime endedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected StudySession() {
	}

	public StudySession(StudyBlock studyBlock, int plannedMinutes, String notes) {
		this.studyBlock = studyBlock;
		this.plannedMinutes = plannedMinutes;
		this.notes = notes;
		this.status = StudySessionStatus.PLANNED;
	}

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = OffsetDateTime.now();
	}

	public void start() {
		if (status != StudySessionStatus.PLANNED) {
			throw new IllegalStateException("Only planned sessions can be started");
		}
		status = StudySessionStatus.IN_PROGRESS;
		startedAt = OffsetDateTime.now();
	}

	public void complete() {
		if (status != StudySessionStatus.IN_PROGRESS) {
			throw new IllegalStateException("Only in-progress sessions can be completed");
		}
		status = StudySessionStatus.COMPLETED;
		endedAt = OffsetDateTime.now();
	}

	public void cancel() {
		if (status != StudySessionStatus.PLANNED && status != StudySessionStatus.IN_PROGRESS) {
			throw new IllegalStateException("Only open sessions can be cancelled");
		}
		status = StudySessionStatus.CANCELLED;
		endedAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public StudyBlock getStudyBlock() { return studyBlock; }
	public int getPlannedMinutes() { return plannedMinutes; }
	public String getNotes() { return notes; }
	public StudySessionStatus getStatus() { return status; }
	public OffsetDateTime getStartedAt() { return startedAt; }
	public OffsetDateTime getEndedAt() { return endedAt; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
