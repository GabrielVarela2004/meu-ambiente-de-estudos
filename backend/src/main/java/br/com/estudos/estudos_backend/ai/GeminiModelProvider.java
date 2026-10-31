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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(GeminiModelProvider.class);

	static final String QUOTA_DAILY_MESSAGE = "A cota diária gratuita do Gemini foi esgotada. Ela é renovada à "
			+ "meia-noite do horário do Pacífico (por volta das 4h ou 5h em Brasília). Tente novamente depois "
			+ "disso ou configure outro provedor em Configurações › Provedores de IA.";
	static final String QUOTA_PER_MINUTE_MESSAGE = "Limite de requisições por minuto do Gemini atingido. "
			+ "Aguarde cerca de um minuto e tente novamente.";
	static final String QUOTA_GENERIC_MESSAGE = "Limite de uso do plano gratuito do Gemini atingido. Aguarde um "
			+ "minuto e tente novamente; se continuar, a cota diária acabou e é renovada à meia-noite do horário "
			+ "do Pacífico (por volta das 4h ou 5h em Brasília).";

	static final String MAX_TOKENS_JSON_MESSAGE = "A resposta do Gemini foi cortada pelo limite de tokens de saída "
			+ "(GEMINI_MAX_OUTPUT_TOKENS). Peça menos itens de uma vez ou aumente o limite no .env.";

	/** Tentativas por chamada: 429 por minuto e 503 são transitórios (backoff + jitter, máx. 3 tentativas). */
	static final int MAX_ATTEMPTS = 3;
	/** Teto de cada pausa de backoff e do Retry-After respeitado (segundos). */
	private static final long BACKOFF_CAP_MS = 10_000L;
	private static final long RETRY_AFTER_CAP_MS = 60_000L;
	/**
	 * RPM do nível gratuito por modelo (AI Studio, conferido em 2026-10-31). Os demais usam
	 * {@code GEMINI_MAX_REQUESTS_PER_MINUTE}. A fila local evita estourar o limite ao gerar várias questões.
	 */
	private static final Map<String, Integer> FREE_TIER_RPM = Map.of(
			"gemini-3.8-flash", 5,
			"gemini-3.5-flash-lite", 15);

	/** Tipo de cota que o Gemini reporta num 429: o tratamento é diferente para cada uma. */
	enum GeminiQuotaLimit { DAILY, PER_MINUTE, GENERIC }

	private static final int VIDEO_TIMEOUT_SECONDS = 300;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final GeminiProperties properties;
	private final HttpClient httpClient;
	private final GeminiRateLimiter rateLimiter;
	private final GeminiRateLimiter.Sleeper sleeper;

	@Autowired
	public GeminiModelProvider(GeminiProperties properties) {
		this(properties, HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
				.build());
	}

	public GeminiModelProvider(GeminiProperties properties, HttpClient httpClient) {
		this(properties, httpClient,
				new GeminiRateLimiter(properties.getMaxRequestsPerMinute(), FREE_TIER_RPM,
						System::currentTimeMillis, Thread::sleep),
				Thread::sleep);
	}

	/** Testes injetam fila e pausa falsas: nada dorme de verdade e o relógio é controlado. */
	GeminiModelProvider(GeminiProperties properties, HttpClient httpClient, GeminiRateLimiter rateLimiter,
			GeminiRateLimiter.Sleeper sleeper) {
		this.properties = properties;
		this.httpClient = httpClient;
		this.rateLimiter = rateLimiter;
		this.sleeper = sleeper;
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
		String model = resolveModel(requestedModel);
		return send(buildBody(request, null, model), model, properties.getRequestTimeoutSeconds(), request.jsonObjectMode());
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
		String model = resolveModel(requestedModel);
		return send(buildBody(request, videoUrl, model), model, timeoutSeconds, request.jsonObjectMode());
	}

	private String resolveModel(String requestedModel) {
		return requestedModel == null || requestedModel.isBlank() ? properties.getModel() : requestedModel.trim();
	}

	private AiModelResult send(ObjectNode body, String resolvedModel, int timeoutSeconds, boolean jsonMode) {
		String model = resolvedModel;
		boolean switchedToFallback = false;
		try {
			for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
				// Fila por modelo: espera uma vaga na janela de 1 minuto em vez de estourar o RPM.
				rateLimiter.acquire(model);
				String url = properties.getBaseUrl() + URLEncoder.encode(model, StandardCharsets.UTF_8)
						+ ":generateContent";
				HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
						.timeout(Duration.ofSeconds(timeoutSeconds))
						.header("x-goog-api-key", properties.getApiKey())
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
						.build();
				HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
				int status = response.statusCode();

				if (status == 429) {
					GeminiQuotaLimit quota = quotaType(response.body());
					String fallback = properties.getFallbackModel();
					// Cota diária (RPD) esgotada: repete automaticamente com o modelo leve do plano gratuito.
					if (quota == GeminiQuotaLimit.DAILY && !switchedToFallback && fallback != null
							&& !fallback.isBlank() && !fallback.equals(model)) {
						log.warn("Cota diária do modelo {} atingida, usando o modelo {}.", model, fallback);
						switchedToFallback = true;
						model = fallback;
						attempt--; // a troca de modelo não consome tentativa
						continue;
					}
					if (quota == GeminiQuotaLimit.PER_MINUTE && attempt < MAX_ATTEMPTS) {
						log.warn("Limite por minuto do modelo {} atingido (tentativa {} de {}); tentando novamente…",
								model, attempt, MAX_ATTEMPTS);
						sleepBackoff(attempt, retryAfterMillis(response));
						continue;
					}
					handleHttpErrors(status, response.body(), model);
				}
				if (status == 503 && attempt < MAX_ATTEMPTS) {
					log.warn("Gemini temporariamente indisponível (HTTP 503, tentativa {} de {}); tentando novamente…",
							attempt, MAX_ATTEMPTS);
					sleepBackoff(attempt, retryAfterMillis(response));
					continue;
				}
				handleHttpErrors(status, response.body(), model);
				return parseResponse(response.body(), model, jsonMode);
			}
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"O Gemini permaneceu indisponível após " + MAX_ATTEMPTS + " tentativas. Tente de novo em instantes.");
		} catch (ResponseStatusException e) {
			throw e;
		} catch (HttpTimeoutException e) {
			throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Tempo esgotado ao aguardar o Gemini.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A requisição ao Gemini foi interrompida.");
		} catch (IOException e) {
			// Só o tipo e a mensagem do erro (sem a chave): ex. certificado do Cloudflare WARP, DNS, conexão recusada.
			log.warn("Falha de rede ao chamar o Gemini: {}", e.toString());
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falha de rede ao conectar com o Gemini.");
		}
	}

	/** Backoff exponencial com jitter (1–2 s, 2–4 s…), respeitando Retry-After quando o Gemini envia. */
	private void sleepBackoff(int attempt, Long retryAfterMillis) throws InterruptedException {
		long delay;
		if (retryAfterMillis != null) {
			delay = Math.min(retryAfterMillis, RETRY_AFTER_CAP_MS);
		} else {
			long base = 1000L << (attempt - 1);
			double jitter = 0.5 + ThreadLocalRandom.current().nextDouble();
			delay = (long) Math.min(base * jitter, BACKOFF_CAP_MS);
		}
		sleeper.sleep(Math.max(1, delay));
	}

	private static Long retryAfterMillis(HttpResponse<String> response) {
		return response.headers().firstValue("Retry-After")
				.flatMap(value -> {
					try {
						return Optional.of(Long.parseLong(value.trim()) * 1000L);
					} catch (NumberFormatException e) {
						return Optional.<Long>empty();
					}
				})
				.orElse(null);
	}

	private ObjectNode buildBody(AiTutorRequest request, String youtubeVideoUrl, String model) {
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
		// Gemini 3.6+ ignora temperature (e passará a recusar); só os 2.x recebem o valor pedido.
		if (request.temperature() != null && model.startsWith("gemini-2")) {
			generationConfig.put("temperature", request.temperature());
		}
		if (request.jsonObjectMode()) {
			generationConfig.put("responseMimeType", "application/json");
			// Saída estruturada: o esquema garante o formato (a validação real continua no backend).
			if (request.responseSchema() != null && !request.responseSchema().isBlank()) {
				JsonNode schema = toGeminiSchema(request.responseSchema());
				if (schema != null) {
					generationConfig.set("responseSchema", schema);
				}
			}
		}
		return body;
	}

	/**
	 * Converte o esquema comum (minúsculo) para o formato do Gemini (tipos em maiúsculas, ex.:
	 * {@code "type":"array"} → {@code "ARRAY"}). Esquema inválido devolve null: segue só com responseMimeType.
	 */
	private JsonNode toGeminiSchema(String schemaJson) {
		try {
			return uppercaseTypes(objectMapper.readTree(schemaJson));
		} catch (JsonProcessingException e) {
			log.warn("Esquema JSON de resposta inválido; seguindo sem responseSchema.");
			return null;
		}
	}

	private JsonNode uppercaseTypes(JsonNode node) {
		if (node.isObject()) {
			ObjectNode copy = objectMapper.createObjectNode();
			node.fields().forEachRemaining(entry -> {
				if ("type".equals(entry.getKey()) && entry.getValue().isTextual()) {
					copy.put(entry.getKey(), entry.getValue().asText().toUpperCase(Locale.ROOT));
				} else {
					copy.set(entry.getKey(), uppercaseTypes(entry.getValue()));
				}
			});
			return copy;
		}
		if (node.isArray()) {
			ArrayNode copy = objectMapper.createArrayNode();
			node.forEach(item -> copy.add(uppercaseTypes(item)));
			return copy;
		}
		return node;
	}

	private AiModelResult parseResponse(String responseBody, String resolvedModel, boolean jsonMode) {
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
		// JSON cortado pelo limite de tokens não tem conserto: melhor dizer o motivo do que "resposta inválida".
		if (jsonMode && "MAX_TOKENS".equals(finishReason)) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, MAX_TOKENS_JSON_MESSAGE);
		}
		JsonNode usage = root.path("usageMetadata");
		Integer inputTokens = usage.has("promptTokenCount") ? usage.path("promptTokenCount").asInt() : null;
		Integer outputTokens = usage.has("candidatesTokenCount") ? usage.path("candidatesTokenCount").asInt() : null;
		String modelVersion = root.path("modelVersion").asText("");
		return new AiModelResult(answer, modelVersion.isBlank() ? resolvedModel : modelVersion, inputTokens, outputTokens);
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
		return switch (quotaType(body)) {
			case DAILY -> QUOTA_DAILY_MESSAGE;
			case PER_MINUTE -> QUOTA_PER_MINUTE_MESSAGE;
			case GENERIC -> QUOTA_GENERIC_MESSAGE;
		};
	}

	/** Classifica o 429 para decidir entre trocar de modelo (diária), retentar (por minuto) ou falhar. */
	static GeminiQuotaLimit quotaType(String body) {
		String text = body == null ? "" : body.toLowerCase(Locale.ROOT);
		if (text.contains("per day") || text.contains("perday") || text.contains("daily")) {
			return GeminiQuotaLimit.DAILY;
		}
		if (text.contains("per minute") || text.contains("perminute")) {
			return GeminiQuotaLimit.PER_MINUTE;
		}
		return GeminiQuotaLimit.GENERIC;
	}
}
