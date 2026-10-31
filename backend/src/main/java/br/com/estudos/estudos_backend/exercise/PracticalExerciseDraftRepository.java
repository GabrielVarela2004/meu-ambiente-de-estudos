package br.com.estudos.estudos_backend.exercise;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticalExerciseDraftRepository extends JpaRepository<PracticalExerciseDraft, UUID> {

	Optional<PracticalExerciseDraft> findByOwnerIdAndExerciseId(String ownerId, UUID exerciseId);

	void deleteByOwnerIdAndExerciseId(String ownerId, UUID exerciseId);
}
