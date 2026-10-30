package br.com.estudos.estudos_backend.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.RunStatus;
import br.com.estudos.estudos_backend.learning.LearningCriteriaProperties;

class DockerCodeRunnerTest {

	private static final String IMAGE = "estudos-java-runner:1";

	private final DockerCodeRunner runner = new DockerCodeRunner(true, "docker", IMAGE, LearningCriteriaProperties.defaults());

	@Test
	void parsesTheReportAfterTheMarker() {
		CodeEvaluationReport report = runner.parse("saída do aluno\n" + DockerCodeRunner.MARKER + "\n"
				+ "{\"status\":\"OK\",\"tests\":[{\"name\":\"a()\",\"passed\":false,\"mandatory\":true,\"message\":\"esperado 1\"}],"
				+ "\"checks\":[{\"type\":\"NO_INSTANCEOF\",\"description\":\"d\",\"passed\":true}],"
				+ "\"quality\":[{\"rule\":\"EMPTY_CATCH\",\"severity\":\"MAJOR\",\"message\":\"m\",\"line\":3}]}");

		assertThat(report.status()).isEqualTo(RunStatus.OK);
		assertThat(report.tests()).singleElement().satisfies(t -> {
			assertThat(t.mandatory()).isTrue();
			assertThat(t.message()).isEqualTo("esperado 1");
		});
		assertThat(report.checks()).singleElement().satisfies(c -> assertThat(c.passed()).isTrue());
		assertThat(report.quality()).singleElement().satisfies(q -> assertThat(q.major()).isTrue());
	}

	@Test
	void outputWithoutReportIsARuntimeError() {
		assertThat(runner.parse("Killed").status()).isEqualTo(RunStatus.RUNTIME_ERROR);
	}

	/** Execução real: só roda onde o Docker está ativo e a imagem de runner/java foi construída. */
	@Test
	@EnabledIf("runnerImageAvailable")
	void evaluatesOverridingExerciseInContainer() {
		String tests = """
				import org.junit.jupiter.api.*;
				import static org.junit.jupiter.api.Assertions.*;
				class AnimalTest {
				  @Test @Tag("obrigatorio") void cachorroLate() { Animal a = new Cachorro(); assertEquals("Au au", a.emitirSom()); }
				}
				""";
		String good = """
				public class Animal { public String emitirSom() { return "..."; } }
				class Cachorro extends Animal { @Override public String emitirSom() { return "Au au"; } }
				""";
		String bad = """
				public class Animal { public String emitirSom() { return "..."; } }
				class Cachorro extends Animal { public String emitirSom() { return "Au"; } }
				""";

		CodeEvaluationReport passing = runner.run(good, tests, List.of("OVERRIDE_ANNOTATION"));
		CodeEvaluationReport failing = runner.run(bad, tests, List.of("OVERRIDE_ANNOTATION"));

		assertThat(passing.status()).isEqualTo(RunStatus.OK);
		assertThat(passing.tests()).allMatch(CodeEvaluationReport.TestResult::passed);
		assertThat(passing.checks()).allMatch(CodeEvaluationReport.CheckResult::passed);
		assertThat(failing.tests()).noneMatch(CodeEvaluationReport.TestResult::passed);
		assertThat(failing.checks()).noneMatch(CodeEvaluationReport.CheckResult::passed);
	}

	static boolean runnerImageAvailable() {
		try {
			Process process = new ProcessBuilder("docker", "image", "inspect", IMAGE)
					.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}
}
