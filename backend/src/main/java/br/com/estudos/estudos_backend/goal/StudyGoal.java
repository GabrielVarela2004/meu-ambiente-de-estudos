package br.com.estudos.estudos_backend.goal;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import jakarta.persistence.*;

@Entity
@Table(name = "study_goals")
public class StudyGoal {
	@Id
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "study_block_id", nullable = false)
	private StudyBlock studyBlock;
	@Column(nullable = false, length = 160)
	private String title;
	@Column(length = 2000)
	private String description;
	@Column(name = "target_units", nullable = false)
	private int targetUnits;
	@Column(name = "completed_units", nullable = false)
	private int completedUnits;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StudyGoalStatus status;
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected StudyGoal() {}

	public StudyGoal(StudyBlock studyBlock, String title, String description, int targetUnits) {
		this.studyBlock = studyBlock;
		this.title = title;
		this.description = description;
		this.targetUnits = targetUnits;
		this.status = StudyGoalStatus.ACTIVE;
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

	public void addProgress(int units) {
		if (status != StudyGoalStatus.ACTIVE) throw new IllegalStateException("Only active goals can receive progress");
		if (units < 1 || completedUnits + units > targetUnits) {
			throw new IllegalArgumentException("Progress must stay within the goal target");
		}
		completedUnits += units;
		if (completedUnits == targetUnits) status = StudyGoalStatus.COMPLETED;
	}

	public void archive() {
		if (status == StudyGoalStatus.ARCHIVED) throw new IllegalStateException("Goal is already archived");
		status = StudyGoalStatus.ARCHIVED;
	}

	public UUID getId() { return id; }
	public StudyBlock getStudyBlock() { return studyBlock; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public int getTargetUnits() { return targetUnits; }
	public int getCompletedUnits() { return completedUnits; }
	public StudyGoalStatus getStatus() { return status; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
