package br.com.estudos.estudos_backend.knowledge;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicReviewDecisionRepository extends JpaRepository<TopicReviewDecision, java.util.UUID> {

	List<TopicReviewDecision> findTop40ByOwnerIdAndActionOrderByCreatedAtDesc(String ownerId, TopicReviewState action);

	List<TopicReviewDecision> findAllByOwnerIdAndReviewRound(String ownerId, int reviewRound);
}
