package br.com.estudos.estudos_backend.exercise;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticalExerciseAttemptRepository extends JpaRepository<PracticalExerciseAttempt, UUID> {
	List<PracticalExerciseAttempt> findAllByExerciseIdOrderByCreatedAtDesc(UUID exerciseId);
	List<PracticalExerciseAttempt> findAllByStudyBlockIdOrderByCreatedAtDesc(UUID studyBlockId);
	List<PracticalExerciseAttempt> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);
	List<PracticalExerciseAttempt> findAllByStudyBlockIdInOrderByCreatedAtDesc(Collection<UUID> studyBlockIds);
	long countByOwnerIdAndExerciseId(String ownerId, UUID exerciseId);
}
