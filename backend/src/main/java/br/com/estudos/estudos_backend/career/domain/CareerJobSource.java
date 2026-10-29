package br.com.estudos.estudos_backend.career.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "career_job_sources")
public class CareerJobSource {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 150)
	private String name;

	@Column(name = "integration_type", nullable = false, length = 50)
	private String integrationType;

	@Column(name = "base_url", nullable = false, length = 500)
	private String baseUrl;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "last_checked_at")
	private OffsetDateTime lastCheckedAt;

	@Column(name = "last_status", nullable = false, length = 50)
	private String lastStatus;

	@Column(name = "last_error", columnDefinition = "TEXT")
	private String lastError;

	@Column(name = "rate_limit_delay_ms", nullable = false)
	private long rateLimitDelayMs;

	@Column(name = "requires_auth", nullable = false)
	private boolean requiresAuth;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected CareerJobSource() {
	}

	public CareerJobSource(String name, String integrationType, String baseUrl, long rateLimitDelayMs, boolean requiresAuth) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.integrationType = integrationType;
		this.baseUrl = baseUrl;
		this.enabled = true;
		this.lastStatus = "UNTESTED";
		this.rateLimitDelayMs = rateLimitDelayMs;
		this.requiresAuth = requiresAuth;
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getIntegrationType() {
		return integrationType;
	}

	public void setIntegrationType(String integrationType) {
		this.integrationType = integrationType;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public OffsetDateTime getLastCheckedAt() {
		return lastCheckedAt;
	}

	public void setLastCheckedAt(OffsetDateTime lastCheckedAt) {
		this.lastCheckedAt = lastCheckedAt;
	}

	public String getLastStatus() {
		return lastStatus;
	}

	public void setLastStatus(String lastStatus) {
		this.lastStatus = lastStatus;
	}

	public String getLastError() {
		return lastError;
	}

	public void setLastError(String lastError) {
		this.lastError = lastError;
	}

	public long getRateLimitDelayMs() {
		return rateLimitDelayMs;
	}

	public void setRateLimitDelayMs(long rateLimitDelayMs) {
		this.rateLimitDelayMs = rateLimitDelayMs;
	}

	public boolean isRequiresAuth() {
		return requiresAuth;
	}

	public void setRequiresAuth(boolean requiresAuth) {
		this.requiresAuth = requiresAuth;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
