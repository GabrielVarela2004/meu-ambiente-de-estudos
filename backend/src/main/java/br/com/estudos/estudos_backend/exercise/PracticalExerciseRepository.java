package br.com.estudos.estudos_backend.exercise;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticalExerciseRepository extends JpaRepository<PracticalExercise, UUID> {
	List<PracticalExercise> findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(UUID blockId, PracticalExerciseStatus status);
	List<PracticalExercise> findAllByStudyBlockIdOrderByUpdatedAtDesc(UUID blockId);
}
