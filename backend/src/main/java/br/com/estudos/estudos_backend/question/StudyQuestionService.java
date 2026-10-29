package br.com.estudos.estudos_backend.question;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialRepository;
import br.com.estudos.estudos_backend.material.StudyMaterialStatus;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;
import br.com.estudos.estudos_backend.studyblock.StudyBlockStatus;

@Service
public class StudyQuestionService {
	private final StudyQuestionRepository repository;
	private final StudyBlockService blockService;
	private final StudyMaterialRepository materialRepository;
	private final StudyQuestionAttemptRepository attemptRepository;

	public StudyQuestionService(StudyQuestionRepository repository, StudyBlockService blockService,
			StudyMaterialRepository materialRepository, StudyQuestionAttemptRepository attemptRepository) {
		this.repository = repository;
		this.blockService = blockService;
		this.materialRepository = materialRepository;
		this.attemptRepository = attemptRepository;
	}

	@Transactional
	public StudyQuestion create(UUID blockId, UUID materialId, String prompt, String explanation,
			List<StudyQuestionController.OptionRequest> options) {
		return create(blockId, materialId, prompt, explanation,
				StudyQuestionDifficulty.INTERMEDIATE, StudyQuestionFormat.MULTIPLE_CHOICE, null, options);
	}

	@Transactional
	public StudyQuestion create(UUID blockId, UUID materialId, String prompt, String explanation,
			StudyQuestionDifficulty difficulty, StudyQuestionFormat format, String expectedAnswer,
			List<StudyQuestionController.OptionRequest> options) {
		StudyBlock block = blockService.get(blockId);
		if (block.getStatus() == StudyBlockStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived study blocks cannot receive questions");
		}
		StudyMaterial material = null;
		if (materialId != null) {
			material = materialRepository.findById(materialId)
					.filter(item -> item.getStudyBlock().getId().equals(blockId))
					.filter(item -> item.getStatus() == StudyMaterialStatus.ACTIVE)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid study material"));
		}

		StudyQuestionDifficulty resolvedDifficulty = difficulty == null ? StudyQuestionDifficulty.INTERMEDIATE : difficulty;
		StudyQuestionFormat resolvedFormat = format == null ? StudyQuestionFormat.MULTIPLE_CHOICE : format;
		String normalizedExpected = expectedAnswer == null || expectedAnswer.isBlank() ? null : expectedAnswer.trim();

		validateFormatAndInputs(resolvedFormat, normalizedExpected, options);

		StudyQuestion question = new StudyQuestion(block, material, prompt.trim(), explanation,
				resolvedDifficulty, resolvedFormat, normalizedExpected);
		if (resolvedFormat != StudyQuestionFormat.DISCURSIVE && options != null) {
			for (int i = 0; i < options.size(); i++) {
				StudyQuestionController.OptionRequest option = options.get(i);
				question.addOption(option.text().trim(), i, option.correct());
			}
		}
		return repository.save(question);
	}

	@Transactional(readOnly = true)
	public List<StudyQuestion> list(UUID blockId, boolean includeArchived) {
		blockService.get(blockId);
		return includeArchived
				? repository.findAllByStudyBlockIdOrderByUpdatedAtDesc(blockId)
				: repository.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(blockId, StudyQuestionStatus.ACTIVE);
	}

	@Transactional
	public void archive(UUID blockId, UUID id) {
		StudyQuestion question = getOwned(blockId, id);
		question.archive();
		repository.save(question);
	}

	@Transactional
	public StudyQuestionAnswer answer(UUID blockId, UUID id, UUID optionId) {
		return answer(blockId, id, optionId, null);
	}

	@Transactional
	public StudyQuestionAnswer answer(UUID blockId, UUID id, UUID optionId, String discursiveAnswer) {
		StudyQuestion question = getOwned(blockId, id);

		if (question.getFormat() == StudyQuestionFormat.DISCURSIVE) {
			if (discursiveAnswer == null || discursiveAnswer.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Discursive questions require a non-blank discursiveAnswer");
			}
			String cleanAnswer = discursiveAnswer.trim();
			boolean correct = evaluateDiscursiveCriteria(cleanAnswer, question.getExpectedAnswer());
			question.scheduleNextReview(correct);
			repository.save(question);
			attemptRepository.save(new StudyQuestionAttempt(blockId, id, null, cleanAnswer, correct));

			String baseExplanation = question.getExplanation() == null || question.getExplanation().isBlank()
					? ""
					: question.getExplanation() + " ";
			String criteriaFeedback = baseExplanation + "[Critérios/Gabarito esperado: " + question.getExpectedAnswer() + "]";
			return new StudyQuestionAnswer(correct, criteriaFeedback.trim());
		}

		if (optionId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Objective questions require an optionId");
		}
		StudyQuestionOption option = question.getOptions().stream()
				.filter(item -> item.getId().equals(optionId))
				.findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Option does not belong to question"));
		boolean correct = option.isCorrect();
		question.scheduleNextReview(correct);
		repository.save(question);
		attemptRepository.save(new StudyQuestionAttempt(blockId, id, optionId, null, correct));
		return new StudyQuestionAnswer(correct, question.getExplanation());
	}

