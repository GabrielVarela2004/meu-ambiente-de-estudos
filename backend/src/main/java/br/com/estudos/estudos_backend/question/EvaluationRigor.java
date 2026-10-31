package br.com.estudos.estudos_backend.question;

/** Nível de rigor da correção das questões abertas (Configurações). Escala: CORRETA ≥ 80, PARCIAL 50–79, INCORRETA < 50. */
public enum EvaluationRigor {
	/** Padrão: CORRETA só com nota geral ≥ 80, todos os essenciais e vocabulário tecnicamente equivalente ao do material. */
	RIGOROSO(80, true),
	/** CORRETA com nota geral ≥ 80 e todos os essenciais; explicar com outras palavras não limita a PARCIAL. */
	MODERADO(80, false);

	private final int correctThreshold;
	private final boolean requiresMaterialTerminology;

	EvaluationRigor(int correctThreshold, boolean requiresMaterialTerminology) {
		this.correctThreshold = correctThreshold;
		this.requiresMaterialTerminology = requiresMaterialTerminology;
	}

	public int correctThreshold() {
		return correctThreshold;
	}

	public boolean requiresMaterialTerminology() {
		return requiresMaterialTerminology;
	}
}
