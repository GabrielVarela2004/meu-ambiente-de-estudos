package br.com.estudos.estudos_backend.question;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

import br.com.estudos.estudos_backend.studyblock.StudyBlockStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyQuestionRepository extends JpaRepository<StudyQuestion, UUID> {
	List<StudyQuestion> findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(UUID blockId, StudyQuestionStatus status);
	List<StudyQuestion> findAllByStudyBlockIdOrderByUpdatedAtDesc(UUID blockId);
	List<StudyQuestion> findAllByStudyBlockIdAndStatusAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
			UUID blockId, StudyQuestionStatus status, OffsetDateTime now);

	@Query("""
			SELECT q FROM StudyQuestion q
			JOIN FETCH q.studyBlock b
			WHERE q.status = :questionStatus
			  AND b.status = :blockStatus
			  AND q.nextReviewAt <= :now
			ORDER BY q.correctStreak ASC, q.nextReviewAt ASC
			""")
	List<StudyQuestion> findGlobalDueForReview(
			@Param("questionStatus") StudyQuestionStatus questionStatus,
			@Param("blockStatus") StudyBlockStatus blockStatus,
			@Param("now") OffsetDateTime now);
}
