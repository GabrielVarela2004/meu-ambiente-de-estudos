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

	@Test
	void parsesTheExecutionReportWithStructuredCompileErrors() {
		CodeExecutionResult result = runner.parseExecution("lixo\n" + DockerCodeRunner.MARKER + "\n"
				+ "{\"status\":\"COMPILATION_ERROR\",\"compileDiagnostics\":[{\"file\":\"loja/Pedido.java\",\"line\":3,"
				+ "\"column\":12,\"message\":\"incompatible types\"}],\"compileErrors\":[\"loja/Pedido.java:3 incompatible types\"]}");

		assertThat(result.status()).isEqualTo(CodeExecutionResult.Status.COMPILATION_ERROR);
		assertThat(result.compileDiagnostics()).singleElement().satisfies(d -> {
			assertThat(d.file()).isEqualTo("loja/Pedido.java");
			assertThat(d.line()).isEqualTo(3);
			assertThat(d.column()).isEqualTo(12);
		});

		CodeExecutionResult ok = runner.parseExecution(DockerCodeRunner.MARKER + "\n"
				+ "{\"status\":\"OK\",\"mainClass\":\"Main\",\"exitCode\":0,\"timeMillis\":42,\"stdout\":\"oi\\n\",\"stderr\":\"\"}");
		assertThat(ok.status()).isEqualTo(CodeExecutionResult.Status.OK);
		assertThat(ok.stdout()).isEqualTo("oi\n");
		assertThat(ok.timeMillis()).isEqualTo(42);
		assertThat(runner.parseExecution("Killed").status()).isEqualTo(CodeExecutionResult.Status.RUNTIME_ERROR);
	}

	@Test
	void writingTheProjectNeverLeavesTheWorkFolder(@org.junit.jupiter.api.io.TempDir java.nio.file.Path work) {
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> DockerCodeRunner.writeProject(work,
				List.of(new ExerciseFile("../fora.java", "class X {}"))))
				.isInstanceOf(br.com.estudos.estudos_backend.common.exception.InvalidInputException.class);
	}

	/** Projeto com classe, enum, interface e pacote, compilado junto e corrigido com testes (Docker real). */
	@Test
	@EnabledIf("runnerImageAvailable")
	void compilesMultiFileProjectWithPackagesAndRunsTests() {
		List<ExerciseFile> project = List.of(
				new ExerciseFile("loja/Forma.java", "package loja; public interface Forma { double area(); }"),
				new ExerciseFile("loja/Cor.java", "package loja; public enum Cor { AZUL, VERDE }"),
				new ExerciseFile("loja/Quadrado.java", """
						package loja;
						public class Quadrado implements Forma {
						  private final double lado; private final Cor cor;
						  public Quadrado(double lado, Cor cor) { this.lado = lado; this.cor = cor; }
						  @Override public double area() { return lado * lado; }
						  public Cor cor() { return cor; }
						}"""));
		String tests = """
				import org.junit.jupiter.api.*;
				import static org.junit.jupiter.api.Assertions.*;
				import loja.*;
				class QuadradoTest { @Test @Tag("obrigatorio") void area() { assertEquals(9.0, new Quadrado(3, Cor.AZUL).area()); } }
				""";

		CodeEvaluationReport report = runner.runProject(project, tests, List.of("IMPLEMENTS|Quadrado|Forma"));

		assertThat(report.status()).isEqualTo(RunStatus.OK);
		assertThat(report.tests()).singleElement().satisfies(t -> assertThat(t.passed()).isTrue());
		assertThat(report.checks()).allMatch(CodeEvaluationReport.CheckResult::passed);
	}

	@Test
	@EnabledIf("runnerImageAvailable")
	void runsMainWithStdinAndReportsCompileErrorsWithFileAndLine() {
		List<ExerciseFile> project = List.of(
				new ExerciseFile("Main.java", """
						import java.util.Scanner;
						public class Main {
						  public static void main(String[] args) {
						    String nome = new Scanner(System.in).nextLine();
						    System.out.println("Olá, " + nome + " - " + util.Saudacao.TEXTO);
						  }
						}"""),
				new ExerciseFile("util/Saudacao.java", "package util; public class Saudacao { public static final String TEXTO = \"bem-vindo\"; }"));

		CodeExecutionResult ok = runner.execute(project, "Ana\n");
		assertThat(ok.status()).isEqualTo(CodeExecutionResult.Status.OK);
		assertThat(ok.stdout()).contains("Olá, Ana - bem-vindo");
		assertThat(ok.mainClass()).isEqualTo("Main");

		CodeExecutionResult broken = runner.execute(List.of(project.get(0),
				new ExerciseFile("util/Saudacao.java", "package util;\npublic class Saudacao {\n  public static final int TEXTO = \"x\";\n}")), null);
		assertThat(broken.status()).isEqualTo(CodeExecutionResult.Status.COMPILATION_ERROR);
		assertThat(broken.compileDiagnostics()).anySatisfy(d -> {
			assertThat(d.file()).isEqualTo("util/Saudacao.java");
			assertThat(d.line()).isEqualTo(3);
		});
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
