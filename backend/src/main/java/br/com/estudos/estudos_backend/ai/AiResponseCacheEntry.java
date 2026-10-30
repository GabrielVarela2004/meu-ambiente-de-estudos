package br.com.estudos.estudos_backend.ai;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Resposta de IA guardada para não repetir a mesma chamada (ver {@link AiResponseCacheService}). */
@Entity
@Table(name = "ai_response_cache")
public class AiResponseCacheEntry {

	@Id
	@Column(name = "cache_key", length = 64)
	private String cacheKey;

	@Column(name = "owner_id", nullable = false, length = 120)
	private String ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private AiOperation operation;

	@Column(name = "provider_name", nullable = false, length = 60)
	private String providerName;

	@Column(length = 120)
	private String model;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String response;

	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;

	@Column(name = "hit_count", nullable = false)
	private int hitCount;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	protected AiResponseCacheEntry() {
	}

	public AiResponseCacheEntry(String cacheKey, String ownerId, AiOperation operation, String providerName,
			String model, String response, Integer inputTokens, Integer outputTokens, OffsetDateTime expiresAt) {
		this.cacheKey = cacheKey;
		this.ownerId = ownerId;
		this.operation = operation;
		this.providerName = providerName;
		this.model = model;
		this.response = response;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.createdAt = OffsetDateTime.now();
		this.expiresAt = expiresAt;
	}

	public void registerHit() {
		hitCount++;
	}

	public boolean isExpired() {
		return expiresAt.isBefore(OffsetDateTime.now());
	}

	/** Tokens economizados por todos os acertos desta entrada. */
	public long savedTokens() {
		long perCall = (inputTokens == null ? 0 : inputTokens) + (outputTokens == null ? 0 : outputTokens);
		return perCall * hitCount;
	}

	public String getCacheKey() { return cacheKey; }
	public String getOwnerId() { return ownerId; }
	public AiOperation getOperation() { return operation; }
	public String getProviderName() { return providerName; }
	public String getModel() { return model; }
	public String getResponse() { return response; }
	public Integer getInputTokens() { return inputTokens; }
	public Integer getOutputTokens() { return outputTokens; }
	public int getHitCount() { return hitCount; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getExpiresAt() { return expiresAt; }
}
