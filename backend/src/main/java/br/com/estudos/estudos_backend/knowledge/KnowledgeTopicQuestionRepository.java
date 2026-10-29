package br.com.estudos.estudos_backend.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeTopicQuestionRepository
		extends JpaRepository<KnowledgeTopicQuestion, KnowledgeTopicQuestion.Key> {
	List<KnowledgeTopicQuestion> findAllByTopicIdIn(Collection<UUID> topicIds);
	List<KnowledgeTopicQuestion> findAllByQuestionId(UUID questionId);
}
