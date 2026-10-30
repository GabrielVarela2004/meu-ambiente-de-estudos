package br.com.estudos.estudos_backend.question;

/**
 * Resultado da correção de uma resposta de questão teórica (exibido em português na tela):
 * - CORRECT (CORRETA): resposta correta e completa.
 * - PARTIAL (PARCIAL): parte do que o material ensina; não conta como acerto.
 * - INCORRECT (INCORRETA): resposta incorreta.
 * - PENDING_EVALUATION (PENDENTE DE AVALIAÇÃO): IA indisponível; fica fora das estatísticas até ser reavaliada.
 * - UNGRADABLE: resposta não avaliável (ex.: interrompida, anulada ou sem critério conclusivo).
 */
public enum QuestionEvaluationStatus {
	CORRECT,
	PARTIAL,
	INCORRECT,
	PENDING_EVALUATION,
	UNGRADABLE;

	/** Entra nas estatísticas de desempenho (exclui pendentes e não avaliáveis). */
	public boolean isGraded() {
		return this == CORRECT || this == PARTIAL || this == INCORRECT;
	}
}
