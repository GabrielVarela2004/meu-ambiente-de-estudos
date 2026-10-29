package br.com.estudos.estudos_backend.goal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.studyblock.*;

@Service
public class StudyGoalService {
	private final StudyGoalRepository goalRepository;
	private final StudyBlockRepository blockRepository;

	public StudyGoalService(StudyGoalRepository goalRepository, StudyBlockRepository blockRepository) {
		this.goalRepository = goalRepository;
		this.blockRepository = blockRepository;
	}

	@Transactional
	public StudyGoal create(UUID blockId, String title, String description, int targetUnits) {
		return goalRepository.save(new StudyGoal(activeBlock(blockId), title, description, targetUnits));
	}

	@Transactional(readOnly = true)
	public List<StudyGoal> list(UUID blockId) {
		activeBlock(blockId);
		return goalRepository.findAllByStudyBlockIdOrderByUpdatedAtDesc(blockId);
	}

	@Transactional
	public StudyGoal addProgress(UUID blockId, UUID goalId, int units) {
		StudyGoal goal = getForBlock(blockId, goalId);
		try {
			goal.addProgress(units);
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
		}
		return goal;
	}

	@Transactional
	public void archive(UUID blockId, UUID goalId) {
		StudyGoal goal = getForBlock(blockId, goalId);
		try {
			goal.archive();
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
		}
	}

	private StudyGoal getForBlock(UUID blockId, UUID goalId) {
		activeBlock(blockId);
		return goalRepository.findById(goalId)
				.filter(goal -> goal.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study goal not found"));
	}

	private StudyBlock activeBlock(UUID id) {
		return blockRepository.findById(id).filter(block -> block.getStatus() == StudyBlockStatus.ACTIVE)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study block not found"));
	}
}
