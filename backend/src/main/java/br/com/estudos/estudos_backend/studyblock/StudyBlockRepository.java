package br.com.estudos.estudos_backend.studyblock;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyBlockRepository extends JpaRepository<StudyBlock, UUID> {

	List<StudyBlock> findAllByStatusOrderByUpdatedAtDesc(StudyBlockStatus status);

	List<StudyBlock> findAllByOrderByUpdatedAtDesc();
}
