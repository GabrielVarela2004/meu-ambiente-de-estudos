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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "career_preferences")
public class CareerPreferences {

	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, unique = true, length = 120)
	private String ownerId;

	@Column(name = "target_roles", nullable = false, columnDefinition = "TEXT")
	private String targetRoles;

	@Column(name = "experience_levels", nullable = false, columnDefinition = "TEXT")
	private String experienceLevels;

	@Column(name = "technologies", nullable = false, columnDefinition = "TEXT")
	private String technologies;

	@Column(name = "mandatory_keywords", columnDefinition = "TEXT")
	private String mandatoryKeywords;

	@Column(name = "preferred_keywords", columnDefinition = "TEXT")
	private String preferredKeywords;

	@Column(length = 200)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private JobModality modality;

	@Column(name = "min_salary", precision = 12, scale = 2)
	private BigDecimal minSalary;

	@Column(name = "search_days", nullable = false, length = 100)
	private String searchDays;

	@Column(name = "search_time_hour", nullable = false)
	private int searchTimeHour;

	@Column(name = "search_time_minute", nullable = false)
	private int searchTimeMinute;

	@Column(name = "max_round_minutes", nullable = false)
	private int maxRoundMinutes;

	@Column(name = "agent_enabled", nullable = false)
	private boolean agentEnabled;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected CareerPreferences() {
	}

	public CareerPreferences(String ownerId) {
		this.id = UUID.randomUUID();
		this.ownerId = ownerId;
		this.targetRoles = "Desenvolvedor Java, Backend Developer, Fullstack, Engenheiro de Software";
		this.experienceLevels = "JUNIOR,INTERNSHIP,TRAINEE";
		this.technologies = "Java, Spring Boot, PostgreSQL, React, TypeScript, Docker, Git";
		this.mandatoryKeywords = "";
		this.preferredKeywords = "Spring, Clean Architecture, REST API, JUnit";
		this.location = "Brasil / Remoto";
		this.modality = JobModality.ANY;
		this.minSalary = null;
		this.searchDays = "MON,TUE,WED,THU,FRI";
		this.searchTimeHour = 9;
		this.searchTimeMinute = 0;
		this.maxRoundMinutes = 30;
		this.agentEnabled = true;
	}

	@PrePersist
	void onCreate() {
		if (this.id == null) {
			this.id = UUID.randomUUID();
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = OffsetDateTime.now();
	}

	// Getters and Setters
	public UUID getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	public String getTargetRoles() {
		return targetRoles;
	}

	public void setTargetRoles(String targetRoles) {
		this.targetRoles = targetRoles;
	}

	public String getExperienceLevels() {
		return experienceLevels;
	}

	public void setExperienceLevels(String experienceLevels) {
		this.experienceLevels = experienceLevels;
	}

	public String getTechnologies() {
		return technologies;
	}

	public void setTechnologies(String technologies) {
		this.technologies = technologies;
	}

	public String getMandatoryKeywords() {
		return mandatoryKeywords;
	}

	public void setMandatoryKeywords(String mandatoryKeywords) {
		this.mandatoryKeywords = mandatoryKeywords;
	}

	public String getPreferredKeywords() {
		return preferredKeywords;
	}

	public void setPreferredKeywords(String preferredKeywords) {
		this.preferredKeywords = preferredKeywords;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public JobModality getModality() {
		return modality;
	}

	public void setModality(JobModality modality) {
		this.modality = modality;
	}

	public BigDecimal getMinSalary() {
		return minSalary;
	}

	public void setMinSalary(BigDecimal minSalary) {
		this.minSalary = minSalary;
	}

	public String getSearchDays() {
		return searchDays;
	}

	public void setSearchDays(String searchDays) {
		this.searchDays = searchDays;
	}

	public int getSearchTimeHour() {
		return searchTimeHour;
	}

	public void setSearchTimeHour(int searchTimeHour) {
		this.searchTimeHour = searchTimeHour;
	}

	public int getSearchTimeMinute() {
		return searchTimeMinute;
	}

	public void setSearchTimeMinute(int searchTimeMinute) {
		this.searchTimeMinute = searchTimeMinute;
	}

	public int getMaxRoundMinutes() {
		return maxRoundMinutes;
	}

	public void setMaxRoundMinutes(int maxRoundMinutes) {
		this.maxRoundMinutes = maxRoundMinutes;
	}

	public boolean isAgentEnabled() {
		return agentEnabled;
	}

	public void setAgentEnabled(boolean agentEnabled) {
		this.agentEnabled = agentEnabled;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
