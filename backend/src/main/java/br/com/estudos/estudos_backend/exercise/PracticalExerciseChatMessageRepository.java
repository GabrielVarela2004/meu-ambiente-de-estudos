package br.com.estudos.estudos_backend.exercise;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticalExerciseChatMessageRepository extends JpaRepository<PracticalExerciseChatMessage, UUID> {
	List<PracticalExerciseChatMessage> findAllByOwnerIdAndExerciseIdOrderByCreatedAtAsc(String ownerId, UUID exerciseId);

	List<PracticalExerciseChatMessage> findAllByOwnerIdAndExerciseIdAndCreatedAtAfterOrderByCreatedAtAsc(
			String ownerId, UUID exerciseId, OffsetDateTime after);
}
