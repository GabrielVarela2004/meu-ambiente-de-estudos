package br.com.estudos.estudos_backend.chat;

import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.learning.ActivityOriginType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatConversationRepository extends JpaRepository<ChatConversationEntry, UUID> {
	List<ChatConversationEntry> findAllByStudyBlockIdOrderByCreatedAtDesc(UUID studyBlockId);

	List<ChatConversationEntry> findAllByOwnerIdAndStudyBlockIdOrderByCreatedAtDesc(
			String ownerId, UUID studyBlockId);

	List<ChatConversationEntry> findAllByOwnerIdAndScopeTypeOrderByCreatedAtDesc(
			String ownerId, ActivityOriginType scopeType);
}
