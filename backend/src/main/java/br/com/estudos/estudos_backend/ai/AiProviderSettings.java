package br.com.estudos.estudos_backend.ai;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_provider_settings")
public class AiProviderSettings {

	@Id
	@Column(name = "provider_id", length = 30)
	private String providerId;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(length = 120)
	private String model;

	@Column(name = "daily_request_limit")
	private Integer dailyRequestLimit;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected AiProviderSettings() {
	}

	public AiProviderSettings(String providerId) {
		this.providerId = providerId;
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = OffsetDateTime.now();
	}

	public void update(boolean enabled, String model, Integer dailyRequestLimit) {
		this.enabled = enabled;
		this.model = model == null || model.isBlank() ? null : model.trim();
		this.dailyRequestLimit = dailyRequestLimit == null || dailyRequestLimit < 1 ? null : dailyRequestLimit;
	}

	public String getProviderId() { return providerId; }
	public boolean isEnabled() { return enabled; }
	public String getModel() { return model; }
	public Integer getDailyRequestLimit() { return dailyRequestLimit; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
