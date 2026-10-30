package br.com.estudos.estudos_backend.ai;

import java.util.List;

import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorRequest;

/**
 * Provedor concreto de modelos (OpenAI, Anthropic, Gemini) registrado no {@link AiProviderRouter}.
 * As chaves de API vêm apenas do ambiente; nunca são persistidas nem expostas.
 */
public interface AiModelProvider {

	/** Identificador estável usado nas configurações e no registro de uso (ex.: "openai"). */
	String id();

	String displayName();

	/** Chave presente e integração habilitada no ambiente. */
	boolean isConfigured();

	String defaultModel();

	List<String> suggestedModels();

	/**
	 * Gera uma resposta com o modelo indicado.
	 * Falhas são lançadas como {@code ResponseStatusException} com o status HTTP equivalente.
	 */
	AiModelResult generate(AiTutorRequest request, String model);

	record AiModelResult(String text, String model, Integer inputTokens, Integer outputTokens) {}
}
