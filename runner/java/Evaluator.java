import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import com.github.javaparser.ParseResult;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

/**
 * Avaliador executado dentro do contêiner isolado (sem rede, com limites de CPU e memória).
 *
 * Entrada em /work: o projeto do estudante em src/ (vários .java, com pacotes) ou, no formato antigo, um único
 * submission.java; tests.java (JUnit 5, opcional); checks.txt (uma checagem de conceito por linha:
 * TIPO|arg1|arg2, opcional); stdin.txt (entrada do modo run, opcional).
 * Modos: sem segundo argumento corrige com os testes; com "run" só compila e executa o main.
 * Saída: um único JSON na saída padrão, depois da linha {@value #MARKER}.
 */
public class Evaluator {

	static final String MARKER = "@@EVALUATION_REPORT@@";
	static final String MANDATORY_TAG = "obrigatorio";
	static final int MAX_METHOD_LINES = 40;
	static final int MAX_NESTING = 3;
	static final int MAX_OUTPUT_CHARS = 4000;
	/** Saída do console do modo run (stdout e stderr, cada um). */
	static final int MAX_RUN_OUTPUT_BYTES = 64 * 1024;
	static final long RUN_TIMEOUT_MILLIS = 10_000;

	public static void main(String[] args) throws Exception {
		Path work = Path.of(args.length > 0 ? args[0] : "/work");
		boolean runMode = args.length > 1 && args[1].equals("run");
		Path out = Files.createTempDirectory("eval");
		PrintStream realOut = System.out;
		Map<String, Object> report = new LinkedHashMap<>();
		try {
			report = runMode ? runProject(work, out) : evaluate(work, out);
		} catch (Throwable t) {
			report.put("status", "RUNTIME_ERROR");
			report.put("message", String.valueOf(t));
		} finally {
			System.setOut(realOut);
		}
		realOut.println(MARKER);
		realOut.println(Json.write(report));
		realOut.flush();
		// Threads deixadas pelo código do estudante não podem segurar o contêiner.
		System.exit(0);
	}

	static JavaParser parser() {
		return new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
	}

	/** Arquivo do projeto: caminho relativo a src/ (com "/") e conteúdo. */
	record SourceFile(String path, String content) {}

