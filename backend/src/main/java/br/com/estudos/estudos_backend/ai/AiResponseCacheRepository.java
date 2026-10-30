package br.com.estudos.estudos_backend.ai;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiResponseCacheRepository extends JpaRepository<AiResponseCacheEntry, String> {

	List<AiResponseCacheEntry> findAllByOwnerId(String ownerId);

	long deleteByOwnerId(String ownerId);

	long deleteByExpiresAtBefore(OffsetDateTime moment);
}
