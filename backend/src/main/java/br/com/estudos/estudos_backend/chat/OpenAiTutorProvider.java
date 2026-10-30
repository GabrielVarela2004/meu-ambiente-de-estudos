package br.com.estudos.estudos_backend.chat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.ai.AiModelProvider;

@Component
public class OpenAiTutorProvider implements AiTutorProvider, AiModelProvider {

	/** Teto para limites pedidos explicitamente (mesmo teto de OPENAI_MAX_OUTPUT_TOKENS). */
	private static final int MAX_REQUESTED_OUTPUT_TOKENS = 16_384;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient;
	private final OpenAiProperties properties;
	private final Object rateLimitLock = new Object();
	private final ArrayDeque<Long> requestTimestamps = new ArrayDeque<>();

	public OpenAiTutorProvider(String apiKey, String model) {
		this(new OpenAiProperties(
				true,
				apiKey,
				model,
				OpenAiProperties.DEFAULT_BASE_URL,
				OpenAiProperties.DEFAULT_CONNECT_TIMEOUT_SECONDS,
				OpenAiProperties.DEFAULT_REQUEST_TIMEOUT_SECONDS,
				OpenAiProperties.DEFAULT_MAX_OUTPUT_TOKENS,
				OpenAiProperties.DEFAULT_MAX_INPUT_CHARS,
				OpenAiProperties.DEFAULT_MAX_REQUESTS_PER_MINUTE));
	}

	@Autowired
	public OpenAiTutorProvider(OpenAiProperties properties) {
		this(
				properties,
				HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
						.build());
	}

	public OpenAiTutorProvider(OpenAiProperties properties, HttpClient httpClient) {
		this.properties = properties;
		this.httpClient = httpClient;
	}

	@Override
	public String providerName() {
		return "OpenAI";
	}

	@Override
	public String id() {
		return "openai";
	}

	@Override
	public String displayName() {
		return providerName();
	}

	@Override
	public String defaultModel() {
		return properties.getModel();
	}

	@Override
	public List<String> suggestedModels() {
		return List.of(properties.getModel(), "gpt-4o-mini", "gpt-4o", "gpt-4.1-mini").stream().distinct().toList();
	}

	@Override
	public boolean isConfigured() {
		return properties.isConfigured();
	}

	@Override
	public String configuredModel() {
		return properties.getModel();
	}

	@Override
	public AiTutorReply generateReply(String systemInstruction, String userPrompt) {
		return generateReply(AiTutorRequest.ofText(systemInstruction, userPrompt));
	}

	@Override
	public AiTutorReply generateReply(AiTutorRequest tutorRequest) {
		AiModelResult result = generate(tutorRequest, properties.getModel());
		return new AiTutorReply(result.text(), providerName(), result.model());
	}

