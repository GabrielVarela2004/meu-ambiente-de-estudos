package br.com.estudos.estudos_backend.question;

/** Nível de rigor da correção das questões abertas (Configurações). */
public enum EvaluationRigor {
	/** Padrão: CORRETA só com nota geral ≥ 85, todos os essenciais e a terminologia do material. */
	RIGOROSO(85, true),
	/** CORRETA com nota geral ≥ 75 e todos os essenciais; terminologia diferente não limita a PARCIAL. */
	MODERADO(75, false);

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
