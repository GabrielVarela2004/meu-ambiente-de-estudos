package br.com.estudos.estudos_backend.ai;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Uma chamada a um provedor de IA (sucesso ou falha), usada no controle de consumo. */
@Entity
@Table(name = "ai_usage_records")
public class AiUsageRecord {

	@Id
	private UUID id;

	@Column(name = "provider_id", nullable = false, length = 30)
	private String providerId;

	@Column(length = 120)
	private String model;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private AiOperation operation;

	@Column(nullable = false)
	private boolean success;

	@Column(name = "http_status")
	private Integer httpStatus;

	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;

	@Column(name = "latency_ms", nullable = false)
	private int latencyMs;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected AiUsageRecord() {
	}

	public AiUsageRecord(String providerId, String model, AiOperation operation, boolean success,
			Integer httpStatus, Integer inputTokens, Integer outputTokens, int latencyMs) {
		this.id = UUID.randomUUID();
		this.providerId = providerId;
		this.model = model;
		this.operation = operation;
		this.success = success;
		this.httpStatus = httpStatus;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.latencyMs = latencyMs;
		this.createdAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public String getProviderId() { return providerId; }
	public String getModel() { return model; }
	public AiOperation getOperation() { return operation; }
	public boolean isSuccess() { return success; }
	public Integer getHttpStatus() { return httpStatus; }
	public Integer getInputTokens() { return inputTokens; }
	public Integer getOutputTokens() { return outputTokens; }
	public int getLatencyMs() { return latencyMs; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
