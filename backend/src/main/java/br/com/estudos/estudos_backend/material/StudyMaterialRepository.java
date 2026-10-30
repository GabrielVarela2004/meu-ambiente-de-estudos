package br.com.estudos.estudos_backend.material;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, UUID> {

	List<StudyMaterial> findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(
			UUID studyBlockId, StudyMaterialStatus status);

	List<StudyMaterial> findAllByStudyBlockIdInAndStatusOrderByUpdatedAtDesc(
			Collection<UUID> studyBlockIds, StudyMaterialStatus status);

	List<StudyMaterial> findAllByStudyBlockIdOrderByUpdatedAtDesc(UUID studyBlockId);

	long countByStudyBlockIdAndStatus(UUID studyBlockId, StudyMaterialStatus status);

	boolean existsByStudyBlockIdAndExternalVideoIdAndStatus(
			UUID studyBlockId, String externalVideoId, StudyMaterialStatus status);

	boolean existsByStudyBlockIdAndSourceUrlAndStatus(
			UUID studyBlockId, String sourceUrl, StudyMaterialStatus status);
}
