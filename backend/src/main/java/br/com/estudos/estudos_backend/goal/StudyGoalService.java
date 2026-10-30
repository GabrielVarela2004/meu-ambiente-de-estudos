package br.com.estudos.estudos_backend.goal;

import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.common.exception.InvalidInputException;
import br.com.estudos.estudos_backend.common.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.*;

@Service
public class StudyGoalService {
	private final StudyGoalRepository goalRepository;
	private final StudyBlockRepository blockRepository;
	private final CurrentUserResolver currentUserResolver;

	public StudyGoalService(StudyGoalRepository goalRepository, StudyBlockRepository blockRepository) {
		this(goalRepository, blockRepository, new CurrentUserResolver());
	}

	@Autowired
	public StudyGoalService(
			StudyGoalRepository goalRepository,
			StudyBlockRepository blockRepository,
			CurrentUserResolver currentUserResolver) {
		this.goalRepository = goalRepository;
		this.blockRepository = blockRepository;
		this.currentUserResolver = currentUserResolver;
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
			throw new InvalidInputException(exception.getMessage());
		} catch (IllegalStateException exception) {
			throw new BusinessRuleException(exception.getMessage());
		}
		return goal;
	}

	@Transactional
	public void archive(UUID blockId, UUID goalId) {
		StudyGoal goal = getForBlock(blockId, goalId);
		try {
			goal.archive();
		} catch (IllegalStateException exception) {
			throw new BusinessRuleException(exception.getMessage());
		}
	}

	private StudyGoal getForBlock(UUID blockId, UUID goalId) {
		activeBlock(blockId);
		return goalRepository.findById(goalId)
				.filter(goal -> goal.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new NotFoundException("Meta de estudo não encontrada."));
	}

	private StudyBlock activeBlock(UUID id) {
		String ownerId = currentUserResolver.currentUserId();
		return blockRepository.findByIdAndOwnerId(id, ownerId)
				.filter(block -> block.getStatus() == StudyBlockStatus.ACTIVE)
				.orElseThrow(() -> new NotFoundException("Bloco de estudo não encontrado."));
	}
}