	@Override
	public AiModelResult generate(AiTutorRequest tutorRequest, String requestedModel) {
		String model = requestedModel == null || requestedModel.isBlank() ? properties.getModel() : requestedModel.trim();
		if (!properties.isEnabled()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"A integração com a OpenAI está desabilitada no ambiente.");
		}
		if (!isConfigured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"A integração com a OpenAI não está configurada (OPENAI_API_KEY ausente ou em branco).");
		}

		validateInputLimits(tutorRequest);
		checkLocalRateLimit();

		try {
			List<Message> messages = new ArrayList<>();
			if (tutorRequest.systemInstruction() != null && !tutorRequest.systemInstruction().isBlank()) {
				messages.add(new Message("system", tutorRequest.systemInstruction()));
			}
			for (AiTutorMessage historyMsg : tutorRequest.conversationHistory()) {
				if (historyMsg != null && historyMsg.content() != null && !historyMsg.content().isBlank()) {
					messages.add(new Message(historyMsg.role(), historyMsg.content()));
				}
			}
			messages.add(new Message("user", tutorRequest.userPrompt()));

			// Limite explícito do pedido vale acima do padrão (ex.: várias questões numa chamada), até o teto da API.
			int maxTokens = tutorRequest.maxOutputTokens() != null && tutorRequest.maxOutputTokens() > 0
					? Math.min(tutorRequest.maxOutputTokens(), MAX_REQUESTED_OUTPUT_TOKENS)
					: properties.getMaxOutputTokens();

			Map<String, String> responseFormat = tutorRequest.jsonObjectMode()
					? Map.of("type", "json_object")
					: null;

			String payload = objectMapper.writeValueAsString(new Payload(
					model,
					messages.toArray(Message[]::new),
					maxTokens,
					responseFormat,
					supportsTemperature(model) ? tutorRequest.temperature() : null));

			HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl()))
					.timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
					.header("Authorization", "Bearer " + properties.getApiKey())
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(payload))
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			handleHttpErrors(response.statusCode());

			JsonNode root;
			try {
				root = objectMapper.readTree(response.body());
			} catch (Exception parseException) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
						"A resposta do provedor de IA não pôde ser interpretada como JSON válido.");
			}

			String content = root.path("choices").path(0).path("message").path("content").asText("");
			if (content.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
						"O provedor de IA retornou uma resposta vazia.");
			}

			String resolvedModel = root.path("model").asText(model);
			if (resolvedModel.isBlank()) {
				resolvedModel = model;
			}
			JsonNode usage = root.path("usage");
			Integer inputTokens = usage.has("prompt_tokens") ? usage.path("prompt_tokens").asInt() : null;
			Integer outputTokens = usage.has("completion_tokens") ? usage.path("completion_tokens").asInt() : null;
			return new AiModelResult(content, resolvedModel, inputTokens, outputTokens);
		} catch (ResponseStatusException exception) {
			throw exception;
		} catch (HttpTimeoutException exception) {
			throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
					"Tempo limite esgotado ao aguardar resposta do provedor de IA.");
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"A requisição ao provedor de IA foi interrompida.");
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Falha de rede ao conectar com o provedor de IA.");
		} catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"Falha inesperada ao comunicar com o provedor de IA.");
		}
	}

	private void validateInputLimits(AiTutorRequest tutorRequest) {
		int maxChars = properties.getMaxInputChars();
		int userPromptLen = tutorRequest.userPrompt() == null ? 0 : tutorRequest.userPrompt().length();
		if (userPromptLen > maxChars) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"A mensagem excede o limite máximo permitido de caracteres (" + maxChars + ").");
		}
	}

	/** Modelos de raciocínio (o1/o3/o4, gpt-5…) recusam temperatura; a família gpt-4 aceita. */
	static boolean supportsTemperature(String model) {
		return model != null && model.toLowerCase(java.util.Locale.ROOT).startsWith("gpt-4");
	}

	private void checkLocalRateLimit() {
		int maxRpm = properties.getMaxRequestsPerMinute();
		if (maxRpm <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		long windowStart = now - 60_000L;
		synchronized (rateLimitLock) {
			while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() < windowStart) {
				requestTimestamps.pollFirst();
			}
			if (requestTimestamps.size() >= maxRpm) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
						"Limite local de requisições por minuto para a IA excedido (" + maxRpm + "/min). Aguarde alguns segundos.");
			}
			requestTimestamps.addLast(now);
		}
	}

	private void handleHttpErrors(int statusCode) {
		if (statusCode >= 200 && statusCode < 300) {
			return;
		}
		if (statusCode == 401 || statusCode == 403) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Credenciais do provedor de IA inválidas ou não autorizadas.");
		}
		if (statusCode == 429) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"Limite de taxa (Rate Limit ou cota) da OpenAI atingido. Tente novamente em instantes.");
		}
		if (statusCode >= 500) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"O provedor de IA está temporariamente indisponível (HTTP " + statusCode + "). Tente novamente mais tarde.");
		}
		throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
				"O provedor de IA retornou uma resposta inesperada (HTTP " + statusCode + ").");
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record Payload(
			String model,
			Message[] messages,
			int max_completion_tokens,
			Map<String, String> response_format,
			Double temperature) {}

	private record Message(String role, String content) {}
}
