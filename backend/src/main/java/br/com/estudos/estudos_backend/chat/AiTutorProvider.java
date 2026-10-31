package br.com.estudos.estudos_backend.chat;

import java.util.List;

/**
 * Porta de abstração para integração modular com diferentes provedores de IA (Requisito 6.1).
 * A aplicação depende desta interface, e não diretamente de um fornecedor específico.
 */
public interface AiTutorProvider {

	String providerName();

	boolean isConfigured();

	default String configuredModel() {
		return "unknown";
	}

	AiTutorReply generateReply(String systemInstruction, String userPrompt);

	/**
	 * Solicita geração de resposta com opções explícitas (como histórico recente de conversa,
	 * modo de saída estruturada JSON e limite customizado de tokens).
	 * Implementações padrão delegam para {@link #generateReply(String, String)} para preservar
	 * compatibilidade retroativa com fakes e mocks nos testes.
	 */
	default AiTutorReply generateReply(AiTutorRequest request) {
		return generateReply(request.systemInstruction(), request.userPrompt());
	}

	record AiTutorMessage(String role, String content) {}

	record AiTutorRequest(
			String systemInstruction,
			List<AiTutorMessage> conversationHistory,
			String userPrompt,
			boolean jsonObjectMode,
			Integer maxOutputTokens,
			/** Temperatura pedida (ex.: 0.1 em correções); null = padrão do modelo. Ignorada onde o modelo não aceita. */
			Double temperature,
			/**
			 * Esquema JSON da saída esperada (formato comum {@code {"type":"array","items":{...}}}), para o provedor
			 * responder com JSON estruturado; null = sem esquema. Ignorado onde o provedor não suporta.
			 */
			String responseSchema) {

		public AiTutorRequest {
			conversationHistory = conversationHistory == null ? List.of() : List.copyOf(conversationHistory);
		}

		public AiTutorRequest(String systemInstruction, List<AiTutorMessage> conversationHistory, String userPrompt,
				boolean jsonObjectMode, Integer maxOutputTokens) {
			this(systemInstruction, conversationHistory, userPrompt, jsonObjectMode, maxOutputTokens, null, null);
		}

		public AiTutorRequest(String systemInstruction, List<AiTutorMessage> conversationHistory, String userPrompt,
				boolean jsonObjectMode, Integer maxOutputTokens, Double temperature) {
			this(systemInstruction, conversationHistory, userPrompt, jsonObjectMode, maxOutputTokens, temperature, null);
		}

		/** Mesmo pedido com limite de saída explícito (vale acima do padrão do .env; ex.: várias questões numa chamada). */
		public AiTutorRequest withMaxOutputTokens(int tokens) {
			return new AiTutorRequest(systemInstruction, conversationHistory, userPrompt, jsonObjectMode, tokens,
					temperature, responseSchema);
		}

		/** Mesmo pedido com esquema JSON estruturado para a resposta. */
		public AiTutorRequest withResponseSchema(String schema) {
			return new AiTutorRequest(systemInstruction, conversationHistory, userPrompt, jsonObjectMode,
					maxOutputTokens, temperature, schema);
		}

		public static AiTutorRequest ofText(String systemInstruction, String userPrompt) {
			return new AiTutorRequest(systemInstruction, List.of(), userPrompt, false, null);
		}

		public static AiTutorRequest ofJson(String systemInstruction, String userPrompt) {
			return new AiTutorRequest(systemInstruction, List.of(), userPrompt, true, null);
		}

		public static AiTutorRequest ofConversation(
				String systemInstruction,
				List<AiTutorMessage> conversationHistory,
				String userPrompt) {
			return new AiTutorRequest(systemInstruction, conversationHistory, userPrompt, false, null);
		}
	}

	record AiTutorReply(String answer, String providerName, String modelName) {
		public String provider() {
			return providerName;
		}

		public String model() {
			return modelName;
		}
	}
}
