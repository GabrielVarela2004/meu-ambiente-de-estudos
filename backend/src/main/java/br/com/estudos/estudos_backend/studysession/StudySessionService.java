package br.com.estudos.estudos_backend.studysession;

import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.common.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
import br.com.estudos.estudos_backend.studyblock.StudyBlockStatus;

@Service
public class StudySessionService {
	private final StudySessionRepository sessionRepository;
	private final StudyBlockRepository blockRepository;
	private final CurrentUserResolver currentUserResolver;

	public StudySessionService(StudySessionRepository sessionRepository, StudyBlockRepository blockRepository) {
		this(sessionRepository, blockRepository, new CurrentUserResolver());
	}

	@Autowired
	public StudySessionService(
			StudySessionRepository sessionRepository,
			StudyBlockRepository blockRepository,
			CurrentUserResolver currentUserResolver) {
		this.sessionRepository = sessionRepository;
		this.blockRepository = blockRepository;
		this.currentUserResolver = currentUserResolver;
	}

	@Transactional
	public StudySession create(UUID blockId, int plannedMinutes, String notes) {
		StudyBlock block = activeBlock(blockId);
		return sessionRepository.save(new StudySession(block, plannedMinutes, notes));
	}

	@Transactional(readOnly = true)
	public List<StudySession> list(UUID blockId) {
		activeBlock(blockId);
		return sessionRepository.findAllByStudyBlockIdOrderByUpdatedAtDesc(blockId);
	}

	@Transactional
	public StudySession start(UUID blockId, UUID sessionId) {
		StudySession session = getForBlock(blockId, sessionId);
		try {
			session.start();
		} catch (IllegalStateException exception) {
			throw new BusinessRuleException(exception.getMessage());
		}
		return session;
	}

	@Transactional
	public StudySession complete(UUID blockId, UUID sessionId) {
		StudySession session = getForBlock(blockId, sessionId);
		try {
			session.complete();
		} catch (IllegalStateException exception) {
			throw new BusinessRuleException(exception.getMessage());
		}
		return session;
	}

	@Transactional
	public StudySession cancel(UUID blockId, UUID sessionId) {
		StudySession session = getForBlock(blockId, sessionId);
		try {
			session.cancel();
		} catch (IllegalStateException exception) {
			throw new BusinessRuleException(exception.getMessage());
		}
		return session;
	}

	private StudySession getForBlock(UUID blockId, UUID sessionId) {
		activeBlock(blockId);
		StudySession session = sessionRepository.findById(sessionId)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new NotFoundException("Sessão de estudo não encontrada."));
		return session;
	}

	private StudyBlock activeBlock(UUID blockId) {
		String ownerId = currentUserResolver.currentUserId();
		return blockRepository.findByIdAndOwnerId(blockId, ownerId)
				.filter(block -> block.getStatus() == StudyBlockStatus.ACTIVE)
				.orElseThrow(() -> new NotFoundException("Bloco de estudo não encontrado."));
	}
}