	/** Lê os .java de src/ (só arquivos comuns dentro da pasta; nada de links para fora dela). */
	static List<SourceFile> readProject(Path srcDir) throws IOException {
		Path root = srcDir.toRealPath();
		List<SourceFile> files = new ArrayList<>();
		try (var paths = Files.walk(root)) {
			for (Path p : paths.sorted().toList()) {
				if (!Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS) || !p.toString().endsWith(".java")) {
					continue;
				}
				if (!p.toRealPath().startsWith(root)) {
					continue;
				}
				files.add(new SourceFile(root.relativize(p).toString().replace('\\', '/'), Files.readString(p)));
			}
		}
		return files;
	}

	static Map<String, Object> evaluate(Path work, Path out) throws IOException {
		Map<String, Object> report = new LinkedHashMap<>();
		Path srcDir = work.resolve("src");
		List<SourceFile> project = Files.isDirectory(srcDir)
				? readProject(srcDir)
				: List.of(new SourceFile("submission.java", Files.readString(work.resolve("submission.java"))));
		boolean legacy = !Files.isDirectory(srcDir);
		Path testsFile = work.resolve("tests.java");
		String tests = Files.exists(testsFile) ? Files.readString(testsFile) : "";
		Path checksFile = work.resolve("checks.txt");
		List<String> checks = Files.exists(checksFile) ? Files.readAllLines(checksFile) : List.of();

		JavaParser parser = parser();
		Map<String, CompilationUnit> units = parseAll(parser, project, report, legacy ? null : out);
		if (units == null) {
			return report;
		}

		List<Map<String, Object>> checkResults = Concepts.run(List.copyOf(units.values()), checks);
		boolean overrideChecked = checks.stream().anyMatch(c -> c.trim().startsWith("OVERRIDE_ANNOTATION"));
		report.put("checks", checkResults);
		List<Map<String, Object>> quality = new ArrayList<>();
		units.forEach((path, unit) -> quality.addAll(Quality.run(unit, !overrideChecked, legacy ? null : path)));
		quality.addAll(Quality.missingOverrides(List.copyOf(units.values()), !overrideChecked));
		report.put("quality", quality);

		Path src = Files.createDirectories(out.resolve("src"));
		Path classes = Files.createDirectories(out.resolve("classes"));
		List<Path> sources = new ArrayList<>();
		if (legacy) {
			// Formato antigo: cada tipo de topo vira o próprio arquivo, no pacote padrão, como os testes esperam.
			sources.addAll(split(units.values().iterator().next(), src));
		} else {
			sources.addAll(copyProject(project, src));
		}
		List<String> testClassNames = new ArrayList<>();
		if (!tests.isBlank()) {
			ParseResult<CompilationUnit> parsedTests = parser.parse(tests);
			if (!parsedTests.isSuccessful() || parsedTests.getResult().isEmpty()) {
				report.put("status", "TESTS_INVALID");
				return report;
			}
			for (TypeDeclaration<?> type : parsedTests.getResult().get().getTypes()) {
				testClassNames.add(type.getNameAsString());
			}
			sources.addAll(split(parsedTests.getResult().get(), src));
		}

		List<Map<String, Object>> diagnostics = compile(sources, src, classes);
		if (!diagnostics.isEmpty()) {
			putCompileErrors(report, diagnostics);
			return report;
		}

		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
		List<Map<String, Object>> testResults = testClassNames.isEmpty()
				? List.of()
				: runTests(classes, testClassNames);
		String stdout = captured.toString(StandardCharsets.UTF_8);
		report.put("status", "OK");
		report.put("tests", testResults);
		report.put("stdout", stdout.length() > MAX_OUTPUT_CHARS ? stdout.substring(0, MAX_OUTPUT_CHARS) : stdout);
		return report;
	}

	static List<Path> split(CompilationUnit unit, Path dir) throws IOException {
		List<Path> files = new ArrayList<>();
		for (TypeDeclaration<?> type : unit.getTypes()) {
			CompilationUnit single = new CompilationUnit();
			unit.getImports().forEach(single::addImport);
			single.addType(type.clone());
			Path file = dir.resolve(type.getNameAsString() + ".java");
			Files.writeString(file, single.toString());
			files.add(file);
		}
		return files;
	}

	/**
	 * Analisa todos os arquivos. Com erro de sintaxe, preenche o relatório (COMPILATION_ERROR com arquivo e linha)
	 * e devolve null. Com {@code out}, usa as mensagens do javac, mais claras que as do analisador.
	 */
	static Map<String, CompilationUnit> parseAll(JavaParser parser, List<SourceFile> project, Map<String, Object> report,
			Path out) throws IOException {
		Map<String, CompilationUnit> units = new LinkedHashMap<>();
		List<Map<String, Object>> diagnostics = new ArrayList<>();
		for (SourceFile file : project) {
			ParseResult<CompilationUnit> parsed = parser.parse(file.content());
			if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
				parsed.getProblems().forEach(p -> diagnostics.add(entry("file", file.path(),
						"line", p.getLocation().flatMap(l -> l.getBegin().getRange()).map(r -> r.begin.line).orElse(0),
						"column", p.getLocation().flatMap(l -> l.getBegin().getRange()).map(r -> r.begin.column).orElse(0),
						"message", p.getMessage())));
			} else {
				units.put(file.path(), parsed.getResult().get());
			}
		}
		if (!diagnostics.isEmpty()) {
			List<Map<String, Object>> javac = List.of();
			if (out != null) {
				Path src = Files.createDirectories(out.resolve("syntax-src"));
				javac = compile(copyProject(project, src), src, Files.createDirectories(out.resolve("syntax-classes")));
			}
			putCompileErrors(report, javac.isEmpty() ? diagnostics : javac);
			return null;
		}
		return units;
	}

	/** Erros estruturados (arquivo, linha, coluna, mensagem) e, para telas antigas, o texto "arquivo:linha mensagem". */
	static void putCompileErrors(Map<String, Object> report, List<Map<String, Object>> diagnostics) {
		report.put("status", "COMPILATION_ERROR");
		report.put("compileDiagnostics", diagnostics);
		List<String> errors = new ArrayList<>();
		diagnostics.forEach(d -> errors.add(d.get("file") + ":" + d.get("line") + " " + d.get("message")));
		report.put("compileErrors", errors);
	}

	/** Copia o projeto para a pasta de compilação mantendo as pastas dos pacotes. */
	static List<Path> copyProject(List<SourceFile> project, Path src) throws IOException {
		List<Path> files = new ArrayList<>();
		for (SourceFile file : project) {
			Path target = src.resolve(file.path()).normalize();
			if (!target.startsWith(src)) {
				throw new IOException("Caminho fora do projeto: " + file.path());
			}
			Files.createDirectories(target.getParent());
			Files.writeString(target, file.content());
			files.add(target);
		}
		return files;
	}

	static List<Map<String, Object>> compile(List<Path> sources, Path src, Path classes) throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.forLanguageTag("pt-BR"), StandardCharsets.UTF_8)) {
			List<String> options = List.of("-d", classes.toString(), "-cp", System.getProperty("java.class.path"),
					"-encoding", "UTF-8", "-proc:none");
			compiler.getTask(null, files, diagnostics, options, null,
					files.getJavaFileObjectsFromPaths(sources)).call();
		}
		List<Map<String, Object>> errors = new ArrayList<>();
		for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
			if (d.getKind() == Diagnostic.Kind.ERROR) {
				String file = d.getSource() == null ? "" : relative(src, Path.of(d.getSource().toUri()));
				errors.add(entry("file", file, "line", (int) Math.max(0, d.getLineNumber()),
						"column", (int) Math.max(0, d.getColumnNumber()), "message", d.getMessage(Locale.forLanguageTag("pt-BR"))));
			}
		}
		return errors;
	}

	static String relative(Path src, Path file) {
		Path abs = file.toAbsolutePath().normalize();
		Path root = src.toAbsolutePath().normalize();
		return (abs.startsWith(root) ? root.relativize(abs) : abs.getFileName()).toString().replace('\\', '/');
	}

	// ------------------------------------------------------------------ execução livre (Run)

	/** Compila o projeto e executa o main num processo filho, com stdin opcional e tempo limite. */
	static Map<String, Object> runProject(Path work, Path out) throws IOException, InterruptedException {
		Map<String, Object> report = new LinkedHashMap<>();
		List<SourceFile> project = readProject(work.resolve("src"));
		Map<String, CompilationUnit> units = parseAll(parser(), project, report, out);
		if (units == null) {
			return report;
		}
		Optional<String> mainClass = findMainClass(units);
		if (mainClass.isEmpty()) {
			report.put("status", "NO_MAIN");
			report.put("message", "Nenhuma classe com public static void main(String[] args) foi encontrada.");
			return report;
		}
		Path src = Files.createDirectories(out.resolve("src"));
		Path classes = Files.createDirectories(out.resolve("classes"));
		List<Map<String, Object>> diagnostics = compile(copyProject(project, src), src, classes);
		if (!diagnostics.isEmpty()) {
			putCompileErrors(report, diagnostics);
			return report;
		}

		Path stdout = out.resolve("stdout.txt");
		Path stderr = out.resolve("stderr.txt");
		Path stdin = work.resolve("stdin.txt");
		String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		ProcessBuilder builder = new ProcessBuilder(javaBin, "-Xmx128m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1",
				"-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", classes.toString(), mainClass.get())
				.redirectOutput(stdout.toFile())
				.redirectError(stderr.toFile());
		boolean hasStdin = Files.exists(stdin);
		builder.redirectInput(hasStdin ? ProcessBuilder.Redirect.from(stdin.toFile()) : ProcessBuilder.Redirect.PIPE);
		long start = System.nanoTime();
		Process process = builder.start();
		if (!hasStdin) {
			// Sem entrada: um Scanner recebe fim de arquivo em vez de esperar até o tempo limite.
			process.getOutputStream().close();
		}
		boolean finished = process.waitFor(RUN_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
		if (!finished) {
			process.destroyForcibly().waitFor();
		}
		long millis = (System.nanoTime() - start) / 1_000_000;

		report.put("status", !finished ? "TIMEOUT" : process.exitValue() == 0 ? "OK" : "RUNTIME_ERROR");
		report.put("mainClass", mainClass.get());
		report.put("exitCode", finished ? process.exitValue() : null);
		report.put("timeMillis", millis);
		report.put("stdout", readTruncated(stdout));
		report.put("stderr", readTruncated(stderr));
		if (!finished) {
			report.put("message", "O programa passou do tempo limite de " + RUN_TIMEOUT_MILLIS / 1000 + " s e foi encerrado.");
		}
		return report;
	}

	/** Classe com public static void main(String[]); a chamada Main tem preferência. Nome completo, com pacote. */
	static Optional<String> findMainClass(Map<String, CompilationUnit> units) {
		List<String> candidates = new ArrayList<>();
		for (CompilationUnit unit : units.values()) {
			String pkg = unit.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("");
			for (TypeDeclaration<?> type : unit.getTypes()) {
				boolean hasMain = type.getMethodsByName("main").stream().anyMatch(m -> m.isStatic() && m.isPublic()
						&& m.getParameters().size() == 1 && m.getParameter(0).getType().asString().replace(" ", "").matches("String(\\[\\]|\\.\\.\\.)"));
				if (hasMain) {
					candidates.add(pkg + type.getNameAsString());
				}
			}
		}
		return candidates.stream().filter(c -> c.equals("Main") || c.endsWith(".Main")).findFirst()
				.or(() -> candidates.stream().findFirst());
	}

	static String readTruncated(Path file) throws IOException {
		if (!Files.exists(file)) {
			return "";
		}
		long size = Files.size(file);
		byte[] bytes;
		try (var in = Files.newInputStream(file)) {
			bytes = in.readNBytes((int) Math.min(size, MAX_RUN_OUTPUT_BYTES));
		}
		String text = new String(bytes, StandardCharsets.UTF_8);
		return size > MAX_RUN_OUTPUT_BYTES ? text + "\n[saída truncada em 64 KB]" : text;
	}

	static List<Map<String, Object>> runTests(Path classes, List<String> testClassNames) throws IOException {
		URLClassLoader loader = new URLClassLoader(new URL[] { classes.toUri().toURL() }, Evaluator.class.getClassLoader());
		Thread.currentThread().setContextClassLoader(loader);
		LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request()
				.configurationParameter("junit.jupiter.execution.timeout.default", "3 s")
				.configurationParameter("junit.jupiter.execution.timeout.thread.mode.default", "SEPARATE_THREAD");
		for (String name : testClassNames) {
			try {
				builder.selectors(DiscoverySelectors.selectClass(loader.loadClass(name)));
			} catch (ClassNotFoundException e) {
				// Tipo auxiliar sem testes.
			}
		}
		LauncherDiscoveryRequest request = builder.build();
		List<Map<String, Object>> results = new ArrayList<>();
		Launcher launcher = LauncherFactory.create();
		launcher.execute(request, new TestExecutionListener() {
			@Override
			public void executionFinished(TestIdentifier id, TestExecutionResult result) {
				if (!id.isTest()) {
					return;
				}
				Map<String, Object> test = new LinkedHashMap<>();
				test.put("name", id.getDisplayName());
				test.put("passed", result.getStatus() == TestExecutionResult.Status.SUCCESSFUL);
				test.put("mandatory", id.getTags().stream().anyMatch(t -> t.getName().equalsIgnoreCase(MANDATORY_TAG)));
				result.getThrowable().ifPresent(t -> test.put("message", truncate(String.valueOf(t.getMessage() == null ? t : t.getMessage()), 300)));
				results.add(test);
			}

			@Override
			public void executionSkipped(TestIdentifier id, String reason) {
				if (id.isTest()) {
					Map<String, Object> test = new LinkedHashMap<>();
					test.put("name", id.getDisplayName());
					test.put("passed", false);
					test.put("mandatory", id.getTags().stream().anyMatch(t -> t.getName().equalsIgnoreCase(MANDATORY_TAG)));
					test.put("message", reason);
					results.add(test);
				}
			}
		});
		return results;
	}

	static String truncate(String text, int max) {
		return text.length() > max ? text.substring(0, max) + "..." : text;
	}

	static int line(Node node) {
		return node.getBegin().map(p -> p.line).orElse(0);
	}

	static Map<String, Object> entry(Object... keyValues) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < keyValues.length; i += 2) {
			map.put((String) keyValues[i], keyValues[i + 1]);
		}
		return map;
	}

	// ------------------------------------------------------------------ conceito

	/** Checagens estruturais pedidas pelo exercício, feitas sobre o código-fonte (não dependem de execução). */
	static final class Concepts {

		static Map<String, ClassOrInterfaceDeclaration> typesOf(List<CompilationUnit> units) {
			Map<String, ClassOrInterfaceDeclaration> types = new HashMap<>();
			units.forEach(u -> u.findAll(ClassOrInterfaceDeclaration.class).forEach(t -> types.put(t.getNameAsString(), t)));
			return types;
		}

		static List<Map<String, Object>> run(List<CompilationUnit> units, List<String> lines) {
			Map<String, ClassOrInterfaceDeclaration> types = typesOf(units);
			List<Map<String, Object>> results = new ArrayList<>();
			for (String raw : lines) {
				if (raw.isBlank()) {
					continue;
				}
				String[] p = raw.trim().split("\\|");
				String type = p[0].trim().toUpperCase(Locale.ROOT);
				String a = p.length > 1 ? p[1].trim() : "";
				String b = p.length > 2 ? p[2].trim() : "";
				Optional<ClassOrInterfaceDeclaration> cls = Optional.ofNullable(types.get(a));
				switch (type) {
					case "CLASS_EXTENDS" -> results.add(result(type, a + " estende " + b,
							cls.map(c -> c.getExtendedTypes().stream().anyMatch(t -> t.getNameAsString().equals(b))).orElse(false)));
					case "IMPLEMENTS" -> results.add(result(type, a + " implementa " + b,
							cls.map(c -> c.getImplementedTypes().stream().anyMatch(t -> t.getNameAsString().equals(b))).orElse(false)));
					case "INTERFACE" -> results.add(result(type, a + " é uma interface",
							cls.map(ClassOrInterfaceDeclaration::isInterface).orElse(false)));
					case "ABSTRACT_CLASS" -> results.add(result(type, a + " é uma classe abstrata",
							cls.map(ClassOrInterfaceDeclaration::isAbstract).orElse(false)));
					case "METHOD_EXISTS" -> results.add(result(type, a + " declara o método " + b + "()",
							cls.map(c -> !c.getMethodsByName(b).isEmpty()).orElse(false)));
					case "METHOD_OVERRIDDEN" -> results.add(result(type, a + " sobrescreve " + b + "()",
							cls.map(c -> !c.getMethodsByName(b).isEmpty()
									&& (!c.getExtendedTypes().isEmpty() || !c.getImplementedTypes().isEmpty())).orElse(false)));
					case "CALLS_SUPER" -> results.add(result(type, a + "." + b + "() chama super." + b + "()",
							cls.map(c -> c.getMethodsByName(b).stream().anyMatch(m -> m.findAll(MethodCallExpr.class).stream()
									.anyMatch(call -> call.getNameAsString().equals(b)
											&& call.getScope().map(s -> s.isSuperExpr()).orElse(false)))).orElse(false)));
					case "OVERRIDE_ANNOTATION" -> {
						List<String> missing = missingOverrides(types);
						results.add(result(type, "Métodos sobrescritos usam @Override", missing.isEmpty(),
								missing.isEmpty() ? null : "Sem @Override: " + String.join(", ", missing)));
					}
					case "EQUALS_WITH_HASHCODE" -> {
						List<String> broken = new ArrayList<>();
						types.values().forEach(c -> {
							boolean eq = c.getMethods().stream().anyMatch(m -> m.getNameAsString().equals("equals") && m.getParameters().size() == 1);
							boolean hash = c.getMethods().stream().anyMatch(m -> m.getNameAsString().equals("hashCode") && m.getParameters().isEmpty());
							if (eq != hash) broken.add(c.getNameAsString());
						});
						results.add(result(type, "equals e hashCode sobrescritos juntos", broken.isEmpty(),
								broken.isEmpty() ? null : "Incompleto em: " + String.join(", ", broken)));
					}
					case "NO_INSTANCEOF" -> {
						long count = units.stream().mapToLong(u -> u.findAll(InstanceOfExpr.class).stream()
								.filter(i -> i.findAncestor(MethodDeclaration.class).map(m -> !m.getNameAsString().equals("equals")).orElse(true))
								.count()).sum();
						results.add(result(type, "Usa polimorfismo em vez de instanceof", count == 0,
								count == 0 ? null : count + " uso(s) de instanceof fora de equals"));
					}
					default -> results.add(result(type, "Checagem desconhecida: " + raw.trim(), true));
				}
			}
			return results;
		}

		/** Métodos com mesmo nome e número de parâmetros que um método do supertipo (declarado no código ou de Object). */
		static List<String> missingOverrides(Map<String, ClassOrInterfaceDeclaration> types) {
			List<String> missing = new ArrayList<>();
			for (ClassOrInterfaceDeclaration c : types.values()) {
				List<ClassOrInterfaceDeclaration> supers = new ArrayList<>();
				c.getExtendedTypes().forEach(t -> Optional.ofNullable(types.get(t.getNameAsString())).ifPresent(supers::add));
				c.getImplementedTypes().forEach(t -> Optional.ofNullable(types.get(t.getNameAsString())).ifPresent(supers::add));
				for (MethodDeclaration m : c.getMethods()) {
					if (m.isStatic() || m.isAnnotationPresent("Override")) {
						continue;
					}
					boolean objectMethod = (m.getNameAsString().equals("equals") && m.getParameters().size() == 1)
							|| (m.getNameAsString().matches("hashCode|toString") && m.getParameters().isEmpty());
					boolean overridesDeclared = supers.stream().anyMatch(s -> s.getMethodsByName(m.getNameAsString()).stream()
							.anyMatch(sm -> sm.getParameters().size() == m.getParameters().size()));
					if (objectMethod || overridesDeclared) {
						missing.add(c.getNameAsString() + "." + m.getNameAsString() + "()");
					}
				}
			}
			return missing;
		}

		static Map<String, Object> result(String type, String description, boolean passed) {
			return result(type, description, passed, null);
		}

		static Map<String, Object> result(String type, String description, boolean passed, String detail) {
			Map<String, Object> map = entry("type", type, "description", description, "passed", passed);
			if (detail != null) map.put("detail", detail);
			return map;
		}
	}

	// ------------------------------------------------------------------ boas práticas

	/** Regras de boas práticas verificadas no código-fonte. MAJOR pesa mais que MINOR na nota. */
	static final class Quality {

		/** Problemas de um arquivo; file é o caminho no projeto (null no formato antigo, de arquivo único). */
		static List<Map<String, Object>> run(CompilationUnit unit, boolean checkOverride, String file) {
			List<Map<String, Object>> issues = new ArrayList<>();
			for (ClassOrInterfaceDeclaration c : unit.findAll(ClassOrInterfaceDeclaration.class)) {
				if (!c.getNameAsString().matches("[A-Z][A-Za-z0-9]*")) {
					issues.add(issue("NAMING", "MINOR", "Classe \"" + c.getNameAsString() + "\" deveria usar PascalCase.", line(c)));
				}
			}
			for (MethodDeclaration m : unit.findAll(MethodDeclaration.class)) {
				if (!m.getNameAsString().matches("[a-z][A-Za-z0-9]*")) {
					issues.add(issue("NAMING", "MINOR", "Método \"" + m.getNameAsString() + "\" deveria usar camelCase.", line(m)));
				}
				int lines = m.getBody().flatMap(b -> b.getRange()).map(r -> r.end.line - r.begin.line).orElse(0);
				if (lines > MAX_METHOD_LINES) {
					issues.add(issue("LONG_METHOD", "MINOR", "Método \"" + m.getNameAsString() + "\" tem " + lines
							+ " linhas; divida em métodos menores.", line(m)));
				}
				m.getBody().ifPresent(body -> {
					int depth = maxNesting(body, 0);
					if (depth > MAX_NESTING) {
						issues.add(issue("DEEP_NESTING", "MINOR", "Método \"" + m.getNameAsString() + "\" tem " + depth
								+ " níveis de aninhamento; simplifique com retornos antecipados ou métodos auxiliares.", line(m)));
					}
				});
			}
			for (FieldDeclaration f : unit.findAll(FieldDeclaration.class)) {
				boolean constant = f.isStatic() && f.isFinal();
				for (VariableDeclarator v : f.getVariables()) {
					String name = v.getNameAsString();
					if (constant && !name.matches("[A-Z][A-Z0-9_]*")) {
						issues.add(issue("NAMING", "MINOR", "Constante \"" + name + "\" deveria usar MAIÚSCULAS_COM_UNDERLINE.", line(f)));
					} else if (!constant && !name.matches("[a-z][A-Za-z0-9]*")) {
						issues.add(issue("NAMING", "MINOR", "Atributo \"" + name + "\" deveria usar camelCase.", line(f)));
					}
					boolean insideInterface = f.findAncestor(ClassOrInterfaceDeclaration.class).map(ClassOrInterfaceDeclaration::isInterface).orElse(false);
					if (f.hasModifier(Modifier.Keyword.PUBLIC) && !f.isFinal() && !insideInterface) {
						issues.add(issue("PUBLIC_FIELD", "MAJOR", "Atributo público \"" + name
								+ "\" quebra o encapsulamento; use private com getters/setters.", line(f)));
					}
				}
			}
			for (CatchClause c : unit.findAll(CatchClause.class)) {
				if (c.getBody().getStatements().isEmpty()) {
					issues.add(issue("EMPTY_CATCH", "MAJOR", "Bloco catch vazio esconde erros.", line(c)));
				}
				String caught = c.getParameter().getType().asString();
				if (caught.equals("Exception") || caught.equals("Throwable")) {
					issues.add(issue("GENERIC_CATCH", "MINOR", "Capture exceções específicas em vez de " + caught + ".", line(c)));
				}
			}
			for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
				if (call.getNameAsString().equals("exit") && call.getScope().map(s -> s.toString().equals("System")).orElse(false)) {
					issues.add(issue("SYSTEM_EXIT", "MAJOR", "Evite System.exit(); ele encerra a aplicação inteira.", line(call)));
				}
			}
			for (BinaryExpr b : unit.findAll(BinaryExpr.class)) {
				boolean equality = b.getOperator() == BinaryExpr.Operator.EQUALS || b.getOperator() == BinaryExpr.Operator.NOT_EQUALS;
				if (equality && (b.getLeft() instanceof StringLiteralExpr || b.getRight() instanceof StringLiteralExpr)) {
					issues.add(issue("STRING_EQUALITY", "MAJOR", "Compare Strings com equals(), não com == ou !=.", line(b)));
				}
			}
			if (file != null) {
				issues.forEach(i -> i.put("file", file));
			}
			return issues;
		}

		/** @Override ausente olhando todos os arquivos juntos (a superclasse pode estar em outro arquivo). */
		static List<Map<String, Object>> missingOverrides(List<CompilationUnit> units, boolean checkOverride) {
			List<Map<String, Object>> issues = new ArrayList<>();
			if (checkOverride) {
				for (String method : Concepts.missingOverrides(Concepts.typesOf(units))) {
					issues.add(issue("MISSING_OVERRIDE", "MINOR", method + " sobrescreve um método e deveria ter @Override.", 0));
				}
			}
			return issues;
		}

		static int maxNesting(Node node, int depth) {
			int max = depth;
			for (Node child : node.getChildNodes()) {
				boolean nests = child instanceof IfStmt || child instanceof ForStmt || child instanceof ForEachStmt
						|| child instanceof WhileStmt || child instanceof DoStmt || child instanceof SwitchStmt || child instanceof TryStmt;
				// "else if" continua no mesmo nível.
				boolean elseIf = child instanceof IfStmt && node instanceof IfStmt && ((IfStmt) node).getElseStmt().map(e -> e == child).orElse(false);
				max = Math.max(max, maxNesting(child, nests && !elseIf ? depth + 1 : depth));
			}
			return max;
		}

		static Map<String, Object> issue(String rule, String severity, String message, int line) {
			return entry("rule", rule, "severity", severity, "message", message, "line", line);
		}
	}

	// ------------------------------------------------------------------ JSON

	/** Escrita mínima de JSON (o contêiner não tem bibliotecas além do JUnit e do JavaParser). */
	static final class Json {
		static String write(Object value) {
			StringBuilder sb = new StringBuilder();
			append(sb, value);
			return sb.toString();
		}

		@SuppressWarnings("unchecked")
		static void append(StringBuilder sb, Object value) {
			if (value == null) {
				sb.append("null");
			} else if (value instanceof Boolean || value instanceof Number) {
				sb.append(value);
			} else if (value instanceof Map<?, ?> map) {
				sb.append('{');
				boolean first = true;
				for (Map.Entry<String, Object> e : ((Map<String, Object>) map).entrySet()) {
					if (!first) sb.append(',');
					first = false;
					append(sb, e.getKey());
					sb.append(':');
					append(sb, e.getValue());
				}
				sb.append('}');
			} else if (value instanceof List<?> list) {
				sb.append('[');
				for (int i = 0; i < list.size(); i++) {
					if (i > 0) sb.append(',');
					append(sb, list.get(i));
				}
				sb.append(']');
			} else {
				sb.append('"');
				for (char ch : value.toString().toCharArray()) {
					switch (ch) {
						case '"' -> sb.append("\\\"");
						case '\\' -> sb.append("\\\\");
						case '\n' -> sb.append("\\n");
						case '\r' -> sb.append("\\r");
						case '\t' -> sb.append("\\t");
						default -> {
							if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
							else sb.append(ch);
						}
					}
				}
				sb.append('"');
			}
		}
	}
}
