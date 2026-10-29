package br.com.estudos.estudos_backend.material;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, UUID> {

	List<StudyMaterial> findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(
			UUID studyBlockId, StudyMaterialStatus status);

	List<StudyMaterial> findAllByStudyBlockIdOrderByUpdatedAtDesc(UUID studyBlockId);
}
