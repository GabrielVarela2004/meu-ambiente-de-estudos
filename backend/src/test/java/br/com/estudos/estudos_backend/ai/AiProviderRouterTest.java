package br.com.estudos.estudos_backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorReply;
import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorRequest;

class AiProviderRouterTest {

	private AiProviderSettingsRepository settingsRepository;
	private AiOperationRouteRepository routeRepository;
	private AiUsageRecordRepository usageRepository;
	private List<AiUsageRecord> recorded;

	@BeforeEach
	void setUp() {
		settingsRepository = mock(AiProviderSettingsRepository.class);
		routeRepository = mock(AiOperationRouteRepository.class);
		usageRepository = mock(AiUsageRecordRepository.class);
		recorded = new ArrayList<>();
		when(settingsRepository.findById(anyString())).thenReturn(Optional.empty());
		when(routeRepository.findById(any())).thenReturn(Optional.empty());
		when(usageRepository.save(any())).thenAnswer(inv -> {
			recorded.add(inv.getArgument(0));
			return inv.getArgument(0);
		});
	}

	private AiProviderRouter router(AiModelProvider... providers) {
		AiSettingsService settings = new AiSettingsService(List.of(providers), settingsRepository, routeRepository, usageRepository);
		return new AiProviderRouter(settings, new AiUsageService(usageRepository));
	}

	private void route(AiOperation operation, String providerId, boolean fallback) {
		when(routeRepository.findById(eq(operation))).thenReturn(Optional.of(new AiOperationRoute(operation, providerId, fallback)));
	}

	@Test
	void usesProviderRoutedForTheCurrentOperation() {
		FakeProvider openai = FakeProvider.ok("openai", "resposta openai");
		FakeProvider anthropic = FakeProvider.ok("anthropic", "resposta claude");
		route(AiOperation.QUESTION_GENERATION, "anthropic", false);

		AiTutorReply reply = AiOperationContext.call(AiOperation.QUESTION_GENERATION,
				() -> router(openai, anthropic).generateReply(AiTutorRequest.ofText("sys", "oi")));

		assertEquals("resposta claude", reply.answer());
		assertEquals("Fake anthropic", reply.providerName());
		assertEquals(0, openai.calls);
		assertEquals(1, recorded.size());
		assertEquals(AiOperation.QUESTION_GENERATION, recorded.get(0).getOperation());
		assertTrue(recorded.get(0).isSuccess());
		assertEquals(10, recorded.get(0).getInputTokens());
	}

	@Test
	void fallsBackToNextProviderOnTransientFailure() {
		FakeProvider openai = FakeProvider.failing("openai", HttpStatus.TOO_MANY_REQUESTS);
		FakeProvider gemini = FakeProvider.ok("gemini", "resposta gemini");
		route(AiOperation.CHAT, "openai", true);

		AiTutorReply reply = router(openai, gemini).generateReply(AiTutorRequest.ofText("sys", "oi"));

		assertEquals("resposta gemini", reply.answer());
		assertEquals(2, recorded.size());
		assertFalse(recorded.get(0).isSuccess());
		assertEquals(429, recorded.get(0).getHttpStatus());
		assertTrue(recorded.get(1).isSuccess());
	}

	@Test
	void doesNotFallBackWhenFallbackIsDisabled() {
		FakeProvider openai = FakeProvider.failing("openai", HttpStatus.BAD_GATEWAY);
		FakeProvider gemini = FakeProvider.ok("gemini", "resposta gemini");
		route(AiOperation.CHAT, "openai", false);

		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> router(openai, gemini).generateReply(AiTutorRequest.ofText("sys", "oi")));

