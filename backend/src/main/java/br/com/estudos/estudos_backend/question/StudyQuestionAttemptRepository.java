package br.com.estudos.estudos_backend.question;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyQuestionAttemptRepository extends JpaRepository<StudyQuestionAttempt, UUID> {
	List<StudyQuestionAttempt> findAllByStudyBlockIdOrderByAnsweredAtDesc(UUID blockId);
	List<StudyQuestionAttempt> findAllByStudyBlockIdAndAnsweredAtGreaterThanEqualOrderByAnsweredAtDesc(
			UUID blockId, OffsetDateTime since);

	List<StudyQuestionAttempt> findAllByOwnerIdOrderByAnsweredAtDesc(String ownerId);
	List<StudyQuestionAttempt> findAllByOwnerIdAndStudyBlockIdOrderByAnsweredAtDesc(String ownerId, UUID blockId);
	List<StudyQuestionAttempt> findAllByStudyBlockIdInOrderByAnsweredAtDesc(Collection<UUID> blockIds);
	long countByOwnerIdAndQuestionId(String ownerId, UUID questionId);
}
