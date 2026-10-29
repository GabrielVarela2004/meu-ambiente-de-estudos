package br.com.estudos.estudos_backend.learning;

/**
 * Estados conceituais de domínio (independentes para modalidade teórica e prática):
 * - UNASSESSED: Não avaliado (ainda não há evidências suficientes).
 * - LEARNING: Em aprendizado (há tentativas registradas, mas o domínio ainda não foi demonstrado).
 * - IN_REVIEW: Em revisão (precisa ser revisitado conforme desempenho e tempo decorrido).
 * - MASTERED: Dominado (critérios definidos para a modalidade foram atendidos com evidência suficiente).
 */
public enum MasteryState {
	UNASSESSED,
	LEARNING,
	IN_REVIEW,
	MASTERED
}
