package br.com.estudos.estudos_backend.material;

import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockStatus;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;

@Service
public class StudyMaterialService {

	private final StudyMaterialRepository repository;
	private final StudyBlockService studyBlockService;
	private final MaterialFileStorage fileStorage;

	public StudyMaterialService(StudyMaterialRepository repository, StudyBlockService studyBlockService,
			MaterialFileStorage fileStorage) {
		this.repository = repository;
		this.studyBlockService = studyBlockService;
		this.fileStorage = fileStorage;
	}

	@Transactional
	public StudyMaterial importTextFile(UUID studyBlockId, MultipartFile file) {
		String content = fileStorage.readAndStore(file);
		String filename = file.getOriginalFilename() == null ? "Material importado" : file.getOriginalFilename();
		return create(studyBlockId, filename, content);
	}

	@Transactional
	public StudyMaterial create(UUID studyBlockId, String title, String content) {
		StudyBlock block = getActiveBlock(studyBlockId);
		return repository.save(new StudyMaterial(block, title, content));
	}

	@Transactional(readOnly = true)
	public List<StudyMaterial> list(UUID studyBlockId, boolean includeArchived) {
		studyBlockService.get(studyBlockId);
		if (includeArchived) {
			return repository.findAllByStudyBlockIdOrderByUpdatedAtDesc(studyBlockId);
		}
		return repository.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(
				studyBlockId, StudyMaterialStatus.ACTIVE);
	}

	@Transactional(readOnly = true)
	public StudyMaterial get(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study material not found"));
	}

	@Transactional
	public StudyMaterial update(UUID studyBlockId, UUID id, String title, String content) {
		StudyMaterial material = get(id);
		validateOwnership(material, studyBlockId);
		if (material.getStatus() == StudyMaterialStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived study materials cannot be edited");
		}
		material.update(title, content);
		return repository.save(material);
	}

	@Transactional
	public void archive(UUID studyBlockId, UUID id) {
		StudyMaterial material = get(id);
		validateOwnership(material, studyBlockId);
		material.archive();
		repository.save(material);
	}

	private StudyBlock getActiveBlock(UUID id) {
		StudyBlock block = studyBlockService.get(id);
		if (block.getStatus() == StudyBlockStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived study blocks cannot receive materials");
		}
		return block;
	}

	private void validateOwnership(StudyMaterial material, UUID studyBlockId) {
		if (!material.getStudyBlock().getId().equals(studyBlockId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study material not found");
		}
	}
}
