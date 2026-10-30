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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorMessage;
import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorRequest;

/** Google Gemini via API REST {@code generateContent}. Chave lida de {@code GEMINI_API_KEY}. */
@Component
public class GeminiModelProvider implements AiModelProvider {

	static final String DEFAULT_MODEL = "gemini-2.5-flash";
	private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final boolean enabled;
	private final String apiKey;
	private final String model;
	private final int maxOutputTokens;
	private final Duration timeout;
	private final HttpClient httpClient;

	public GeminiModelProvider(
			@Value("${app.ai.gemini.enabled:${GEMINI_ENABLED:true}}") boolean enabled,
			@Value("${app.ai.gemini.api-key:${GEMINI_API_KEY:}}") String apiKey,
			@Value("${app.ai.gemini.model:${GEMINI_MODEL:" + DEFAULT_MODEL + "}}") String model,
			@Value("${app.ai.gemini.max-output-tokens:${GEMINI_MAX_OUTPUT_TOKENS:4096}}") int maxOutputTokens,
			@Value("${app.ai.gemini.request-timeout-seconds:${GEMINI_REQUEST_TIMEOUT_SECONDS:60}}") int timeoutSeconds) {
		this.enabled = enabled;
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
		this.maxOutputTokens = maxOutputTokens < 256 ? 4096 : maxOutputTokens;
		this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
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
		return enabled && !apiKey.isBlank();
	}

	@Override
	public String defaultModel() {
		return model;
	}

	@Override
	public List<String> suggestedModels() {
		return List.of(model, "gemini-2.5-flash", "gemini-2.5-pro").stream().distinct().toList();
	}

	@Override
	public AiModelResult generate(AiTutorRequest request, String requestedModel) {
		if (!isConfigured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"A integração com o Gemini não está configurada (GEMINI_API_KEY ausente).");
		}
		String resolvedModel = requestedModel == null || requestedModel.isBlank() ? model : requestedModel.trim();

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
		addContent(contents, "user", request.userPrompt());
		ObjectNode generationConfig = body.putObject("generationConfig");
		generationConfig.put("maxOutputTokens", maxOutputTokens);
		if (request.jsonObjectMode()) {
			generationConfig.put("responseMimeType", "application/json");
		}

		try {
			String url = BASE_URL + URLEncoder.encode(resolvedModel, StandardCharsets.UTF_8) + ":generateContent";
			HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
					.timeout(timeout)
					.header("x-goog-api-key", apiKey)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
					.build();
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			handleHttpErrors(response.statusCode(), resolvedModel);

			JsonNode root = objectMapper.readTree(response.body());
			JsonNode candidate = root.path("candidates").path(0);
			String finishReason = candidate.path("finishReason").asText("");
			if ("SAFETY".equals(finishReason) || "PROHIBITED_CONTENT".equals(finishReason)
					|| root.path("promptFeedback").has("blockReason")) {
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
						"O Gemini bloqueou esta solicitação por política de segurança.");
			}
			StringBuilder text = new StringBuilder();
			for (JsonNode part : candidate.path("content").path("parts")) {
				text.append(part.path("text").asText(""));
			}
			String answer = text.toString().trim();
			if (answer.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Gemini retornou uma resposta vazia.");
			}
			JsonNode usage = root.path("usageMetadata");
			Integer inputTokens = usage.has("promptTokenCount") ? usage.path("promptTokenCount").asInt() : null;
			Integer outputTokens = usage.has("candidatesTokenCount") ? usage.path("candidatesTokenCount").asInt() : null;
			String modelVersion = root.path("modelVersion").asText(resolvedModel);
			return new AiModelResult(answer, modelVersion.isBlank() ? resolvedModel : modelVersion, inputTokens, outputTokens);
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

	private static void addContent(ArrayNode contents, String role, String text) {
		ObjectNode content = contents.addObject();
		content.put("role", role);
		content.putArray("parts").addObject().put("text", text);
	}

	private static void handleHttpErrors(int status, String model) {
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
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limite de taxa ou cota do Gemini atingido.");
		}
		if (status >= 500) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Gemini está temporariamente indisponível (HTTP " + status + ").");
		}
		throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Gemini retornou uma resposta inesperada (HTTP " + status + ").");
	}
}
