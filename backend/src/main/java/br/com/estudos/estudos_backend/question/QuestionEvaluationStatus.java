package br.com.estudos.estudos_backend.question;

/**
 * Resultado da correção de uma resposta de questão teórica:
 * - CORRECT: resposta correta.
 * - INCORRECT: resposta incorreta.
 * - UNGRADABLE: resposta não avaliável (ex.: interrompida, anulada ou sem critério conclusivo).
 */
public enum QuestionEvaluationStatus {
	CORRECT,
	INCORRECT,
	UNGRADABLE
}
