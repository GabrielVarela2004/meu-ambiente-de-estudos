package br.com.estudos.estudos_backend.ai;

import java.net.URI;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuração do Google Gemini (Google AI Studio), no mesmo padrão de {@code OpenAiProperties}.
 * A chave vem só de {@code GEMINI_API_KEY} e nunca aparece em logs nem respostas.
 */
@Component
public class GeminiProperties {

	private static final Logger log = LoggerFactory.getLogger(GeminiProperties.class);

	/** Modelo recomendado pela documentação oficial para projetos novos (conferido em 2026-10-30). */
	public static final String DEFAULT_MODEL = "gemini-3.8-flash";
	public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
	public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
	public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 60;
	public static final int DEFAULT_MAX_OUTPUT_TOKENS = 4096;
	/** O plano gratuito tem limite baixo de requisições por minuto; o limite local evita estourá-lo. */
	public static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 10;

	private final boolean enabled;
	private final String apiKey;
	private final String model;
	private final String baseUrl;
	private final int connectTimeoutSeconds;
	private final int requestTimeoutSeconds;
	private final int maxOutputTokens;
	private final int maxRequestsPerMinute;

	public GeminiProperties(
			@Value("${app.ai.gemini.enabled:${GEMINI_ENABLED:true}}") boolean enabled,
			@Value("${app.ai.gemini.api-key:${GEMINI_API_KEY:}}") String apiKey,
			@Value("${app.ai.gemini.model:${GEMINI_MODEL:" + DEFAULT_MODEL + "}}") String model,
			@Value("${app.ai.gemini.base-url:${GEMINI_BASE_URL:" + DEFAULT_BASE_URL + "}}") String baseUrl,
			@Value("${app.ai.gemini.connect-timeout-seconds:${GEMINI_CONNECT_TIMEOUT_SECONDS:10}}") int connectTimeoutSeconds,
			@Value("${app.ai.gemini.request-timeout-seconds:${GEMINI_REQUEST_TIMEOUT_SECONDS:60}}") int requestTimeoutSeconds,
			@Value("${app.ai.gemini.max-output-tokens:${GEMINI_MAX_OUTPUT_TOKENS:4096}}") int maxOutputTokens,
			@Value("${app.ai.gemini.max-requests-per-minute:${GEMINI_MAX_REQUESTS_PER_MINUTE:10}}") int maxRequestsPerMinute) {
		this.enabled = enabled;
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
		String resolvedBaseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
		this.baseUrl = resolvedBaseUrl.endsWith("/") ? resolvedBaseUrl : resolvedBaseUrl + "/";
		this.connectTimeoutSeconds = clamp(connectTimeoutSeconds, 1, 120, DEFAULT_CONNECT_TIMEOUT_SECONDS);
		this.requestTimeoutSeconds = clamp(requestTimeoutSeconds, 5, 600, DEFAULT_REQUEST_TIMEOUT_SECONDS);
		this.maxOutputTokens = clamp(maxOutputTokens, 256, 65_536, DEFAULT_MAX_OUTPUT_TOKENS);
		this.maxRequestsPerMinute = clamp(maxRequestsPerMinute, 1, 1_000, DEFAULT_MAX_REQUESTS_PER_MINUTE);
		validateEndpointUri(this.baseUrl);
	}

	/** Configuração com os valores padrão, usada em testes. */
	public static GeminiProperties withKey(String apiKey) {
		return new GeminiProperties(true, apiKey, DEFAULT_MODEL, DEFAULT_BASE_URL, DEFAULT_CONNECT_TIMEOUT_SECONDS,
				DEFAULT_REQUEST_TIMEOUT_SECONDS, DEFAULT_MAX_OUTPUT_TOKENS, DEFAULT_MAX_REQUESTS_PER_MINUTE);
	}

	@PostConstruct
	void logStartupDiagnostics() {
		if (!enabled) {
			log.info("Gemini desativado (GEMINI_ENABLED=false).");
		} else if (!hasApiKey()) {
			log.info("Gemini sem chave (GEMINI_API_KEY vazia): o provedor fica indisponível até ser configurado.");
		} else {
			log.info("Gemini configurado [modelo={}, timeout={}s, limite={}/min].", model, requestTimeoutSeconds,
					maxRequestsPerMinute);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean hasApiKey() {
		return !apiKey.isBlank();
	}

	public boolean isConfigured() {
		return enabled && hasApiKey();
	}

	public String getApiKey() {
		return apiKey;
	}

	public String getModel() {
		return model;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public int getConnectTimeoutSeconds() {
		return connectTimeoutSeconds;
	}

	public int getRequestTimeoutSeconds() {
		return requestTimeoutSeconds;
	}

	public int getMaxOutputTokens() {
		return maxOutputTokens;
	}

	public int getMaxRequestsPerMinute() {
		return maxRequestsPerMinute;
	}

	private static int clamp(int value, int min, int max, int fallback) {
		return value < min || value > max ? fallback : value;
	}

	private static void validateEndpointUri(String url) {
		String scheme = URI.create(url).getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
			throw new IllegalStateException("GEMINI_BASE_URL inválida: use http ou https.");
		}
	}

	@Override
	public String toString() {
		return "GeminiProperties[enabled=" + enabled + ", apiKeyConfigured=" + hasApiKey() + ", model=" + model
				+ ", baseUrl=" + baseUrl + ", connectTimeoutSeconds=" + connectTimeoutSeconds
				+ ", requestTimeoutSeconds=" + requestTimeoutSeconds + ", maxOutputTokens=" + maxOutputTokens
				+ ", maxRequestsPerMinute=" + maxRequestsPerMinute + "]";
	}
}
