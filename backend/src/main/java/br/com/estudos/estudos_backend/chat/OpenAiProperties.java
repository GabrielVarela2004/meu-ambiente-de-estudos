package br.com.estudos.estudos_backend.chat;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuração centralizada e validada para integração com a API da OpenAI.
 * Nunca registra nem expõe o valor de {@code apiKey} em logs ou respostas.
 */
@Component
public class OpenAiProperties {

	private static final Logger log = LoggerFactory.getLogger(OpenAiProperties.class);

	public static final String DEFAULT_MODEL = "gpt-4o-mini";
	public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1/chat/completions";
	public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
	public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 45;
	public static final int DEFAULT_MAX_OUTPUT_TOKENS = 1200;
	public static final int DEFAULT_MAX_INPUT_CHARS = 40_000;
	public static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 20;

	private final boolean enabled;
	private final String apiKey;
	private final String model;
	private final String baseUrl;
	private final int connectTimeoutSeconds;
	private final int requestTimeoutSeconds;
	private final int maxOutputTokens;
	private final int maxInputChars;
	private final int maxRequestsPerMinute;

	public OpenAiProperties(
			@Value("${app.ai.openai.enabled:${OPENAI_ENABLED:true}}") boolean enabled,
			@Value("${app.ai.openai.api-key:${OPENAI_API_KEY:}}") String apiKey,
			@Value("${app.ai.openai.model:${OPENAI_MODEL:gpt-4o-mini}}") String model,
			@Value("${app.ai.openai.base-url:${OPENAI_BASE_URL:https://api.openai.com/v1/chat/completions}}") String baseUrl,
			@Value("${app.ai.openai.connect-timeout-seconds:${OPENAI_CONNECT_TIMEOUT_SECONDS:10}}") int connectTimeoutSeconds,
			@Value("${app.ai.openai.request-timeout-seconds:${OPENAI_REQUEST_TIMEOUT_SECONDS:45}}") int requestTimeoutSeconds,
			@Value("${app.ai.openai.max-output-tokens:${OPENAI_MAX_OUTPUT_TOKENS:1200}}") int maxOutputTokens,
			@Value("${app.ai.openai.max-input-chars:${OPENAI_MAX_INPUT_CHARS:40000}}") int maxInputChars,
			@Value("${app.ai.openai.max-requests-per-minute:${OPENAI_MAX_REQUESTS_PER_MINUTE:20}}") int maxRequestsPerMinute) {
		this.enabled = enabled;
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.trim();
		this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl.trim();
		this.connectTimeoutSeconds = clamp(connectTimeoutSeconds, 1, 120, DEFAULT_CONNECT_TIMEOUT_SECONDS);
		this.requestTimeoutSeconds = clamp(requestTimeoutSeconds, 2, 300, DEFAULT_REQUEST_TIMEOUT_SECONDS);
		this.maxOutputTokens = clamp(maxOutputTokens, 64, 16_384, DEFAULT_MAX_OUTPUT_TOKENS);
		this.maxInputChars = clamp(maxInputChars, 1_000, 120_000, DEFAULT_MAX_INPUT_CHARS);
		this.maxRequestsPerMinute = clamp(maxRequestsPerMinute, 1, 600, DEFAULT_MAX_REQUESTS_PER_MINUTE);
		validateEndpointUri(this.baseUrl);
	}

	@PostConstruct
	void logStartupDiagnostics() {
		if (!enabled) {
			log.info("OpenAI provider is explicitly DISABLED (OPENAI_ENABLED=false). AI features will return HTTP 503.");
			return;
		}
		if (!hasApiKey()) {
			log.info("OpenAI provider initialized WITHOUT API key (OPENAI_API_KEY is blank). "
					+ "Non-AI features are active; AI endpoints will return HTTP 503 until configured.");
			return;
		}
		log.info("OpenAI provider configured and ready [model={}, connectTimeout={}s, requestTimeout={}s, maxOutputTokens={}, maxRequestsPerMinute={}].",
				model, connectTimeoutSeconds, requestTimeoutSeconds, maxOutputTokens, maxRequestsPerMinute);
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

	public int getMaxInputChars() {
		return maxInputChars;
	}

	public int getMaxRequestsPerMinute() {
		return maxRequestsPerMinute;
	}

	private static int clamp(int value, int min, int max, int fallback) {
		if (value < min || value > max) {
			return fallback;
		}
		return value;
	}

	private static void validateEndpointUri(String url) {
		try {
			URI uri = URI.create(url);
			String scheme = uri.getScheme();
			if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
				throw new IllegalArgumentException("OpenAI base URL must use http or https scheme");
			}
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException("Invalid OPENAI_BASE_URL configuration: " + ex.getMessage());
		}
	}

	@Override
	public String toString() {
		return "OpenAiProperties[enabled=" + enabled
				+ ", apiKeyConfigured=" + hasApiKey()
				+ ", model=" + model
				+ ", baseUrl=" + baseUrl
				+ ", connectTimeoutSeconds=" + connectTimeoutSeconds
				+ ", requestTimeoutSeconds=" + requestTimeoutSeconds
				+ ", maxOutputTokens=" + maxOutputTokens
				+ ", maxInputChars=" + maxInputChars
				+ ", maxRequestsPerMinute=" + maxRequestsPerMinute + "]";
	}
}
