package br.com.estudos.estudos_backend.exercise;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
import br.com.estudos.estudos_backend.studyblock.StudyBlockStatus;

@Service
public class PracticalExerciseService {
	private final PracticalExerciseRepository exerciseRepository;
	private final PracticalExerciseAttemptRepository attemptRepository;
	private final StudyBlockRepository blockRepository;

	public PracticalExerciseService(PracticalExerciseRepository exerciseRepository,
			PracticalExerciseAttemptRepository attemptRepository, StudyBlockRepository blockRepository) {
		this.exerciseRepository = exerciseRepository;
		this.attemptRepository = attemptRepository;
		this.blockRepository = blockRepository;
	}

	@Transactional
	public PracticalExercise create(UUID blockId, String title, String prompt, String guidance) {
		return exerciseRepository.save(new PracticalExercise(activeBlock(blockId), title, prompt, guidance));
	}

	@Transactional(readOnly = true)
	public List<PracticalExercise> list(UUID blockId, boolean includeArchived) {
		activeBlock(blockId);
		return includeArchived
				? exerciseRepository.findAllByStudyBlockIdOrderByUpdatedAtDesc(blockId)
				: exerciseRepository.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, PracticalExerciseStatus.ACTIVE);
	}

	@Transactional
	public void archive(UUID blockId, UUID exerciseId) {
		PracticalExercise exercise = getForBlock(blockId, exerciseId);
		exercise.archive();
	}

	@Transactional
	public PracticalExerciseAttempt submit(UUID blockId, UUID exerciseId, String answer, int rating, String feedback) {
		PracticalExercise exercise = getForBlock(blockId, exerciseId);
		return attemptRepository.save(new PracticalExerciseAttempt(exercise, answer, rating, feedback));
	}

	@Transactional(readOnly = true)
	public List<PracticalExerciseAttempt> attempts(UUID blockId, UUID exerciseId) {
		getForBlock(blockId, exerciseId);
		return attemptRepository.findAllByExerciseIdOrderByCreatedAtDesc(exerciseId);
	}

	private PracticalExercise getForBlock(UUID blockId, UUID exerciseId) {
		activeBlock(blockId);
		return exerciseRepository.findById(exerciseId)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found"));
	}

	private StudyBlock activeBlock(UUID id) {
		return blockRepository.findById(id).filter(block -> block.getStatus() == StudyBlockStatus.ACTIVE)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study block not found"));
	}
}
