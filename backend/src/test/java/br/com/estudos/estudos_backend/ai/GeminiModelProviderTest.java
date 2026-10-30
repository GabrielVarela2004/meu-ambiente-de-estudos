package br.com.estudos.estudos_backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorRequest;

/** Gemini com o HTTP simulado: nenhuma chamada sai para a API real. */
class GeminiModelProviderTest {

	private static final String SUCCESS_BODY = """
			{"candidates":[{"content":{"role":"model","parts":[{"text":"{\\"ok\\":true}"}]},"finishReason":"STOP"}],
			 "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":5},"modelVersion":"gemini-3.8-flash"}
			""";

	private final HttpClient http = mock(HttpClient.class);

	@SuppressWarnings("unchecked")
	private void respond(int status, String body) throws Exception {
		HttpResponse<String> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(status);
		when(response.body()).thenReturn(body);
		when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
	}

	private GeminiModelProvider provider(String key) {
		return new GeminiModelProvider(GeminiProperties.withKey(key), http);
	}

	@Test
	@SuppressWarnings("unchecked")
	void successReturnsTextUsageAndSendsKeyInHeader() throws Exception {
		respond(200, SUCCESS_BODY);

		AiModelProvider.AiModelResult result = provider("chave-falsa")
				.generate(AiTutorRequest.ofJson("instrução", "pergunta"), null);

		assertEquals("{\"ok\":true}", result.text());
		assertEquals("gemini-3.8-flash", result.model());
		assertEquals(12, result.inputTokens());
		assertEquals(5, result.outputTokens());

		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
		HttpRequest sent = captor.getValue();
		assertTrue(sent.uri().toString().endsWith("/v1beta/models/gemini-3.8-flash:generateContent"));
		assertEquals("chave-falsa", sent.headers().firstValue("x-goog-api-key").orElseThrow());
		assertFalse(sent.uri().toString().contains("chave-falsa"), "a chave não pode ir na URL");
	}

	@Test
	@SuppressWarnings("unchecked")
	void withoutKeyIsUnavailableAndNeverCallsTheApi() throws Exception {
		GeminiModelProvider provider = provider("");

		assertFalse(provider.isConfigured());
		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> provider.generate(AiTutorRequest.ofText("s", "u"), null));
		assertEquals(503, ex.getStatusCode().value());
		verify(http, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	void dailyQuotaExhaustedHasClearMessage() throws Exception {
		respond(429, """
				{"error":{"code":429,"status":"RESOURCE_EXHAUSTED",
				 "message":"Quota exceeded for metric: generate_content_free_tier_requests, limit: GenerateRequestsPerDayPerProjectPerModel-FreeTier"}}
				""");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> provider("k").generate(AiTutorRequest.ofText("s", "u"), null));
		assertEquals(429, ex.getStatusCode().value());
		assertEquals(GeminiModelProvider.QUOTA_DAILY_MESSAGE, ex.getReason());
	}

	@Test
	void perMinuteLimitHasClearMessage() throws Exception {
		respond(429, """
				{"error":{"code":429,"status":"RESOURCE_EXHAUSTED",
				 "message":"Quota exceeded, limit: GenerateRequestsPerMinutePerProjectPerModel-FreeTier"}}
				""");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> provider("k").generate(AiTutorRequest.ofText("s", "u"), null));
		assertEquals(GeminiModelProvider.QUOTA_PER_MINUTE_MESSAGE, ex.getReason());
	}

	@Test
	@SuppressWarnings("unchecked")
	void timeoutIs504() throws Exception {
		when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new HttpTimeoutException("request timed out"));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> provider("k").generate(AiTutorRequest.ofText("s", "u"), null));
		assertEquals(504, ex.getStatusCode().value());
	}

	@Test
	void invalidJsonFromApiIs502() throws Exception {
		respond(200, "<html>erro no proxy</html>");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> provider("k").generate(AiTutorRequest.ofText("s", "u"), null));
		assertEquals(502, ex.getStatusCode().value());
		assertEquals("O Gemini retornou uma resposta em formato inválido.", ex.getReason());
	}

	@Test
	void localPerMinuteLimitBlocksBeforeCallingTheApi() throws Exception {
		respond(200, SUCCESS_BODY);
		GeminiProperties oneRequestPerMinute = new GeminiProperties(true, "k", GeminiProperties.DEFAULT_MODEL,
				GeminiProperties.DEFAULT_BASE_URL, 10, 60, 4096, 1);
		GeminiModelProvider provider = new GeminiModelProvider(oneRequestPerMinute, http);

		provider.generate(AiTutorRequest.ofText("s", "u"), null);
		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> provider.generate(AiTutorRequest.ofText("s", "u"), null));
		assertEquals(429, ex.getStatusCode().value());
	}

	@Test
	void jsonCutByTheTokenLimitHasClearMessage() throws Exception {
		respond(200, """
				{"candidates":[{"content":{"role":"model","parts":[{"text":"[{\\"prompt\\":\\"Explique"}]},"finishReason":"MAX_TOKENS"}],
				 "usageMetadata":{"promptTokenCount":4000,"candidatesTokenCount":1196}}
				""");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> provider("k").generate(AiTutorRequest.ofJson("s", "gere"), null));
		assertEquals(502, ex.getStatusCode().value());
		assertEquals(GeminiModelProvider.MAX_TOKENS_JSON_MESSAGE, ex.getReason());
	}

	@Test
	void textCutByTheTokenLimitIsStillReturned() throws Exception {
		respond(200, """
				{"candidates":[{"content":{"role":"model","parts":[{"text":"Resposta parcial"}]},"finishReason":"MAX_TOKENS"}]}
				""");

		assertEquals("Resposta parcial", provider("k").generate(AiTutorRequest.ofText("s", "u"), null).text());
	}

	@Test
	void keyNeverAppearsInToString() {
		assertFalse(GeminiProperties.withKey("segredo-123").toString().contains("segredo-123"));
	}
}
