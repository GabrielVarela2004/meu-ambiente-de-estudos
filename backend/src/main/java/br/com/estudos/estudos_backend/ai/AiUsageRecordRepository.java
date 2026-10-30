package br.com.estudos.estudos_backend.ai;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageRecordRepository extends JpaRepository<AiUsageRecord, UUID> {

	long countByProviderIdAndCreatedAtGreaterThanEqual(String providerId, OffsetDateTime since);

	List<AiUsageRecord> findAllByCreatedAtGreaterThanEqual(OffsetDateTime since);
}
