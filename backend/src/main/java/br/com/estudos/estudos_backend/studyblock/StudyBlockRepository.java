package br.com.estudos.estudos_backend.studyblock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyBlockRepository extends JpaRepository<StudyBlock, UUID> {

	List<StudyBlock> findAllByStatusOrderByUpdatedAtDesc(StudyBlockStatus status);

	List<StudyBlock> findAllByOrderByUpdatedAtDesc();

	List<StudyBlock> findAllByOwnerIdAndStatusOrderByUpdatedAtDesc(String ownerId, StudyBlockStatus status);

	List<StudyBlock> findAllByOwnerIdOrderByUpdatedAtDesc(String ownerId);

	Optional<StudyBlock> findByIdAndOwnerId(UUID id, String ownerId);

	boolean existsByIdAndOwnerId(UUID id, String ownerId);
}
