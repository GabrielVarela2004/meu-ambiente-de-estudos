package br.com.estudos.estudos_backend.ai;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fila local de requisições por minuto para cada modelo do Gemini (nível gratuito tem limite por
 * modelo, ex.: 5 RPM no 3.8-flash e 15 no 3.5-flash-lite). Em vez de falhar quando o minuto enche,
 * a chamada espera uma vaga na janela de 60 s — respeitando {@code GEMINI_MAX_REQUESTS_PER_MINUTE}
 * como padrão para modelos sem limite conhecido.
 */
final class GeminiRateLimiter {

	/** Janela deslizante de um minuto dos limites do Gemini. */
	static final long WINDOW_MS = 60_000L;
	/** Tempo máximo esperando na fila antes de devolver erro claro ao usuário. */
	static final long MAX_WAIT_MS = 65_000L;
	/** Pausa entre verificações da fila (a espera total continua controlada por {@link #MAX_WAIT_MS}). */
	private static final long POLL_MS = 1_000L;

	/** Pausa injetável: em produção dorme de verdade; em teste avança o relógio falso. */
	interface Sleeper {
		void sleep(long millis) throws InterruptedException;
	}

	private final int defaultRpm;
	private final Map<String, Integer> rpmByModel;
	private final LongSupplier clock;
	private final Sleeper sleeper;
	private final Object lock = new Object();
	private final Map<String, ArrayDeque<Long>> timestampsByModel = new HashMap<>();

	GeminiRateLimiter(int defaultRpm, Map<String, Integer> rpmByModel, LongSupplier clock, Sleeper sleeper) {
		this.defaultRpm = Math.max(1, defaultRpm);
		this.rpmByModel = Map.copyOf(rpmByModel);
		this.clock = clock;
		this.sleeper = sleeper;
	}

	int limitFor(String model) {
		return rpmByModel.getOrDefault(model, defaultRpm);
	}

	/**
	 * Ocupa uma vaga na janela de um minuto do modelo, esperando na fila se necessário.
	 * Depois de {@link #MAX_WAIT_MS} esperando, devolve 429 com mensagem clara.
	 */
	void acquire(String model) {
		long deadline = clock.getAsLong() + MAX_WAIT_MS;
		for (;;) {
			long waitMs;
			synchronized (lock) {
				long now = clock.getAsLong();
				ArrayDeque<Long> queue = queue(model);
				while (!queue.isEmpty() && queue.peekFirst() < now - WINDOW_MS) {
					queue.pollFirst();
				}
				if (queue.size() < limitFor(model)) {
					queue.addLast(now);
					return;
				}
				// Vaga mais próxima de abrir: quando o mais antigo sair da janela.
				waitMs = Math.min(queue.peekFirst() + WINDOW_MS - now, POLL_MS);
			}
			if (clock.getAsLong() + Math.max(1, waitMs) > deadline) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
						"Limite de " + limitFor(model) + " requisições por minuto do modelo " + model
								+ " atingido. Aguarde cerca de um minuto e tente novamente.");
			}
			try {
				sleeper.sleep(Math.max(1, waitMs));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
						"A requisição ao Gemini foi interrompida.");
			}
		}
	}

	private ArrayDeque<Long> queue(String model) {
		return timestampsByModel.computeIfAbsent(model, key -> new ArrayDeque<>());
	}
}
