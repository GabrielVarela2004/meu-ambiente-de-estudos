package br.com.estudos.estudos_backend.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorMessage;
import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorRequest;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;

/**
 * Cache das respostas de IA das operações em que a mesma entrada deve dar a mesma saída
 * ({@link AiOperation#cacheable()}). A chave inclui usuário, operação, provedor, modelo e o pedido inteiro, então
 * trocar de modelo ou mudar o conteúdo gera uma nova chamada. Falhas aqui nunca derrubam a chamada de IA.
 */
@Service
public class AiResponseCacheService {

	private static final Logger log = LoggerFactory.getLogger(AiResponseCacheService.class);

	private final AiResponseCacheRepository repository;
	private final CurrentUserResolver currentUserResolver;
	private final boolean enabled;
	private final Duration ttl;

	public AiResponseCacheService(AiResponseCacheRepository repository, CurrentUserResolver currentUserResolver,
			@Value("${app.ai.cache.enabled:true}") boolean enabled,
			@Value("${app.ai.cache.ttl-hours:168}") long ttlHours) {
		this.repository = repository;
		this.currentUserResolver = currentUserResolver;
		this.enabled = enabled;
		this.ttl = Duration.ofHours(Math.max(1, ttlHours));
	}

	public boolean isEnabled() {
		return enabled;
	}

	/** Chave do pedido para o usuário atual. */
	public String key(AiOperation operation, String providerId, String model, AiTutorRequest request) {
		StringBuilder sb = new StringBuilder()
				.append(currentUserResolver.currentUserId()).append('\u0000')
				.append(operation).append('\u0000')
				.append(providerId).append('\u0000')
				.append(model).append('\u0000')
				.append(request.systemInstruction()).append('\u0000')
				.append(request.userPrompt()).append('\u0000')
				.append(request.jsonObjectMode()).append('\u0000')
				.append(request.maxOutputTokens()).append('\u0000')
				.append(request.temperature());
		for (AiTutorMessage message : request.conversationHistory()) {
			sb.append('\u0000').append(message.role()).append('\u0001').append(message.content());
		}
		return sha256(sb.toString());
	}

	/** Resposta guardada e ainda válida; conta o acerto. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<AiResponseCacheEntry> lookup(String key) {
		try {
			Optional<AiResponseCacheEntry> entry = repository.findById(key);
			if (entry.isEmpty()) {
				return Optional.empty();
			}
			if (entry.get().isExpired()) {
				repository.delete(entry.get());
				return Optional.empty();
			}
			entry.get().registerHit();
			return Optional.of(repository.save(entry.get()));
		} catch (RuntimeException e) {
			log.warn("Não foi possível ler o cache de IA: {}", e.getMessage());
			return Optional.empty();
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void store(String key, AiOperation operation, String providerName, String model, String response,
			Integer inputTokens, Integer outputTokens) {
		if (response == null || response.isBlank()) {
			return;
		}
		try {
			OffsetDateTime now = OffsetDateTime.now();
			repository.deleteByExpiresAtBefore(now);
			repository.save(new AiResponseCacheEntry(key, currentUserResolver.currentUserId(), operation, providerName,
					model, response, inputTokens, outputTokens, now.plus(ttl)));
		} catch (RuntimeException e) {
			log.warn("Não foi possível gravar no cache de IA: {}", e.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public CacheStats stats() {
		List<AiResponseCacheEntry> entries = repository.findAllByOwnerId(currentUserResolver.currentUserId());
		long hits = entries.stream().mapToLong(AiResponseCacheEntry::getHitCount).sum();
		long savedTokens = entries.stream().mapToLong(AiResponseCacheEntry::savedTokens).sum();
		return new CacheStats(enabled, ttl.toHours(), entries.size(), hits, savedTokens);
	}

	@Transactional
	public long clear() {
		return repository.deleteByOwnerId(currentUserResolver.currentUserId());
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponível", e);
		}
	}

	/** Resumo do cache do usuário: entradas, chamadas evitadas e tokens economizados. */
	public record CacheStats(boolean enabled, long ttlHours, long entries, long hits, long savedTokens) {}
}
