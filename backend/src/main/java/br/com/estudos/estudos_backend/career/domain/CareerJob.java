package br.com.estudos.estudos_backend.career.domain;

import java.math.BigDecimal;
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
@Table(name = "career_jobs")
public class CareerJob {

	@Id
	private UUID id;

	@Column(name = "source_id")
	private UUID sourceId;

	@Column(name = "source_name", nullable = false, length = 150)
	private String sourceName;

	@Column(name = "external_id", length = 250)
	private String externalId;

	@Column(nullable = false, unique = true, length = 1000)
	private String url;

	@Column(nullable = false, length = 300)
	private String title;

	@Column(nullable = false, length = 200)
	private String company;

	@Column(length = 250)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private JobModality modality;

	@Enumerated(EnumType.STRING)
	@Column(name = "experience_level", nullable = false, length = 50)
	private ExperienceLevel experienceLevel;

	@Column(name = "salary_min", precision = 12, scale = 2)
	private BigDecimal salaryMin;

	@Column(name = "salary_max", precision = 12, scale = 2)
	private BigDecimal salaryMax;

	@Column(name = "salary_currency", nullable = false, length = 10)
	private String salaryCurrency;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	@Column(name = "requirements_summary", columnDefinition = "TEXT")
	private String requirementsSummary;

	@Column(columnDefinition = "TEXT")
	private String tags;

	@Column(name = "published_at")
	private OffsetDateTime publishedAt;

	@Column(name = "discovered_at", nullable = false)
	private OffsetDateTime discoveredAt;

	@Column(name = "last_seen_at", nullable = false)
	private OffsetDateTime lastSeenAt;

	@Column(nullable = false)
	private boolean active;

	@Enumerated(EnumType.STRING)
	@Column(name = "application_status", nullable = false, length = 30)
	private JobApplicationStatus applicationStatus = JobApplicationStatus.DISCOVERED;

	@Column(name = "application_notes", columnDefinition = "TEXT")
	private String applicationNotes;

	@Column(name = "status_updated_at")
	private OffsetDateTime statusUpdatedAt;

	/** Bloco de estudo criado a partir desta vaga (Etapa G). */
	@Column(name = "study_block_id")
	private UUID studyBlockId;

	protected CareerJob() {
	}

	public CareerJob(
			UUID sourceId,
			String sourceName,
			String externalId,
			String url,
			String title,
			String company,
			String location,
			JobModality modality,
			ExperienceLevel experienceLevel,
			BigDecimal salaryMin,
			BigDecimal salaryMax,
			String salaryCurrency,
			String description,
			String requirementsSummary,
			String tags,
			OffsetDateTime publishedAt) {
		this.id = UUID.randomUUID();
		this.sourceId = sourceId;
		this.sourceName = sourceName;
		this.externalId = externalId;
		this.url = url;
		this.title = title;
		this.company = company;
		this.location = location != null ? location : "Remoto";
		this.modality = modality != null ? modality : JobModality.REMOTE;
		this.experienceLevel = experienceLevel != null ? experienceLevel : ExperienceLevel.JUNIOR;
		this.salaryMin = salaryMin;
		this.salaryMax = salaryMax;
		this.salaryCurrency = salaryCurrency != null ? salaryCurrency : "BRL";
		this.description = description != null ? description : "";
		this.requirementsSummary = requirementsSummary;
		this.tags = tags;
		this.publishedAt = publishedAt;
		this.active = true;
	}

	@PrePersist
	void onCreate() {
		if (this.id == null) {
			this.id = UUID.randomUUID();
		}
		OffsetDateTime now = OffsetDateTime.now();
		if (this.discoveredAt == null) {
			this.discoveredAt = now;
		}
		this.lastSeenAt = now;
	}

	// Getters and Setters
	public JobApplicationStatus getApplicationStatus() {
		return applicationStatus;
	}

	public void setApplicationStatus(JobApplicationStatus applicationStatus) {
		if (this.applicationStatus != applicationStatus) {
			this.statusUpdatedAt = OffsetDateTime.now();
		}
		this.applicationStatus = applicationStatus;
	}

	public String getApplicationNotes() {
		return applicationNotes;
	}

	public void setApplicationNotes(String applicationNotes) {
		this.applicationNotes = applicationNotes;
	}

	public OffsetDateTime getStatusUpdatedAt() {
		return statusUpdatedAt;
	}

	public UUID getStudyBlockId() {
		return studyBlockId;
	}

	public void setStudyBlockId(UUID studyBlockId) {
		this.studyBlockId = studyBlockId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSourceId() {
		return sourceId;
	}

	public String getSourceName() {
		return sourceName;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getUrl() {
		return url;
	}

	public String getTitle() {
		return title;
	}

	public String getCompany() {
		return company;
	}

	public String getLocation() {
		return location;
	}

	public JobModality getModality() {
		return modality;
	}

	public ExperienceLevel getExperienceLevel() {
		return experienceLevel;
	}

	public BigDecimal getSalaryMin() {
		return salaryMin;
	}

	public BigDecimal getSalaryMax() {
		return salaryMax;
	}

	public String getSalaryCurrency() {
		return salaryCurrency;
	}

	public String getDescription() {
		return description;
	}

	public String getRequirementsSummary() {
		return requirementsSummary;
	}

	public String getTags() {
		return tags;
	}

	public OffsetDateTime getPublishedAt() {
		return publishedAt;
	}

	public OffsetDateTime getDiscoveredAt() {
		return discoveredAt;
	}

	public OffsetDateTime getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(OffsetDateTime lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}
}
