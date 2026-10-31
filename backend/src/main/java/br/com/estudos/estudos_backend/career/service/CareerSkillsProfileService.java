package br.com.estudos.estudos_backend.career.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.exercise.PracticalExerciseAttempt;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseAttemptRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionAttempt;
import br.com.estudos.estudos_backend.question.StudyQuestionAttemptRepository;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;

/** Perfil de habilidades: cada bloco de estudo é uma competência, avaliada pelas tentativas feitas nele. */
@Service
public class CareerSkillsProfileService {

	private final StudyBlockRepository blockRepository;
	private final StudyQuestionAttemptRepository questionAttemptRepository;
	private final PracticalExerciseAttemptRepository exerciseAttemptRepository;
	private final CurrentUserResolver currentUserResolver;

	public CareerSkillsProfileService(
			StudyBlockRepository blockRepository,
			StudyQuestionAttemptRepository questionAttemptRepository,
			PracticalExerciseAttemptRepository exerciseAttemptRepository,
			CurrentUserResolver currentUserResolver) {
		this.blockRepository = blockRepository;
		this.questionAttemptRepository = questionAttemptRepository;
		this.exerciseAttemptRepository = exerciseAttemptRepository;
		this.currentUserResolver = currentUserResolver;
	}

	public record CompetencyItem(
			String skillName,
			String category,
			String level, // 'DEMONSTRATED', 'IN_DEVELOPMENT', 'STUDIED', 'UNASSESSED'
			int exerciseCount,
			int practicalCount,
			int accuracyPercent,
			String sourceBlockTitle,
			/** Título e descrição do bloco, usados na comparação com os requisitos das vagas. */
			String matchText
	) {}

	public record UserCareerSkillsProfile(
			List<CompetencyItem> demonstrated,
			List<CompetencyItem> inDevelopment,
			List<CompetencyItem> studied,
			int totalSkillsCount,
			int demonstratedSkillsCount
	) {}

	/** Localiza o bloco que representa a competência (blocos mais recentes primeiro). */
	@Transactional(readOnly = true)
	public Optional<StudyBlock> findBlockForSkill(String skillName) {
		if (skillName == null || skillName.isBlank()) {
			return Optional.empty();
		}
		String wanted = skillName.trim().toLowerCase();
		return blockRepository.findAllByOwnerIdOrderByUpdatedAtDesc(currentUserResolver.currentUserId()).stream()
				.filter(block -> block.getTitle().trim().toLowerCase().equals(wanted))
				.findFirst();
	}

	@Transactional(readOnly = true)
	public UserCareerSkillsProfile getUserSkillsProfile() {
		String ownerId = currentUserResolver.currentUserId();
		List<StudyBlock> userBlocks = blockRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);

		Map<UUID, List<StudyQuestionAttempt>> qByBlock = new HashMap<>();
		for (StudyQuestionAttempt qa : questionAttemptRepository.findAllByOwnerIdOrderByAnsweredAtDesc(ownerId)) {
			if (qa.getStudyBlockId() != null) {
				qByBlock.computeIfAbsent(qa.getStudyBlockId(), k -> new ArrayList<>()).add(qa);
			}
		}

		Map<UUID, List<PracticalExerciseAttempt>> pByBlock = new HashMap<>();
		for (PracticalExerciseAttempt pa : exerciseAttemptRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)) {
			if (pa.getStudyBlockId() != null) {
				pByBlock.computeIfAbsent(pa.getStudyBlockId(), k -> new ArrayList<>()).add(pa);
			}
		}

		List<CompetencyItem> demonstrated = new ArrayList<>();
		List<CompetencyItem> inDevelopment = new ArrayList<>();
		List<CompetencyItem> studied = new ArrayList<>();
		Set<String> seenSkills = new HashSet<>();

		for (StudyBlock block : userBlocks) {
			String name = block.getTitle().trim();
			if (!seenSkills.add(name.toLowerCase())) {
				continue;
			}
			String matchText = block.getDescription() == null || block.getDescription().isBlank()
					? name
					: name + " " + block.getDescription();

			List<StudyQuestionAttempt> blockQAs = qByBlock.getOrDefault(block.getId(), List.of());
			List<PracticalExerciseAttempt> blockPAs = pByBlock.getOrDefault(block.getId(), List.of());

			long correctQAs = blockQAs.stream().filter(StudyQuestionAttempt::isCorrect).count();
			long passedPAs = blockPAs.stream().filter(pa -> pa.getScorePercent() >= 70 || pa.getSelfRating() >= 4).count();

			int totalAttempts = blockQAs.size() + blockPAs.size();
			int accuracy = totalAttempts > 0 ? (int) Math.round(((correctQAs + passedPAs) * 100.0) / totalAttempts) : 0;

			if (totalAttempts >= 2 && accuracy >= 70) {
				demonstrated.add(new CompetencyItem(name, "Bloco de estudo", "DEMONSTRATED",
						blockQAs.size(), blockPAs.size(), accuracy, name, matchText));
			} else if (totalAttempts > 0) {
				inDevelopment.add(new CompetencyItem(name, "Bloco de estudo", "IN_DEVELOPMENT",
						blockQAs.size(), blockPAs.size(), accuracy, name, matchText));
			} else {
				studied.add(new CompetencyItem(name, "Bloco de estudo", "STUDIED", 0, 0, 0, name, matchText));
			}
		}

		int total = demonstrated.size() + inDevelopment.size() + studied.size();
		return new UserCareerSkillsProfile(demonstrated, inDevelopment, studied, total, demonstrated.size());
	}
}
