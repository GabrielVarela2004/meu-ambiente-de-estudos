package br.com.estudos.estudos_backend.goal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyGoalRepository extends JpaRepository<StudyGoal, UUID> {
	List<StudyGoal> findAllByStudyBlockIdOrderByUpdatedAtDesc(UUID blockId);
}