		assertEquals(502, error.getStatusCode().value());
		assertEquals(0, gemini.calls);
	}

	@Test
	void doesNotFallBackOnClientErrors() {
		FakeProvider openai = FakeProvider.failing("openai", HttpStatus.BAD_REQUEST);
		FakeProvider gemini = FakeProvider.ok("gemini", "resposta gemini");
		route(AiOperation.CHAT, "openai", true);

		assertThrows(ResponseStatusException.class,
				() -> router(openai, gemini).generateReply(AiTutorRequest.ofText("sys", "oi")));
		assertEquals(0, gemini.calls);
	}

	@Test
	void skipsProviderThatReachedDailyLimitOrIsDisabled() {
		FakeProvider openai = FakeProvider.ok("openai", "resposta openai");
		FakeProvider anthropic = FakeProvider.ok("anthropic", "resposta claude");
		FakeProvider gemini = FakeProvider.ok("gemini", "resposta gemini");
		AiProviderSettings limited = new AiProviderSettings("openai");
		limited.update(true, null, 5);
		AiProviderSettings disabled = new AiProviderSettings("anthropic");
		disabled.update(false, null, null);
		when(settingsRepository.findById("openai")).thenReturn(Optional.of(limited));
		when(settingsRepository.findById("anthropic")).thenReturn(Optional.of(disabled));
		when(usageRepository.countByProviderIdAndCreatedAtGreaterThanEqual(eq("openai"), any())).thenReturn(5L);
		route(AiOperation.CHAT, "openai", true);

		AiTutorReply reply = router(openai, anthropic, gemini).generateReply(AiTutorRequest.ofText("sys", "oi"));

		assertEquals("resposta gemini", reply.answer());
		assertEquals(0, openai.calls);
		assertEquals(0, anthropic.calls);
	}

	@Test
	void usesModelOverrideFromSettings() {
		FakeProvider anthropic = FakeProvider.ok("anthropic", "ok");
		AiProviderSettings settings = new AiProviderSettings("anthropic");
		settings.update(true, "claude-haiku-5-5", null);
		when(settingsRepository.findById("anthropic")).thenReturn(Optional.of(settings));
		route(AiOperation.CHAT, "anthropic", false);

		AiTutorReply reply = router(anthropic).generateReply(AiTutorRequest.ofText("sys", "oi"));

		assertEquals("claude-haiku-5-5", anthropic.lastModel);
		assertEquals("claude-haiku-5-5", reply.modelName());
	}

	@Test
	void reportsUnavailableWhenNoProviderHasKey() {
		FakeProvider openai = FakeProvider.unconfigured("openai");
		AiProviderRouter router = router(openai);

		assertFalse(router.isConfigured());
		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> router.generateReply(AiTutorRequest.ofText("sys", "oi")));
		assertEquals(503, error.getStatusCode().value());
		assertTrue(error.getReason().contains("sem chave de API configurada"));
	}

	/** Provedor falso configurável para os cenários de roteamento. */
	private static final class FakeProvider implements AiModelProvider {
		private final String id;
		private final boolean configured;
		private final String answer;
		private final HttpStatus failure;
		int calls;
		String lastModel;

		private FakeProvider(String id, boolean configured, String answer, HttpStatus failure) {
			this.id = id;
			this.configured = configured;
			this.answer = answer;
			this.failure = failure;
		}

		static FakeProvider ok(String id, String answer) { return new FakeProvider(id, true, answer, null); }
		static FakeProvider failing(String id, HttpStatus status) { return new FakeProvider(id, true, null, status); }
		static FakeProvider unconfigured(String id) { return new FakeProvider(id, false, null, null); }

		@Override public String id() { return id; }
		@Override public String displayName() { return "Fake " + id; }
		@Override public boolean isConfigured() { return configured; }
		@Override public String defaultModel() { return id + "-default"; }
		@Override public List<String> suggestedModels() { return List.of(defaultModel()); }

		@Override
		public AiModelResult generate(AiTutorRequest request, String model) {
			calls++;
			lastModel = model;
			if (failure != null) {
				throw new ResponseStatusException(failure, "falha simulada");
			}
			return new AiModelResult(answer, model, 10, 20);
		}
	}
}
