package br.com.estudos.estudos_backend.ai;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorMessage;
import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorRequest;

/**
 * Google Gemini (Google AI Studio) via REST {@code models/{modelo}:generateContent}, com o {@link HttpClient}
 * nativo. Configuração em {@link GeminiProperties}; chave só em {@code GEMINI_API_KEY}.
 */
@Component
public class GeminiModelProvider implements AiModelProvider {

	static final String QUOTA_DAILY_MESSAGE = "A cota diária gratuita do Gemini foi esgotada. Ela é renovada à "
			+ "meia-noite do horário do Pacífico (por volta das 4h ou 5h em Brasília). Tente novamente depois "
			+ "disso ou configure outro provedor em Configurações › Provedores de IA.";
	static final String QUOTA_PER_MINUTE_MESSAGE = "Limite de requisições por minuto do Gemini atingido. "
			+ "Aguarde cerca de um minuto e tente novamente.";
	static final String QUOTA_GENERIC_MESSAGE = "Limite de uso do plano gratuito do Gemini atingido. Aguarde um "
			+ "minuto e tente novamente; se continuar, a cota diária acabou e é renovada à meia-noite do horário "
			+ "do Pacífico (por volta das 4h ou 5h em Brasília).";

	private static final int VIDEO_TIMEOUT_SECONDS = 300;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final GeminiProperties properties;
	private final HttpClient httpClient;
	private final Object rateLimitLock = new Object();
	private final ArrayDeque<Long> requestTimestamps = new ArrayDeque<>();

	@Autowired
	public GeminiModelProvider(GeminiProperties properties) {
		this(properties, HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
				.build());
	}

	public GeminiModelProvider(GeminiProperties properties, HttpClient httpClient) {
		this.properties = properties;
		this.httpClient = httpClient;
	}

	@Override
	public String id() {
		return "gemini";
	}

	@Override
	public String displayName() {
		return "Google Gemini";
	}

	@Override
	public boolean isConfigured() {
		return properties.isConfigured();
	}

	@Override
	public String defaultModel() {
		return properties.getModel();
	}

	@Override
	public List<String> suggestedModels() {
		return List.of(properties.getModel(), "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash")
				.stream().distinct().toList();
	}