	@Transactional(readOnly = true)
	public List<StudyQuestion> dueForReview(UUID blockId) {
		blockService.get(blockId);
		return repository.findAllByStudyBlockIdAndStatusAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
				blockId, StudyQuestionStatus.ACTIVE, OffsetDateTime.now());
	}

	@Transactional(readOnly = true)
	public List<StudyQuestion> dueForGlobalReview() {
		return repository.findGlobalDueForReview(
				StudyQuestionStatus.ACTIVE, StudyBlockStatus.ACTIVE, OffsetDateTime.now());
	}

	@Transactional
	public StudyQuestion rescheduleReview(UUID questionId, int days) {
		if (days < 1 || days > 365) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 365");
		}
		StudyQuestion question = repository.findById(questionId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study question not found"));
		if (question.getStatus() == StudyQuestionStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived study questions cannot be rescheduled");
		}
		question.rescheduleManual(days);
		return repository.save(question);
	}

	@Transactional(readOnly = true)
	public StudyQuestionStats stats(UUID blockId) {
		blockService.get(blockId);
		List<StudyQuestionAttempt> attempts = attemptRepository.findAllByStudyBlockIdOrderByAnsweredAtDesc(blockId);
		long correct = attempts.stream().filter(StudyQuestionAttempt::isCorrect).count();
		return new StudyQuestionStats(attempts.size(), correct,
				attempts.isEmpty() ? 0 : Math.round(correct * 100.0 / attempts.size()));
	}

	@Transactional(readOnly = true)
	public StudyQuestionStats stats(UUID blockId, int days) {
		blockService.get(blockId);
		OffsetDateTime since = OffsetDateTime.now().minusDays(days);
		List<StudyQuestionAttempt> attempts = attemptRepository
				.findAllByStudyBlockIdAndAnsweredAtGreaterThanEqualOrderByAnsweredAtDesc(blockId, since);
		long correct = attempts.stream().filter(StudyQuestionAttempt::isCorrect).count();
		return new StudyQuestionStats(attempts.size(), correct,
				attempts.isEmpty() ? 0 : Math.round(correct * 100.0 / attempts.size()));
	}

	private StudyQuestion getOwned(UUID blockId, UUID id) {
		StudyQuestion question = repository.findById(id)
				.filter(item -> item.getStudyBlock().getId().equals(blockId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study question not found"));
		if (question.getStatus() == StudyQuestionStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived study questions cannot be answered");
		}
		return question;
	}

	private void validateFormatAndInputs(StudyQuestionFormat format, String expectedAnswer,
			List<StudyQuestionController.OptionRequest> options) {
		if (format == StudyQuestionFormat.DISCURSIVE) {
			if (expectedAnswer == null || expectedAnswer.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Discursive questions require a non-blank expectedAnswer with evaluation criteria");
			}
			return;
		}

		if (format == StudyQuestionFormat.TRUE_FALSE) {
			if (options == null || options.size() != 2
					|| options.stream().filter(StudyQuestionController.OptionRequest::correct).count() != 1
					|| options.stream().anyMatch(option -> option.text() == null || option.text().isBlank())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"True/False questions require exactly 2 options and 1 correct option");
			}
			return;
		}

		if (options == null || options.size() < 2 || options.size() > 6
				|| options.stream().filter(StudyQuestionController.OptionRequest::correct).count() != 1
				|| options.stream().anyMatch(option -> option.text() == null || option.text().isBlank())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Questions require 2 to 6 options and exactly one correct option");
		}
	}

	private boolean evaluateDiscursiveCriteria(String userAnswer, String expectedAnswer) {
		if (expectedAnswer == null || expectedAnswer.isBlank()) {
			return false;
		}
		String normalizedUser = userAnswer.toLowerCase(Locale.ROOT);
		String normalizedExpected = expectedAnswer.toLowerCase(Locale.ROOT);
		if (normalizedUser.contains(normalizedExpected)) {
			return true;
		}
		List<String> keyTerms = Arrays.stream(normalizedExpected.split("[^\\p{L}\\p{N}]+"))
				.filter(term -> term.length() >= 4)
				.distinct()
				.toList();
		if (keyTerms.isEmpty()) {
			return normalizedUser.equals(normalizedExpected);
		}
		long matched = keyTerms.stream().filter(normalizedUser::contains).count();
		long required = Math.max(1L, Math.round(Math.ceil(keyTerms.size() * 0.6)));
		return matched >= required;
	}

	public record StudyQuestionAnswer(boolean correct, String explanation) {}
	public record StudyQuestionStats(long attempts, long correct, long accuracyPercent) {}
}
