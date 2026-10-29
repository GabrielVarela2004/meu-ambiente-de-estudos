package br.com.estudos.estudos_backend.chat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OpenAiTutorProvider implements AiTutorProvider {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final String apiKey;
	private final String model;

	public OpenAiTutorProvider(
			@Value("${OPENAI_API_KEY:}") String apiKey,
			@Value("${OPENAI_MODEL:gpt-4o-mini}") String model) {
		this.apiKey = apiKey;
		this.model = model;
	}

	@Override
	public String providerName() {
		return "OpenAI";
	}

	@Override
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

	@Override
	public AiTutorReply generateReply(String systemInstruction, String userPrompt) {
		if (!isConfigured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"OpenAI integration is not configured");
		}
		try {
			String payload = objectMapper.writeValueAsString(new Payload(model, new Message[] {
					new Message("system", systemInstruction),
					new Message("user", userPrompt)
			}));
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
					.header("Authorization", "Bearer " + apiKey)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(payload))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI provider could not answer");
			}
			JsonNode root = objectMapper.readTree(response.body());
			String content = root.path("choices").path(0).path("message").path("content").asText("");
			if (content.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI provider returned an empty answer");
			}
			return new AiTutorReply(content, providerName(), model);
		} catch (ResponseStatusException exception) {
			throw exception;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI provider request was interrupted");
		} catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI provider request failed");
		}
	}

	private record Payload(String model, Message[] messages) {}
	private record Message(String role, String content) {}
}
