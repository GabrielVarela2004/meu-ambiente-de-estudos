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
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopic;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicRepository;
import br.com.estudos.estudos_backend.question.StudyQuestionAttempt;
import br.com.estudos.estudos_backend.question.StudyQuestionAttemptRepository;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;

@Service
public class CareerSkillsProfileService {

	private final KnowledgeTopicRepository topicRepository;
	private final StudyBlockRepository blockRepository;
	private final StudyQuestionAttemptRepository questionAttemptRepository;
	private final PracticalExerciseAttemptRepository exerciseAttemptRepository;
	private final CurrentUserResolver currentUserResolver;

	public CareerSkillsProfileService(
			KnowledgeTopicRepository topicRepository,
			StudyBlockRepository blockRepository,
			StudyQuestionAttemptRepository questionAttemptRepository,
			PracticalExerciseAttemptRepository exerciseAttemptRepository,
			CurrentUserResolver currentUserResolver) {
		this.topicRepository = topicRepository;
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
			String sourceBlockTitle
	) {}

	public record UserCareerSkillsProfile(
			List<CompetencyItem> demonstrated,
			List<CompetencyItem> inDevelopment,
			List<CompetencyItem> studied,
			int totalSkillsCount,
			int demonstratedSkillsCount
	) {}

	/**
	 * Localiza o tópico que representa a competência (mesma ordem usada no perfil:
	 * blocos mais recentes primeiro, tópicos por data de criação).
	 */
	@Transactional(readOnly = true)
	public Optional<KnowledgeTopic> findTopicForSkill(String skillName) {
		if (skillName == null || skillName.isBlank()) {
			return Optional.empty();
		}
		String wanted = skillName.trim().toLowerCase();
		String ownerId = currentUserResolver.currentUserId();
		for (StudyBlock block : blockRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)) {
			for (KnowledgeTopic topic : topicRepository.findAllByStudyBlockIdOrderByCreatedAtAsc(block.getId())) {
				// Sugestões ainda não validadas não contam como habilidade.
				if (!topic.isPendingReview() && topic.getTitle().trim().toLowerCase().equals(wanted)) {
					return Optional.of(topic);
				}
			}
		}
		return Optional.empty();
	}

	@Transactional(readOnly = true)
	public UserCareerSkillsProfile getUserSkillsProfile() {
		String ownerId = currentUserResolver.currentUserId();
		List<StudyBlock> userBlocks = blockRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);

		List<StudyQuestionAttempt> qAttempts = questionAttemptRepository.findAllByOwnerIdOrderByAnsweredAtDesc(ownerId);
		List<PracticalExerciseAttempt> pAttempts = exerciseAttemptRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);

		Map<UUID, List<StudyQuestionAttempt>> qByTopic = new HashMap<>();
		for (StudyQuestionAttempt qa : qAttempts) {
			if (qa.getTopicId() != null) {
				qByTopic.computeIfAbsent(qa.getTopicId(), k -> new ArrayList<>()).add(qa);
			}
		}

		Map<UUID, List<PracticalExerciseAttempt>> pByTopic = new HashMap<>();
		for (PracticalExerciseAttempt pa : pAttempts) {
			if (pa.getTopicId() != null) {
				pByTopic.computeIfAbsent(pa.getTopicId(), k -> new ArrayList<>()).add(pa);
			}
		}

		List<CompetencyItem> demonstrated = new ArrayList<>();
		List<CompetencyItem> inDevelopment = new ArrayList<>();
		List<CompetencyItem> studied = new ArrayList<>();
		Set<String> seenSkills = new HashSet<>();

		for (StudyBlock block : userBlocks) {
			List<KnowledgeTopic> topics = topicRepository.findAllByStudyBlockIdOrderByCreatedAtAsc(block.getId());

			for (KnowledgeTopic topic : topics) {
				if (topic.isPendingReview()) {
					continue;
				}
				String name = topic.getTitle().trim();
				if (seenSkills.contains(name.toLowerCase())) {
					continue;
				}
				seenSkills.add(name.toLowerCase());

				List<StudyQuestionAttempt> topicQAs = qByTopic.getOrDefault(topic.getId(), List.of());
				List<PracticalExerciseAttempt> topicPAs = pByTopic.getOrDefault(topic.getId(), List.of());

				long correctQAs = topicQAs.stream().filter(StudyQuestionAttempt::isCorrect).count();
				long passedPAs = topicPAs.stream().filter(pa -> pa.getScorePercent() >= 70 || pa.getSelfRating() >= 4).count();

				int totalAttempts = topicQAs.size() + topicPAs.size();
				long totalSuccess = correctQAs + passedPAs;
				int accuracy = totalAttempts > 0 ? (int) Math.round((totalSuccess * 100.0) / totalAttempts) : 0;

				if (totalAttempts >= 2 && accuracy >= 70) {
					demonstrated.add(new CompetencyItem(
							name,
							block.getTitle(),
							"DEMONSTRATED",
							topicQAs.size(),
							topicPAs.size(),
							accuracy,
							block.getTitle()
					));
				} else if (totalAttempts > 0) {
					inDevelopment.add(new CompetencyItem(
							name,
							block.getTitle(),
							"IN_DEVELOPMENT",
							topicQAs.size(),
							topicPAs.size(),
							accuracy,
							block.getTitle()
					));
				} else {
					studied.add(new CompetencyItem(
							name,
							block.getTitle(),
							"STUDIED",
							0,
							0,
							0,
							block.getTitle()
					));
				}
			}
		}

		// Se o usuário ainda não tiver tópicos individuais gerados, inclui os cadernos de estudo como habilidades estudadas
		if (demonstrated.isEmpty() && inDevelopment.isEmpty() && studied.isEmpty()) {
			for (StudyBlock b : userBlocks) {
				studied.add(new CompetencyItem(
						b.getTitle(),
						"Caderno de Estudos",
						"STUDIED",
						0,
						0,
						0,
						b.getTitle()
				));
			}
		}

		int total = demonstrated.size() + inDevelopment.size() + studied.size();
		return new UserCareerSkillsProfile(demonstrated, inDevelopment, studied, total, demonstrated.size());
	}
}
