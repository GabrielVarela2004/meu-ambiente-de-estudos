package br.com.estudos.estudos_backend.studysession;

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
public class StudySessionService {
	private final StudySessionRepository sessionRepository;
	private final StudyBlockRepository blockRepository;

	public StudySessionService(StudySessionRepository sessionRepository, StudyBlockRepository blockRepository) {
		this.sessionRepository = sessionRepository;
		this.blockRepository = blockRepository;
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
			throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
		}
		return session;
	}

	@Transactional
	public StudySession complete(UUID blockId, UUID sessionId) {
		StudySession session = getForBlock(blockId, sessionId);
		try {
			session.complete();
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
		}
		return session;
	}

	@Transactional
	public StudySession cancel(UUID blockId, UUID sessionId) {
		StudySession session = getForBlock(blockId, sessionId);
		try {
			session.cancel();
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
		}
		return session;
	}

	private StudySession getForBlock(UUID blockId, UUID sessionId) {
		activeBlock(blockId);
		StudySession session = sessionRepository.findById(sessionId)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study session not found"));
		return session;
	}

	private StudyBlock activeBlock(UUID blockId) {
		return blockRepository.findById(blockId)
				.filter(block -> block.getStatus() == StudyBlockStatus.ACTIVE)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study block not found"));
	}
}
