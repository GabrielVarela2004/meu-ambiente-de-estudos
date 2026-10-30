package br.com.estudos.estudos_backend.ai;

import java.util.function.Supplier;

/**
 * Informa ao roteador de IA qual operação está em andamento na thread atual.
 * Os serviços envolvem a chamada ao provedor em {@link #call}; sem contexto, vale {@link AiOperation#CHAT}.
 * Assim os serviços continuam dependendo apenas de {@code AiTutorProvider}.
 */
public final class AiOperationContext {

	private static final ThreadLocal<AiOperation> CURRENT = new ThreadLocal<>();

	private AiOperationContext() {
	}

	public static <T> T call(AiOperation operation, Supplier<T> action) {
		AiOperation previous = CURRENT.get();
		CURRENT.set(operation);
		try {
			return action.get();
		} finally {
			if (previous == null) {
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		}
	}

	public static AiOperation current() {
		AiOperation operation = CURRENT.get();
		return operation == null ? AiOperation.CHAT : operation;
	}
}
