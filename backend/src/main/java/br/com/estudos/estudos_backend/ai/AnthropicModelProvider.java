package br.com.estudos.estudos_backend.ai;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;

import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorMessage;
import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorRequest;

/** Claude (Anthropic) via SDK oficial {@code anthropic-java}. Chave lida de {@code ANTHROPIC_API_KEY}. */
@Component
public class AnthropicModelProvider implements AiModelProvider {

	private static final Logger log = LoggerFactory.getLogger(AnthropicModelProvider.class);
	static final String DEFAULT_MODEL = "claude-opus-5-5";
	private static final String JSON_ONLY_INSTRUCTION =
			"\n\nResponda somente com um objeto JSON válido, sem texto antes ou depois e sem blocos de código.";

	private final boolean enabled;
	private final String apiKey;
	private final String model;
	private final long maxOutputTokens;
	private final OutputConfig.Effort effort;
	private final Duration timeout;
	private volatile AnthropicClient client;

	public AnthropicModelProvider(
			@Value("${app.ai.anthropic.enabled:${ANTHROPIC_ENABLED:true}}") boolean enabled,
			@Value("${app.ai.anthropic.api-key:${ANTHROPIC_API_KEY:}}") String apiKey,
			@Value("${app.ai.anthropic.model:${ANTHROPIC_MODEL:" + DEFAULT_MODEL + "}}") String model,
			@Value("${app.ai.anthropic.max-output-tokens:${ANTHROPIC_MAX_OUTPUT_TOKENS:16000}}") long maxOutputTokens,
			@Value("${app.ai.anthropic.effort:${ANTHROPIC_EFFORT:low}}") String effort,
			@Value("${app.ai.anthropic.request-timeout-seconds:${ANTHROPIC_REQUEST_TIMEOUT_SECONDS:120}}") int timeoutSeconds) {
		this.enabled = enabled;
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
		this.maxOutputTokens = maxOutputTokens < 1024 ? 16000 : maxOutputTokens;
		this.effort = OutputConfig.Effort.of(effort == null || effort.isBlank() ? "low" : effort.trim().toLowerCase());
		this.timeout = Duration.ofSeconds(Math.max(10, timeoutSeconds));
	}

	@Override
	public String id() {
		return "anthropic";
	}

	@Override
	public String displayName() {
		return "Anthropic (Claude)";
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
		return List.of(model, "claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5").stream().distinct().toList();
	}

	@Override
	public AiModelResult generate(AiTutorRequest request, String requestedModel) {
		if (!isConfigured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"A integração com a Anthropic não está configurada (ANTHROPIC_API_KEY ausente).");
		}
		String resolvedModel = requestedModel == null || requestedModel.isBlank() ? model : requestedModel.trim();

		String system = request.systemInstruction() == null ? "" : request.systemInstruction();
		if (request.jsonObjectMode()) {
			system = system + JSON_ONLY_INSTRUCTION;
		}

		MessageCreateParams.Builder params = MessageCreateParams.builder()
				.model(resolvedModel)
				.maxTokens(maxOutputTokens)
				.outputConfig(OutputConfig.builder().effort(effort).build());
		if (!system.isBlank()) {
			params.system(system);
		}
		for (AiTutorMessage message : request.conversationHistory()) {
			if (message == null || message.content() == null || message.content().isBlank()) {
				continue;
			}
			if ("assistant".equals(message.role())) {
				params.addAssistantMessage(message.content());
			} else {
				params.addUserMessage(message.content());
			}
		}
		params.addUserMessage(request.userPrompt());

		Message response;
		try {
			response = client().messages().create(params.build());
		} catch (UnauthorizedException | PermissionDeniedException e) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Credenciais da Anthropic inválidas ou sem permissão.");
		} catch (NotFoundException e) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"Modelo da Anthropic não encontrado: " + resolvedModel + ".");
		} catch (RateLimitException e) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"Limite de taxa da Anthropic atingido. Tente novamente em instantes.");
		} catch (BadRequestException e) {
			log.warn("Anthropic rejeitou a requisição: {}", e.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"A Anthropic rejeitou a requisição gerada pela plataforma.");
		} catch (InternalServerException e) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"A Anthropic está temporariamente indisponível. Tente novamente mais tarde.");
		} catch (AnthropicServiceException e) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"A Anthropic retornou uma resposta inesperada (HTTP " + e.statusCode() + ").");
		} catch (AnthropicIoException e) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Falha de rede ou tempo esgotado ao conectar com a Anthropic.");
		}

		if (response.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
			// 422 permite que o roteador tente o próximo provedor da cadeia de fallback.
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
					"O modelo da Anthropic recusou esta solicitação.");
		}

		String text = response.content().stream()
				.flatMap(block -> block.text().stream())
				.map(TextBlock::text)
				.collect(Collectors.joining("\n"))
				.trim();
		if (request.jsonObjectMode()) {
			text = stripCodeFence(text);
		}
		if (text.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Anthropic retornou uma resposta vazia.");
		}
		return new AiModelResult(text, response.model().asString(),
				(int) response.usage().inputTokens(), (int) response.usage().outputTokens());
	}

	private AnthropicClient client() {
		AnthropicClient current = client;
		if (current == null) {
			synchronized (this) {
				current = client;
				if (current == null) {
					current = AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(timeout).build();
					client = current;
				}
			}
		}
		return current;
	}

	/** Remove cercas de código (```json ... ```) caso o modelo as inclua apesar da instrução. */
	static String stripCodeFence(String text) {
		String trimmed = text.trim();
		if (!trimmed.startsWith("```")) {
			return trimmed;
		}
		int firstNewline = trimmed.indexOf('\n');
		int lastFence = trimmed.lastIndexOf("```");
		if (firstNewline < 0 || lastFence <= firstNewline) {
			return trimmed;
		}
		return trimmed.substring(firstNewline + 1, lastFence).trim();
	}
}
