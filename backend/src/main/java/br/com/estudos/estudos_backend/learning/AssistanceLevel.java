package br.com.estudos.estudos_backend.learning;

/**
 * Categorias de nível de ajuda utilizada para resolver uma atividade prática ou teórica:
 * - INDEPENDENT / NONE: resolveu sem dicas ou solução fornecida.
 * - WITH_HINTS: precisou de orientações graduais.
 * - SOLUTION_REVEALED: consultou uma solução ou explicação que apresentou a resposta.
 * - NOT_COMPLETED: não conseguiu chegar a uma solução válida.
 */
public enum AssistanceLevel {
	NONE,
	INDEPENDENT,
	WITH_HINTS,
	SOLUTION_REVEALED,
	NOT_COMPLETED
}
