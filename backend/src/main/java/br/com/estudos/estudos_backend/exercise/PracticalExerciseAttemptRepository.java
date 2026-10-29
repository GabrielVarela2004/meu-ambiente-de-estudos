package br.com.estudos.estudos_backend.exercise;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticalExerciseAttemptRepository extends JpaRepository<PracticalExerciseAttempt, UUID> {
	List<PracticalExerciseAttempt> findAllByExerciseIdOrderByCreatedAtDesc(UUID exerciseId);
}
