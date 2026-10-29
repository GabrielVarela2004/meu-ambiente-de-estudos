package br.com.estudos.estudos_backend.career.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "career_agent_executions")
public class CareerAgentExecution {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger_type", nullable = false, length = 50)
	private AgentTriggerType triggerType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private AgentExecutionStatus status;

	@Column(name = "started_at", nullable = false)
	private OffsetDateTime startedAt;

	@Column(name = "finished_at")
	private OffsetDateTime finishedAt;

	@Column(name = "jobs_found", nullable = false)
	private int jobsFound;

	@Column(name = "new_jobs_count", nullable = false)
	private int newJobsCount;

	@Column(name = "duration_seconds", nullable = false)
	private long durationSeconds;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@Column(name = "execution_details", columnDefinition = "TEXT")
	private String executionDetails;

	protected CareerAgentExecution() {
	}

	public CareerAgentExecution(AgentTriggerType triggerType) {
		this.id = UUID.randomUUID();
		this.triggerType = triggerType;
		this.status = AgentExecutionStatus.RUNNING;
		this.startedAt = OffsetDateTime.now();
		this.jobsFound = 0;
		this.newJobsCount = 0;
		this.durationSeconds = 0;
	}

	@PrePersist
	void onCreate() {
		if (this.id == null) {
			this.id = UUID.randomUUID();
		}
		if (this.startedAt == null) {
			this.startedAt = OffsetDateTime.now();
		}
	}

	public void complete(int jobsFound, int newJobsCount, String details) {
		this.status = AgentExecutionStatus.COMPLETED;
		this.finishedAt = OffsetDateTime.now();
		this.jobsFound = jobsFound;
		this.newJobsCount = newJobsCount;
		this.executionDetails = details;
		this.durationSeconds = java.time.Duration.between(startedAt, finishedAt).toSeconds();
	}

	public void markTimedOut(int jobsFound, int newJobsCount, String details) {
		this.status = AgentExecutionStatus.TIMED_OUT;
		this.finishedAt = OffsetDateTime.now();
		this.jobsFound = jobsFound;
		this.newJobsCount = newJobsCount;
		this.executionDetails = details;
		this.durationSeconds = java.time.Duration.between(startedAt, finishedAt).toSeconds();
	}

	public void fail(String errorMessage, String details) {
		this.status = AgentExecutionStatus.FAILED;
		this.finishedAt = OffsetDateTime.now();
		this.errorMessage = errorMessage;
		this.executionDetails = details;
		this.durationSeconds = java.time.Duration.between(startedAt, finishedAt).toSeconds();
	}

	// Getters
	public UUID getId() {
		return id;
	}

	public AgentTriggerType getTriggerType() {
		return triggerType;
	}

	public AgentExecutionStatus getStatus() {
		return status;
	}

	public OffsetDateTime getStartedAt() {
		return startedAt;
	}

	public OffsetDateTime getFinishedAt() {
		return finishedAt;
	}

	public int getJobsFound() {
		return jobsFound;
	}

	public int getNewJobsCount() {
		return newJobsCount;
	}

	public long getDurationSeconds() {
		return durationSeconds;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public String getExecutionDetails() {
		return executionDetails;
	}
}
