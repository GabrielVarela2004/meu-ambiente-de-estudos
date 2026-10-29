package br.com.estudos.estudos_backend.studyblock;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyBlockService {

	private final StudyBlockRepository repository;

	public StudyBlockService(StudyBlockRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public StudyBlock create(String title, String description, String learningObjective) {
		return repository.save(new StudyBlock(title, description, learningObjective));
	}

	@Transactional(readOnly = true)
	public List<StudyBlock> list(boolean includeArchived) {
		if (includeArchived) {
			return repository.findAllByOrderByUpdatedAtDesc();
		}
		return repository.findAllByStatusOrderByUpdatedAtDesc(StudyBlockStatus.ACTIVE);
	}

	@Transactional(readOnly = true)
	public StudyBlock get(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study block not found"));
	}

	@Transactional
	public StudyBlock update(UUID id, String title, String description, String learningObjective) {
		StudyBlock block = get(id);
		if (block.getStatus() == StudyBlockStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived study blocks cannot be edited");
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
}
