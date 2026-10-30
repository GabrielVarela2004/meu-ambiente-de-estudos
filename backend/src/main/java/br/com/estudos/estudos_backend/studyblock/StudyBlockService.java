package br.com.estudos.estudos_backend.studyblock;

import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.common.exception.NotFoundException;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyBlockService {

	private final StudyBlockRepository repository;
	private final StudyMaterialRepository materialRepository;
	private final CurrentUserResolver currentUserResolver;

	public StudyBlockService(StudyBlockRepository repository, StudyMaterialRepository materialRepository) {
		this(repository, materialRepository, new CurrentUserResolver());
	}

	@Autowired
	public StudyBlockService(
			StudyBlockRepository repository,
			StudyMaterialRepository materialRepository,
			CurrentUserResolver currentUserResolver) {
		this.repository = repository;
		this.materialRepository = materialRepository;
		this.currentUserResolver = currentUserResolver;
	}

	@Transactional
	public StudyBlock create(String title, String description, String learningObjective) {
		String ownerId = currentUserResolver.currentUserId();
		return repository.save(new StudyBlock(ownerId, title, description, learningObjective));
	}

	@Transactional(readOnly = true)
	public List<StudyBlock> list(boolean includeArchived) {
		String ownerId = currentUserResolver.currentUserId();
		if (includeArchived) {
			return repository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
		}
		return repository.findAllByOwnerIdAndStatusOrderByUpdatedAtDesc(ownerId, StudyBlockStatus.ACTIVE);
	}

	@Transactional(readOnly = true)
	public StudyBlock get(UUID id) {
		String ownerId = currentUserResolver.currentUserId();
		return repository.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new NotFoundException("Bloco de estudo não encontrado."));
	}

	@Transactional(readOnly = true)
	public long countActiveSources(UUID studyBlockId) {
		return materialRepository.countByStudyBlockIdAndStatus(studyBlockId, StudyMaterialStatus.ACTIVE);
	}

	@Transactional
	public void touch(StudyBlock block) {
		block.touch();
		repository.save(block);
	}

	@Transactional
	public StudyBlock update(UUID id, String title, String description, String learningObjective) {
		StudyBlock block = get(id);
		if (block.getStatus() == StudyBlockStatus.ARCHIVED) {
			throw new BusinessRuleException("Blocos de estudo arquivados não podem ser editados.");
		}
		block.update(title, description, learningObjective);
		return repository.save(block);
	}

	@Transactional
	public void archive(UUID id) {
		StudyBlock block = get(id);
		block.archive();
		repository.save(block);
	}

	@Transactional
	public StudyBlock unarchive(UUID id) {
		StudyBlock block = get(id);
		if (block.getStatus() == StudyBlockStatus.ACTIVE) {
			throw new BusinessRuleException("O bloco de estudo já está ativo.");
		}
		block.unarchive();
		return repository.save(block);
	}
}
