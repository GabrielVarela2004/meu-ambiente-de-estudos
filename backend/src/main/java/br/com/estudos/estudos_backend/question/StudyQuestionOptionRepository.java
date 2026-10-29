package br.com.estudos.estudos_backend.question;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyQuestionOptionRepository extends JpaRepository<StudyQuestionOption, UUID> {
	List<StudyQuestionOption> findAllByQuestionIdOrderByOrderAsc(UUID questionId);
}