	@Override
	public AiModelResult generate(AiTutorRequest request, String requestedModel) {
		if (!isConfigured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"A integração com o Gemini não está configurada (GEMINI_API_KEY ausente).");
		}
		return send(buildBody(request, null), resolveModel(requestedModel), properties.getRequestTimeoutSeconds());
	}

	/**
	 * Gera uma resposta a partir de um vídeo público do YouTube enviado por URL ({@code fileData.fileUri}),
	 * sem baixar o vídeo. Recurso em preview no Gemini; só vídeos públicos.
	 */
	public AiModelResult generateFromYoutubeVideo(String videoUrl, AiTutorRequest request, String requestedModel) {
		if (!isConfigured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"A integração com o Gemini não está configurada (GEMINI_API_KEY ausente).");
		}
		// Vídeos longos demoram mais para serem analisados que uma resposta de texto.
		int timeoutSeconds = Math.max(properties.getRequestTimeoutSeconds(), VIDEO_TIMEOUT_SECONDS);
		return send(buildBody(request, videoUrl), resolveModel(requestedModel), timeoutSeconds);
	}

	private String resolveModel(String requestedModel) {
		return requestedModel == null || requestedModel.isBlank() ? properties.getModel() : requestedModel.trim();
	}

	private AiModelResult send(ObjectNode body, String resolvedModel, int timeoutSeconds) {
		checkLocalRateLimit();
		try {
			String url = properties.getBaseUrl() + URLEncoder.encode(resolvedModel, StandardCharsets.UTF_8)
					+ ":generateContent";
			HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(timeoutSeconds))
					.header("x-goog-api-key", properties.getApiKey())
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
					.build();
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			handleHttpErrors(response.statusCode(), response.body(), resolvedModel);
			return parseResponse(response.body(), resolvedModel);
		} catch (ResponseStatusException e) {
			throw e;
		} catch (HttpTimeoutException e) {
			throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Tempo esgotado ao aguardar o Gemini.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A requisição ao Gemini foi interrompida.");
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falha de rede ao conectar com o Gemini.");
		}
	}

	private ObjectNode buildBody(AiTutorRequest request, String youtubeVideoUrl) {
		ObjectNode body = objectMapper.createObjectNode();
		if (request.systemInstruction() != null && !request.systemInstruction().isBlank()) {
			body.putObject("systemInstruction").putArray("parts").addObject().put("text", request.systemInstruction());
		}
		ArrayNode contents = body.putArray("contents");
		for (AiTutorMessage message : request.conversationHistory()) {
			if (message == null || message.content() == null || message.content().isBlank()) {
				continue;
			}
			addContent(contents, "assistant".equals(message.role()) ? "model" : "user", message.content());
		}
		if (youtubeVideoUrl == null) {
			addContent(contents, "user", request.userPrompt());
		} else {
			ObjectNode content = contents.addObject();
			content.put("role", "user");
			ArrayNode parts = content.putArray("parts");
			parts.addObject().putObject("fileData").put("fileUri", youtubeVideoUrl);
			parts.addObject().put("text", request.userPrompt());
		}
		ObjectNode generationConfig = body.putObject("generationConfig");
		// Pedidos com limite explícito maior (ex.: anotações de vídeo) podem passar do padrão de texto.
		int maxTokens = request.maxOutputTokens() == null
				? properties.getMaxOutputTokens()
				: request.maxOutputTokens();
		generationConfig.put("maxOutputTokens", maxTokens);
		if (request.jsonObjectMode()) {
			generationConfig.put("responseMimeType", "application/json");
		}
		return body;
	}

	private AiModelResult parseResponse(String responseBody, String resolvedModel) {
		JsonNode root;
		try {
			root = objectMapper.readTree(responseBody);
		} catch (JsonProcessingException e) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Gemini retornou uma resposta em formato inválido.");
		}
		JsonNode candidate = root.path("candidates").path(0);
		String finishReason = candidate.path("finishReason").asText("");
		if ("SAFETY".equals(finishReason) || "PROHIBITED_CONTENT".equals(finishReason)
				|| root.path("promptFeedback").has("blockReason")) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
					"O Gemini bloqueou esta solicitação por política de segurança.");
		}
		StringBuilder text = new StringBuilder();
		for (JsonNode part : candidate.path("content").path("parts")) {
			if (!part.path("thought").asBoolean(false)) {
				text.append(part.path("text").asText(""));
			}
		}
		String answer = text.toString().trim();
		if (answer.isBlank()) {
			String reason = "MAX_TOKENS".equals(finishReason)
					? "O Gemini atingiu o limite de tokens antes de responder. Tente um pedido menor."
					: "O Gemini retornou uma resposta vazia.";
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason);
		}
		JsonNode usage = root.path("usageMetadata");
		Integer inputTokens = usage.has("promptTokenCount") ? usage.path("promptTokenCount").asInt() : null;
		Integer outputTokens = usage.has("candidatesTokenCount") ? usage.path("candidatesTokenCount").asInt() : null;
		String modelVersion = root.path("modelVersion").asText("");
		return new AiModelResult(answer, modelVersion.isBlank() ? resolvedModel : modelVersion, inputTokens, outputTokens);
	}

	private void checkLocalRateLimit() {
		int maxRpm = properties.getMaxRequestsPerMinute();
		long now = System.currentTimeMillis();
		synchronized (rateLimitLock) {
			while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() < now - 60_000L) {
				requestTimestamps.pollFirst();
			}
			if (requestTimestamps.size() >= maxRpm) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
						"Limite local de " + maxRpm + " requisições por minuto ao Gemini atingido. Aguarde alguns segundos.");
			}
			requestTimestamps.addLast(now);
		}
	}

	private static void addContent(ArrayNode contents, String role, String text) {
		ObjectNode content = contents.addObject();
		content.put("role", role);
		content.putArray("parts").addObject().put("text", text);
	}

	private static void handleHttpErrors(int status, String body, String model) {
		if (status >= 200 && status < 300) {
			return;
		}
		if (status == 401 || status == 403) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chave do Gemini inválida ou sem permissão.");
		}
		if (status == 404) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Modelo do Gemini não encontrado: " + model + ".");
		}
		if (status == 429) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, quotaMessage(body));
		}
		if (status >= 500) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Gemini está temporariamente indisponível (HTTP " + status + ").");
		}
		throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Gemini retornou uma resposta inesperada (HTTP " + status + ").");
	}

	/** Diferencia, pelo texto do erro 429, a cota diária (RPD) do limite por minuto (RPM/TPM). */
	static String quotaMessage(String body) {
		String text = body == null ? "" : body.toLowerCase(Locale.ROOT);
		if (text.contains("per day") || text.contains("perday") || text.contains("daily")) {
			return QUOTA_DAILY_MESSAGE;
		}
		if (text.contains("per minute") || text.contains("perminute")) {
			return QUOTA_PER_MINUTE_MESSAGE;
		}
		return QUOTA_GENERIC_MESSAGE;
	}
}
