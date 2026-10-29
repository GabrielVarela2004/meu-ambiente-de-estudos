package br.com.estudos.estudos_backend.chat;

/**
 * Porta de abstração para integração modular com diferentes provedores de IA (Requisito 6.1).
 * A aplicação depende desta interface, e não diretamente de um fornecedor específico.
 */
public interface AiTutorProvider {

	String providerName();

	boolean isConfigured();

	AiTutorReply generateReply(String systemInstruction, String userPrompt);

	record AiTutorReply(String answer, String providerName, String modelName) {}
}
