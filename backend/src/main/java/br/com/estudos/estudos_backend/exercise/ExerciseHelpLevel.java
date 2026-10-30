package br.com.estudos.estudos_backend.exercise;

/**
 * Quanto da resposta a tutora entregou numa mensagem do chat do exercício.
 * O desconto é aplicado sobre a autonomia da tentativa; conversar muito não é punido,
 * apenas o quanto da solução foi entregue.
 */
public enum ExerciseHelpLevel {
	/** Esclarecimento do enunciado: não revela nada da solução. */
	CLARIFICATION("Esclarecimento do enunciado", 0),
	/** Dúvida de conceito: indica lacuna teórica, desconto pequeno na prática. */
	CONCEPT("Dúvida de conceito", 2),
	/** Direção geral, sem apontar o erro. */
	DIRECTION("Direção", 5),
	/** Aponta o erro ou a estrutura exata a usar. */
	SPECIFIC_HINT("Dica específica", 10),
	/** Mostra um trecho de código que resolve parte da etapa. */
	PARTIAL_CODE("Código parcial", 25),
	/** Entrega a etapa inteira: a etapa deixa de contar. */
	STEP_SOLUTION("Solução da etapa", 0),
	/** Solução completa revelada pelo botão "Ver solução". */
	FULL_SOLUTION("Solução completa", 0);

	private final String label;
	private final int penaltyPercent;

	ExerciseHelpLevel(String label, int penaltyPercent) {
		this.label = label;
		this.penaltyPercent = penaltyPercent;
	}

	public String label() { return label; }

	/** Desconto fixo; STEP_SOLUTION e FULL_SOLUTION são tratados à parte pelo calculador. */
	public int penaltyPercent() { return penaltyPercent; }

	/** Se a mensagem conta como dica (para hintsUsedCount). */
	public boolean countsAsHint() { return this != CLARIFICATION; }
}
