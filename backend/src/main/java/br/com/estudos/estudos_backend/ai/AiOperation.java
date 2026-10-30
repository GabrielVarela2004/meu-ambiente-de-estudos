package br.com.estudos.estudos_backend.ai;

/** Operações da plataforma que usam IA; cada uma pode ser roteada para um provedor diferente. */
public enum AiOperation {
	CHAT("Tutor IA (chat)"),
	QUESTION_GENERATION("Geração de questões"),
	EXERCISE_GENERATION("Geração de exercícios práticos"),
	TOPIC_EXTRACTION("Extração de tópicos das fontes"),
	MATERIAL_SUMMARY("Resumo de materiais"),
	PERFORMANCE_ANALYSIS("Análise de desempenho"),
	CAREER_ANALYSIS("Análise de vagas"),
	/** Só o Gemini aceita vídeo do YouTube por URL: não tem rota configurável, mas entra no consumo. */
	VIDEO_ANALYSIS("Análise de vídeos do YouTube", false),
	ANSWER_EVALUATION("Correção de respostas abertas");

	private final String label;
	private final boolean routable;

	AiOperation(String label) {
		this(label, true);
	}

	AiOperation(String label, boolean routable) {
		this.label = label;
		this.routable = routable;
	}

	public String label() {
		return label;
	}

	/** Se a operação aparece na tela de rotas (provedor por operação). */
	public boolean routable() {
		return routable;
	}
}
