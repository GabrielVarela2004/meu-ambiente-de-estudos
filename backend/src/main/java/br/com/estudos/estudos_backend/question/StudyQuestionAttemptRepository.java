package br.com.estudos.estudos_backend.question;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyQuestionAttemptRepository extends JpaRepository<StudyQuestionAttempt, UUID> {
	List<StudyQuestionAttempt> findAllByStudyBlockIdOrderByAnsweredAtDesc(UUID blockId);
	List<StudyQuestionAttempt> findAllByStudyBlockIdAndAnsweredAtGreaterThanEqualOrderByAnsweredAtDesc(
			UUID blockId, OffsetDateTime since);
}
