package br.com.estudos.estudos_backend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class OpenAiPropertiesTest {

	@Test
	void defaultsAndUnconfiguredBehaviorAreSafeWithoutApiKey() {
		OpenAiProperties props = new OpenAiProperties(
				true,
				"   ",
				"",
				"",
				0,
				-5,
				0,
				50,
				0);

		assertTrue(props.isEnabled());
		assertFalse(props.hasApiKey());
		assertFalse(props.isConfigured());
		assertEquals(OpenAiProperties.DEFAULT_MODEL, props.getModel());
		assertEquals(OpenAiProperties.DEFAULT_BASE_URL, props.getBaseUrl());
		assertEquals(OpenAiProperties.DEFAULT_CONNECT_TIMEOUT_SECONDS, props.getConnectTimeoutSeconds());
		assertEquals(OpenAiProperties.DEFAULT_REQUEST_TIMEOUT_SECONDS, props.getRequestTimeoutSeconds());
		assertEquals(OpenAiProperties.DEFAULT_MAX_OUTPUT_TOKENS, props.getMaxOutputTokens());
		assertEquals(OpenAiProperties.DEFAULT_MAX_INPUT_CHARS, props.getMaxInputChars());
		assertEquals(OpenAiProperties.DEFAULT_MAX_REQUESTS_PER_MINUTE, props.getMaxRequestsPerMinute());

		OpenAiTutorProvider provider = new OpenAiTutorProvider(props);
		assertFalse(provider.isConfigured());
		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> provider.generateReply("sys", "oi"));
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
	}

	@Test
	void neverExposesSecretApiKeyInToStringAndRespectsDisabledFlag() {
		String fakeSecret = "sk-test-super-secret-value-99999";
		OpenAiProperties disabledProps = new OpenAiProperties(
				false,
				fakeSecret,
				"gpt-4o-mini",
				"https://api.openai.com/v1/chat/completions",
				10,
				45,
				1200,
				40000,
				20);

		assertFalse(disabledProps.toString().contains(fakeSecret));
		assertFalse(disabledProps.isConfigured());

		OpenAiTutorProvider provider = new OpenAiTutorProvider(disabledProps);
		assertFalse(provider.isConfigured());
		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> provider.generateReply("sys", "oi"));
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
	}

	@Test
	void rejectsInvalidBaseUrlSchemeAtStartup() {
		assertThrows(IllegalStateException.class, () -> new OpenAiProperties(
				true,
				"",
				"gpt-4o-mini",
				"ftp://api.openai.com/v1/chat/completions",
				10,
				45,
				1200,
				40000,
				20));
	}

	@Test
	@SuppressWarnings("unchecked")
	void openAiTutorProviderUsesInjectedHttpClientAndSupportsStructuredAiTutorRequestOffline() throws Exception {
		OpenAiProperties props = new OpenAiProperties(
				true,
				"sk-fake-offline-test-key",
				"gpt-4o-mini",
				"https://api.openai.com/v1/chat/completions",
				10,
				45,
				900,
				40000,
				20);

		HttpClient mockHttpClient = mock(HttpClient.class);
		HttpResponse<String> mockResponse = mock(HttpResponse.class);
		when(mockResponse.statusCode()).thenReturn(200);
		when(mockResponse.body()).thenReturn("""
				{
				  "model": "gpt-4o-mini-2024-07-18",
				  "choices": [
				    {
				      "message": {
				        "role": "assistant",
				        "content": "{\\"prompt\\":\\"Pergunta gerada\\"}"
				      }
				    }
				  ]
				}
				""");
		when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(mockResponse);

		OpenAiTutorProvider provider = new OpenAiTutorProvider(props, mockHttpClient);
		assertTrue(provider.isConfigured());

		AiTutorProvider.AiTutorRequest request = new AiTutorProvider.AiTutorRequest(
				"Instrução de sistema",
				List.of(new AiTutorProvider.AiTutorMessage("user", "Contexto prévio")),
				"Prompt principal",
				true,
				600);

		AiTutorProvider.AiTutorReply reply = provider.generateReply(request);

		assertEquals("{\"prompt\":\"Pergunta gerada\"}", reply.answer());
		assertEquals("OpenAI", reply.providerName());
		assertEquals("gpt-4o-mini-2024-07-18", reply.modelName());

		ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
		org.mockito.Mockito.verify(mockHttpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
		HttpRequest sentRequest = requestCaptor.getValue();
		assertEquals("https://api.openai.com/v1/chat/completions", sentRequest.uri().toString());
		assertTrue(sentRequest.headers().firstValue("Authorization").orElse("").startsWith("Bearer "));
	}
}
