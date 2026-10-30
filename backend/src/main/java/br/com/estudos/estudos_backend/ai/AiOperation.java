package br.com.estudos.estudos_backend.ai;

/** Operações da plataforma que usam IA; cada uma pode ser roteada para um provedor diferente. */
public enum AiOperation {
	// Geração, chat e tutoria não usam cache: precisam de variedade ou dependem da conversa.
	CHAT("Tutor IA (chat)"),
	QUESTION_GENERATION("Geração de questões"),
	EXERCISE_GENERATION("Geração de exercícios práticos"),
	EXERCISE_TUTOR("Tutoria nos exercícios práticos"),
	TOPIC_EXTRACTION("Extração de tópicos das fontes", true, true),
	MATERIAL_SUMMARY("Resumo de materiais", true, true),
	PERFORMANCE_ANALYSIS("Análise de desempenho", true, true),
	CAREER_ANALYSIS("Análise de vagas", true, true),
	/** Só o Gemini aceita vídeo do YouTube por URL: não tem rota configurável, mas entra no consumo. */
	VIDEO_ANALYSIS("Análise de vídeos do YouTube", false, false),
	ANSWER_EVALUATION("Correção de respostas abertas", true, true);

	private final String label;
	private final boolean routable;
	private final boolean cacheable;

	AiOperation(String label) {
		this(label, true, false);
	}

	AiOperation(String label, boolean routable, boolean cacheable) {
		this.label = label;
		this.routable = routable;
		this.cacheable = cacheable;
	}

	/** Se a mesma entrada deve dar a mesma saída: a resposta pode vir do cache ({@link AiResponseCacheService}). */
	public boolean cacheable() {
		return cacheable;
	}

	public String label() {
		return label;
	}

	/** Se a operação aparece na tela de rotas (provedor por operação). */
	public boolean routable() {
		return routable;
	}
}
