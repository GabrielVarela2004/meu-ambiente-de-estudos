package br.com.estudos.estudos_backend.exercise;

import java.util.List;

/** Resultado do "Executar" (execução livre do main): não é tentativa e não usa IA. */
public record CodeExecutionResult(
		Status status,
		String message,
		String mainClass,
		Integer exitCode,
		long timeMillis,
		String stdout,
		String stderr,
		List<CompileDiagnostic> compileDiagnostics) {

	public CodeExecutionResult {
		compileDiagnostics = compileDiagnostics == null ? List.of() : List.copyOf(compileDiagnostics);
		stdout = stdout == null ? "" : stdout;
		stderr = stderr == null ? "" : stderr;
	}

	public static CodeExecutionResult failure(Status status, String message) {
		return new CodeExecutionResult(status, message, null, null, 0, "", "", List.of());
	}

	public enum Status {
		OK,
		COMPILATION_ERROR,
		RUNTIME_ERROR,
		TIMEOUT,
		/** Nenhuma classe com public static void main(String[]). */
		NO_MAIN,
		UNAVAILABLE
	}

	/** Erro de compilação com posição: o arquivo é o caminho no projeto (relativo a src). */
	public record CompileDiagnostic(String file, int line, int column, String message) {}
}
