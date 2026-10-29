package br.com.estudos.estudos_backend.knowledge;

/**
 * Resultado da avaliação de um tópico.
 *
 * @param recentAccuracyPercent percentual de acerto nas respostas recentes; {@code null} quando não há amostra suficiente
 */
public record TopicLearningAssessment(
		TopicLearningState state,
		String reason,
		int attempts,
		Integer recentAccuracyPercent) {
}
