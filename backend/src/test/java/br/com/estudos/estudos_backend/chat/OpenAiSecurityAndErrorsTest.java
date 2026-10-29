package br.com.estudos.estudos_backend.chat;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiSecurityAndErrorsTest {

	private OpenAiProperties createProps(String apiKey, int maxRpm, int maxChars) {
		return new OpenAiProperties(
				true,
				apiKey,
				"gpt-4o-mini",
				"https://api.openai.com/v1/chat/completions",
				5,
				15,
				800,
				maxChars,
				maxRpm);
	}

	@SuppressWarnings("unchecked")
	private HttpResponse<String> mockResponse(int statusCode, String body) {
		HttpResponse<String> resp = mock(HttpResponse.class);
		when(resp.statusCode()).thenReturn(statusCode);
		when(resp.body()).thenReturn(body);
		return resp;
	}

	@Test
	@SuppressWarnings("unchecked")
	void sendsProperHeadersAndParsesSuccessfulResponse() throws Exception {
		HttpClient mockHttp = mock(HttpClient.class);
		OpenAiProperties props = createProps("sk-test-secret-123", 20, 10_000);
		OpenAiTutorProvider provider = new OpenAiTutorProvider(props, mockHttp);

		String jsonResponse = """
				{
					"id": "chatcmpl-123",
					"model": "gpt-4o-mini",
					"choices": [
						{
							"index": 0,
							"message": {
								"role": "assistant",
								"content": "Olá! Posso ajudar com seus estudos."
							}
						}
					]
				}
				""";
		HttpResponse<String> resp = mockResponse(200, jsonResponse);
		when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(resp);

		AiTutorProvider.AiTutorReply reply = provider.generateReply(
				new AiTutorProvider.AiTutorRequest(
						"Você é um tutor.",
						List.of(new AiTutorProvider.AiTutorMessage("user", "Oi")),
						"Como funciona o polimorfismo?",
						false,
						500));

		assertNotNull(reply);
		assertEquals("Olá! Posso ajudar com seus estudos.", reply.answer());
		assertEquals("OpenAI", reply.provider());
		assertEquals("gpt-4o-mini", reply.model());

		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		verify(mockHttp).send(captor.capture(), any());
		HttpRequest sent = captor.getValue();
		assertEquals("Bearer sk-test-secret-123", sent.headers().firstValue("Authorization").orElse(""));
		assertEquals("application/json", sent.headers().firstValue("Content-Type").orElse(""));
	}

	@Test
	@SuppressWarnings("unchecked")
	void maps401UnauthorizedTo503WithoutLeakingApiKey() throws Exception {
		HttpClient mockHttp = mock(HttpClient.class);
		OpenAiProperties props = createProps("sk-invalid-key", 20, 10_000);
		OpenAiTutorProvider provider = new OpenAiTutorProvider(props, mockHttp);

		HttpResponse<String> resp = mockResponse(401, "{\"error\":{\"message\":\"Incorrect API key\"}}");
		when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(resp);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> provider.generateReply("Instrução", "Pergunta"));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
		assertFalse(ex.getMessage().contains("sk-invalid-key"), "Chave de API nunca deve vazar na mensagem de erro");
	}

	@Test
	@SuppressWarnings("unchecked")
	void maps429RateLimitTo429TooManyRequests() throws Exception {
		HttpClient mockHttp = mock(HttpClient.class);
		OpenAiProperties props = createProps("sk-valid", 20, 10_000);
		OpenAiTutorProvider provider = new OpenAiTutorProvider(props, mockHttp);

		HttpResponse<String> resp = mockResponse(429, "{\"error\":{\"message\":\"Rate limit reached\"}}");
		when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(resp);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> provider.generateReply("Instrução", "Pergunta"));

		assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
	}

	@Test
	@SuppressWarnings("unchecked")
	void maps500ServerErrorTo502BadGateway() throws Exception {
		HttpClient mockHttp = mock(HttpClient.class);
		OpenAiProperties props = createProps("sk-valid", 20, 10_000);
		OpenAiTutorProvider provider = new OpenAiTutorProvider(props, mockHttp);

		HttpResponse<String> resp = mockResponse(500, "{\"error\":{\"message\":\"Internal error\"}}");
		when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(resp);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> provider.generateReply("Instrução", "Pergunta"));

		assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
	}

	@Test
	@SuppressWarnings("unchecked")
	void mapsHttpTimeoutTo504GatewayTimeout() throws Exception {
		HttpClient mockHttp = mock(HttpClient.class);
		OpenAiProperties props = createProps("sk-valid", 20, 10_000);
		OpenAiTutorProvider provider = new OpenAiTutorProvider(props, mockHttp);

		when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new HttpTimeoutException("Request timed out"));

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> provider.generateReply("Instrução", "Pergunta"));

		assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatusCode());
	}

	@Test
	@SuppressWarnings("unchecked")
	void mapsMalformedJsonReplyTo502BadGateway() throws Exception {
		HttpClient mockHttp = mock(HttpClient.class);
		OpenAiProperties props = createProps("sk-valid", 20, 10_000);
		OpenAiTutorProvider provider = new OpenAiTutorProvider(props, mockHttp);

		HttpResponse<String> resp = mockResponse(200, "Isto não é um JSON da OpenAI!");
		when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(resp);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> provider.generateReply("Instrução", "Pergunta"));

		assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
	}

	@Test
	@SuppressWarnings("unchecked")
	void mapsEmptyChoicesReplyTo502BadGateway() throws Exception {
		HttpClient mockHttp = mock(HttpClient.class);
		OpenAiProperties props = createProps("sk-valid", 20, 10_000);
		OpenAiTutorProvider provider = new OpenAiTutorProvider(props, mockHttp);

		HttpResponse<String> resp = mockResponse(200, "{\"choices\":[]}");
		when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(resp);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> provider.generateReply("Instrução", "Pergunta"));

		assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
	}

	@Test
	@SuppressWarnings("unchecked")
	void enforcesLocalRateLimiterWithoutExceedingMaxRpm() throws Exception {
		HttpClient mockHttp = mock(HttpClient.class);
		// Permite no máximo 2 requisições por minuto
		OpenAiProperties props = createProps("sk-valid", 2, 10_000);
		OpenAiTutorProvider provider = new OpenAiTutorProvider(props, mockHttp);

		String okJson = """
				{"choices":[{"message":{"content":"Ok"}}]}
				""";
		HttpResponse<String> resp = mockResponse(200, okJson);
		when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(resp);

		// Req 1: OK
		provider.generateReply("Sys", "User 1");
		// Req 2: OK
		provider.generateReply("Sys", "User 2");

		// Req 3: Deve ser bloqueada pelo rate limiter local com 429
		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> provider.generateReply("Sys", "User 3"));

		assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
		assertTrue(ex.getMessage().contains("2/min"));
	}

	@Test
	void rejectsInputExceedingMaxInputChars() throws Exception {
		HttpClient mockHttp = mock(HttpClient.class);
		// Limite mínimo clampado em 1_000 caracteres
		OpenAiProperties props = createProps("sk-valid", 20, 1_000);
		OpenAiTutorProvider provider = new OpenAiTutorProvider(props, mockHttp);

		String longPrompt = "A".repeat(1_001);
		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> provider.generateReply("Sys", longPrompt));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
		verify(mockHttp, never()).send(any(), any());
	}

	@Test
	void rejectsWhenApiKeyIsMissingWith503WithoutCallingNetwork() throws Exception {
		HttpClient mockHttp = mock(HttpClient.class);
		// Sem API key
		OpenAiProperties props = createProps("", 20, 10_000);
		OpenAiTutorProvider provider = new OpenAiTutorProvider(props, mockHttp);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> provider.generateReply("Sys", "Prompt"));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
		verify(mockHttp, never()).send(any(), any());
	}
}
