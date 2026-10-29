package br.com.estudos.estudos_backend.question;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.studyblock.StudyBlockStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyQuestionRepository extends JpaRepository<StudyQuestion, UUID> {
	List<StudyQuestion> findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(UUID blockId, StudyQuestionStatus status);
	List<StudyQuestion> findAllByStudyBlockIdOrderByUpdatedAtDesc(UUID blockId);
	List<StudyQuestion> findAllByStudyBlockIdInAndStatusOrderByUpdatedAtDesc(
			Collection<UUID> blockIds, StudyQuestionStatus status);
	List<StudyQuestion> findAllByStudyBlockIdAndStatusAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
			UUID blockId, StudyQuestionStatus status, OffsetDateTime now);

	@Query("SELECT q FROM StudyQuestion q JOIN q.studyBlock b WHERE b.ownerId = :ownerId AND q.status = :status")
	List<StudyQuestion> findAllByOwnerIdAndStatus(@Param("ownerId") String ownerId, @Param("status") StudyQuestionStatus status);

	@Query("""
			SELECT q FROM StudyQuestion q
			JOIN FETCH q.studyBlock b
			WHERE q.status = :questionStatus
			  AND b.status = :blockStatus
			  AND b.ownerId = :ownerId
			  AND q.nextReviewAt <= :now
			ORDER BY
			  CASE 
			    WHEN q.difficulty = br.com.estudos.estudos_backend.question.StudyQuestionDifficulty.HARD AND q.correctStreak = 0 THEN 1
			    WHEN q.correctStreak = 0 THEN 2
			    WHEN q.difficulty = br.com.estudos.estudos_backend.question.StudyQuestionDifficulty.HARD THEN 3
			    WHEN q.difficulty = br.com.estudos.estudos_backend.question.StudyQuestionDifficulty.INTERMEDIATE THEN 4
			    ELSE 5
			  END ASC,
			  q.correctStreak ASC,
			  q.reviewIntervalDays ASC,
			  q.nextReviewAt ASC
			""")
	List<StudyQuestion> findGlobalDueForReviewByOwner(
			@Param("ownerId") String ownerId,
			@Param("questionStatus") StudyQuestionStatus questionStatus,
			@Param("blockStatus") StudyBlockStatus blockStatus,
			@Param("now") OffsetDateTime now);

	@Query("""
			SELECT q FROM StudyQuestion q
			JOIN FETCH q.studyBlock b
			WHERE q.status = :questionStatus
			  AND b.status = :blockStatus
			  AND q.nextReviewAt <= :now
			ORDER BY
			  CASE 
			    WHEN q.difficulty = br.com.estudos.estudos_backend.question.StudyQuestionDifficulty.HARD AND q.correctStreak = 0 THEN 1
			    WHEN q.correctStreak = 0 THEN 2
			    WHEN q.difficulty = br.com.estudos.estudos_backend.question.StudyQuestionDifficulty.HARD THEN 3
			    WHEN q.difficulty = br.com.estudos.estudos_backend.question.StudyQuestionDifficulty.INTERMEDIATE THEN 4
			    ELSE 5
			  END ASC,
			  q.correctStreak ASC,
			  q.reviewIntervalDays ASC,
			  q.nextReviewAt ASC
			""")
	List<StudyQuestion> findGlobalDueForReview(
			@Param("questionStatus") StudyQuestionStatus questionStatus,
			@Param("blockStatus") StudyBlockStatus blockStatus,
			@Param("now") OffsetDateTime now);
}
