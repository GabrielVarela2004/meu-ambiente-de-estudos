package br.com.estudos.estudos_backend.ai;

/** Operações da plataforma que usam IA; cada uma pode ser roteada para um provedor diferente. */
public enum AiOperation {
	CHAT("Tutor IA (chat)"),
	QUESTION_GENERATION("Geração de questões"),
	EXERCISE_GENERATION("Geração de exercícios práticos"),
	EXERCISE_TUTOR("Tutoria nos exercícios práticos"),
	TOPIC_EXTRACTION("Extração de tópicos das fontes"),
	MATERIAL_SUMMARY("Resumo de materiais"),
	PERFORMANCE_ANALYSIS("Análise de desempenho"),
	CAREER_ANALYSIS("Análise de vagas");

	private final String label;

	AiOperation(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
