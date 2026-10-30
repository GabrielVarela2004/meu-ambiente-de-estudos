package br.com.estudos.estudos_backend.studysession;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {
	List<StudySession> findAllByStudyBlockIdOrderByUpdatedAtDesc(UUID studyBlockId);

	List<StudySession> findAllByStudyBlockIdIn(java.util.Collection<UUID> studyBlockIds);
}
