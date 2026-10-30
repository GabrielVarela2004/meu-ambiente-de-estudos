package br.com.estudos.estudos_backend.ai;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_operation_routes")
public class AiOperationRoute {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(length = 40)
	private AiOperation operation;

	@Column(name = "provider_id", nullable = false, length = 30)
	private String providerId;

	@Column(name = "fallback_enabled", nullable = false)
	private boolean fallbackEnabled = true;

	/** Modelo só para esta operação, no provedor da rota; null = modelo do provedor. */
	@Column(length = 120)
	private String model;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected AiOperationRoute() {
	}

	public AiOperationRoute(AiOperation operation, String providerId, boolean fallbackEnabled) {
		this.operation = operation;
		this.providerId = providerId;
		this.fallbackEnabled = fallbackEnabled;
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = OffsetDateTime.now();
	}

	public void update(String providerId, boolean fallbackEnabled) {
		this.providerId = providerId;
		this.fallbackEnabled = fallbackEnabled;
	}

	public void update(String providerId, boolean fallbackEnabled, String model) {
		update(providerId, fallbackEnabled);
		this.model = model == null || model.isBlank() ? null : model.strip();
	}

	public AiOperation getOperation() { return operation; }
	public String getProviderId() { return providerId; }
	public String getModel() { return model; }
	public boolean isFallbackEnabled() { return fallbackEnabled; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
