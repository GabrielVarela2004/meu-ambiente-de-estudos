package br.com.estudos.estudos_backend.knowledge;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeTopicRepository extends JpaRepository<KnowledgeTopic, UUID> {
	List<KnowledgeTopic> findAllByStudyBlockIdOrderByCreatedAtAsc(UUID blockId);
	List<KnowledgeTopic> findAllByStudyBlockIdAndStatusOrderByCreatedAtAsc(UUID blockId, KnowledgeTopicStatus status);
	boolean existsByParentTopicIdAndStatus(UUID parentTopicId, KnowledgeTopicStatus status);
}
