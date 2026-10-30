package br.com.estudos.estudos_backend.exercise;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.CheckResult;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.QualityIssue;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.RunStatus;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.TestResult;
import br.com.estudos.estudos_backend.learning.LearningCriteriaProperties;

/**
 * Executa o avaliador (imagem construída a partir de runner/java) num contêiner descartável:
 * sem rede, com limite de memória, CPU e processos, sistema de arquivos só de leitura e tempo limite.
 */
@Component
public class DockerCodeRunner implements CodeRunner {

	private static final Logger log = LoggerFactory.getLogger(DockerCodeRunner.class);
	static final String MARKER = "@@EVALUATION_REPORT@@";
	private static final long AVAILABILITY_CACHE_MILLIS = 60_000;

	private final boolean enabled;
	private final String dockerCommand;
	private final String image;
	private final long timeoutMillis;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private volatile Boolean availableCache;
	private volatile long availableCheckedAt;

	public DockerCodeRunner(
			@Value("${estudos.runner.enabled:true}") boolean enabled,
			@Value("${estudos.runner.docker-command:docker}") String dockerCommand,
			@Value("${estudos.runner.image:estudos-java-runner:1}") String image,
			LearningCriteriaProperties criteria) {
		this.enabled = enabled;
		this.dockerCommand = dockerCommand;
		this.image = image;
		this.timeoutMillis = criteria.getExecutionTimeoutMillis();
	}

	@Override
	public boolean isAvailable() {
		if (!enabled) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (availableCache == null || now - availableCheckedAt > AVAILABILITY_CACHE_MILLIS) {
			availableCache = imageExists();
			availableCheckedAt = now;
			if (!availableCache) {
				log.info("Avaliador de código indisponível: Docker parado ou imagem {} não construída.", image);
			}
		}
		return availableCache;
	}

	private boolean imageExists() {
		try {
			Process process = new ProcessBuilder(dockerCommand, "image", "inspect", image)
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
			return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	@Override
	public CodeEvaluationReport run(String submission, String testCode, List<String> conceptChecks) {
		if (!isAvailable()) {
			return CodeEvaluationReport.failure(RunStatus.UNAVAILABLE, "Ambiente de execução indisponível.");
		}
		Path work = null;
		String containerName = "estudos-eval-" + UUID.randomUUID();
		try {
			work = Files.createTempDirectory("estudos-eval");
			Files.writeString(work.resolve("submission.java"), submission == null ? "" : submission);
			Files.writeString(work.resolve("tests.java"), testCode == null ? "" : testCode);
			Files.writeString(work.resolve("checks.txt"), String.join("\n", conceptChecks == null ? List.of() : conceptChecks));
			makeReadableByContainer(work);

			List<String> command = new ArrayList<>(List.of(dockerCommand, "run", "--rm", "--name", containerName,
					"--network", "none", "--memory", "256m", "--memory-swap", "256m", "--cpus", "1",
					"--pids-limit", "128", "--read-only", "--tmpfs", "/tmp:rw,exec,size=64m",
					"--security-opt", "no-new-privileges", "--cap-drop", "ALL",
					"-v", work.toAbsolutePath() + ":/work:ro", image));
			Path output = work.resolve("output.txt");
			Process process = new ProcessBuilder(command)
					.redirectErrorStream(true)
					.redirectOutput(output.toFile())
					.start();
			if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				kill(containerName);
				return CodeEvaluationReport.failure(RunStatus.TIMEOUT,
						"O código excedeu o tempo limite de " + timeoutMillis / 1000 + " s.");
			}
			return parse(Files.readString(output, StandardCharsets.UTF_8));
		} catch (IOException e) {
			log.warn("Falha ao executar o avaliador de código: {}", e.getMessage());
			return CodeEvaluationReport.failure(RunStatus.UNAVAILABLE, "Falha ao iniciar o ambiente de execução.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			kill(containerName);
			return CodeEvaluationReport.failure(RunStatus.TIMEOUT, "Execução interrompida.");
		} finally {
			deleteQuietly(work);
		}
	}

	CodeEvaluationReport parse(String output) {
		int index = output.lastIndexOf(MARKER);
		if (index < 0) {
			// Sem relatório: o processo morreu (falta de memória, System.exit, etc.).
			String tail = output.length() > 500 ? output.substring(output.length() - 500) : output;
			return CodeEvaluationReport.failure(RunStatus.RUNTIME_ERROR, "A execução terminou sem relatório. " + tail.strip());
		}
		String json = output.substring(index + MARKER.length()).strip();
		try {
			JsonNode node = objectMapper.readTree(json);
			RunStatus status;
			try {
				status = RunStatus.valueOf(node.path("status").asText("RUNTIME_ERROR"));
			} catch (IllegalArgumentException e) {
				status = RunStatus.RUNTIME_ERROR;
			}
			List<String> compileErrors = new ArrayList<>();
			node.path("compileErrors").forEach(e -> compileErrors.add(e.asText()));
			List<TestResult> tests = new ArrayList<>();
			node.path("tests").forEach(t -> tests.add(new TestResult(t.path("name").asText(), t.path("passed").asBoolean(),
					t.path("mandatory").asBoolean(), t.hasNonNull("message") ? t.path("message").asText() : null)));
			List<CheckResult> checks = new ArrayList<>();
			node.path("checks").forEach(c -> checks.add(new CheckResult(c.path("type").asText(), c.path("description").asText(),
					c.path("passed").asBoolean(), c.hasNonNull("detail") ? c.path("detail").asText() : null)));
			List<QualityIssue> quality = new ArrayList<>();
			node.path("quality").forEach(q -> quality.add(new QualityIssue(q.path("rule").asText(), q.path("severity").asText(),
					q.path("message").asText(), q.path("line").asInt())));
			return new CodeEvaluationReport(status, node.hasNonNull("message") ? node.path("message").asText() : null,
					compileErrors, tests, checks, quality, json);
		} catch (IOException e) {
			return CodeEvaluationReport.failure(RunStatus.RUNTIME_ERROR, "Relatório do avaliador inválido.");
		}
	}

	private void kill(String containerName) {
		try {
			new ProcessBuilder(dockerCommand, "rm", "-f", containerName)
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start()
					.waitFor(10, TimeUnit.SECONDS);
		} catch (IOException e) {
			log.warn("Não foi possível remover o contêiner {}: {}", containerName, e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** O contêiner roda como usuário comum; os arquivos precisam ser legíveis por ele. */
	private static void makeReadableByContainer(Path work) throws IOException {
		try {
			Files.setPosixFilePermissions(work, PosixFilePermissions.fromString("rwxr-xr-x"));
			try (Stream<Path> files = Files.list(work)) {
				for (Path file : files.toList()) {
					Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
				}
			}
		} catch (UnsupportedOperationException e) {
			// Windows: as permissões POSIX não se aplicam; o Docker Desktop monta a pasta legível.
		}
	}

	private static void deleteQuietly(Path dir) {
		if (dir == null) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		} catch (IOException ignored) {
			// Pasta temporária: o sistema limpa depois.
		}
	}
}
