package br.com.estudos.estudos_backend.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Regra pura (sem Spring nem banco) que estima o estado de aprendizagem de um tópico
 * a partir das respostas registradas nas questões vinculadas a ele.
 *
 * Critérios, em ordem de avaliação:
 * 1. Nenhuma questão vinculada            → INSUFFICIENT_DATA (não há como medir).
 * 2. Questões vinculadas, nenhuma resposta → NOT_STUDIED.
 * 3. Menos de 3 respostas                  → INSUFFICIENT_DATA (amostra pequena demais).
 * 4. Acerto recente abaixo de 60%          → NEEDS_REVIEW.
 * 5. Ao menos 5 respostas, acerto recente de pelo menos 80%, acertos em ao menos
 *    2 questões diferentes e resposta mais recente correta → CONSISTENT (domínio estimado).
 * 6. Demais casos                           → LEARNING.
 *
 * "Acerto recente" considera somente as 5 respostas mais recentes, para separar
 * desempenho recente de desempenho histórico.
 */
public class TopicLearningStateEvaluator {

	public static final int MIN_ATTEMPTS_TO_EVALUATE = 3;
	public static final int MIN_ATTEMPTS_FOR_CONSISTENT = 5;
	public static final int RECENT_WINDOW = 5;
	public static final int NEEDS_REVIEW_BELOW_PERCENT = 60;
	public static final int CONSISTENT_FROM_PERCENT = 80;
	public static final int MIN_DISTINCT_CORRECT_QUESTIONS = 2;

	/**
	 * @param attemptsNewestFirst respostas das questões do tópico, da mais recente para a mais antiga
	 * @param linkedQuestions     quantidade de questões vinculadas ao tópico
	 */
	public TopicLearningAssessment evaluate(List<AttemptEvidence> attemptsNewestFirst, int linkedQuestions) {
		int total = attemptsNewestFirst.size();

		if (linkedQuestions == 0) {
			return new TopicLearningAssessment(TopicLearningState.INSUFFICIENT_DATA,
					"Nenhuma questão vinculada a este tópico. Sem evidências, o estado não pode ser estimado.",
					0, null);
		}
		if (total == 0) {
			return new TopicLearningAssessment(TopicLearningState.NOT_STUDIED,
					"Nenhuma resposta registrada para a(s) " + linkedQuestions + " questão(ões) vinculada(s).",
					0, null);
		}
		if (total < MIN_ATTEMPTS_TO_EVALUATE) {
			return new TopicLearningAssessment(TopicLearningState.INSUFFICIENT_DATA,
					"Apenas " + total + " resposta(s) registrada(s). São necessárias pelo menos "
							+ MIN_ATTEMPTS_TO_EVALUATE + " para estimar o estado.",
					total, null);
		}

		List<AttemptEvidence> recent = attemptsNewestFirst.subList(0, Math.min(RECENT_WINDOW, total));
		long recentCorrect = recent.stream().filter(AttemptEvidence::correct).count();
		int recentPercent = (int) Math.round(recentCorrect * 100.0 / recent.size());
		long distinctCorrectQuestions = recent.stream()
				.filter(AttemptEvidence::correct)
				.map(AttemptEvidence::questionId)
				.distinct()
				.count();
		boolean mostRecentCorrect = recent.get(0).correct();
		String basis = recentPercent + "% de acerto nas últimas " + recent.size() + " resposta(s)";

		if (recentPercent < NEEDS_REVIEW_BELOW_PERCENT) {
			return new TopicLearningAssessment(TopicLearningState.NEEDS_REVIEW,
					basis + ", abaixo do limite de " + NEEDS_REVIEW_BELOW_PERCENT + "%.",
					total, recentPercent);
		}

		boolean enoughAttempts = total >= MIN_ATTEMPTS_FOR_CONSISTENT;
		boolean highAccuracy = recentPercent >= CONSISTENT_FROM_PERCENT;
		boolean variedEvidence = distinctCorrectQuestions >= MIN_DISTINCT_CORRECT_QUESTIONS;

		if (enoughAttempts && highAccuracy && variedEvidence && mostRecentCorrect) {
			return new TopicLearningAssessment(TopicLearningState.CONSISTENT,
					basis + ", com acertos em " + distinctCorrectQuestions
							+ " questões diferentes. Domínio estimado a partir das respostas, não uma garantia.",
					total, recentPercent);
		}

		List<String> missing = new ArrayList<>();
		if (!enoughAttempts) missing.add("ao menos " + MIN_ATTEMPTS_FOR_CONSISTENT + " respostas (há " + total + ")");
		if (!highAccuracy) missing.add("acerto recente de pelo menos " + CONSISTENT_FROM_PERCENT + "%");
		if (!variedEvidence) missing.add("acertos em pelo menos " + MIN_DISTINCT_CORRECT_QUESTIONS + " questões diferentes");
		if (!mostRecentCorrect) missing.add("acerto na resposta mais recente");
		return new TopicLearningAssessment(TopicLearningState.LEARNING,
				basis + ". Para ser considerado consistente, falta: " + String.join("; ", missing) + ".",
				total, recentPercent);
	}

	public record AttemptEvidence(UUID questionId, boolean correct) {}
}
