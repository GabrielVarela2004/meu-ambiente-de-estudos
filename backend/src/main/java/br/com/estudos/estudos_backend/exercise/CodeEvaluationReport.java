package br.com.estudos.estudos_backend.exercise;

import java.util.List;

/** Relatório devolvido pelo avaliador: testes, checagens de conceito e problemas de boas práticas. */
public record CodeEvaluationReport(
		RunStatus status,
		String message,
		List<String> compileErrors,
		List<TestResult> tests,
		List<CheckResult> checks,
		List<QualityIssue> quality,
		String rawJson) {

	public CodeEvaluationReport {
		compileErrors = compileErrors == null ? List.of() : List.copyOf(compileErrors);
		tests = tests == null ? List.of() : List.copyOf(tests);
		checks = checks == null ? List.of() : List.copyOf(checks);
		quality = quality == null ? List.of() : List.copyOf(quality);
	}

	public static CodeEvaluationReport failure(RunStatus status, String message) {
		return new CodeEvaluationReport(status, message, List.of(), List.of(), List.of(), List.of(), null);
	}

	public enum RunStatus {
		OK,
		COMPILATION_ERROR,
		RUNTIME_ERROR,
		TIMEOUT,
		/** Os testes gerados pela IA não compilam ou não são válidos. */
		TESTS_INVALID,
		UNAVAILABLE
	}

	public record TestResult(String name, boolean passed, boolean mandatory, String message) {}

	public record CheckResult(String type, String description, boolean passed, String detail) {}

	public record QualityIssue(String rule, String severity, String message, int line) {
		public boolean major() { return "MAJOR".equalsIgnoreCase(severity); }
	}
}
