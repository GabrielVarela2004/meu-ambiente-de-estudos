package br.com.estudos.estudos_backend.exercise;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.common.exception.InvalidInputException;

/**
 * Regras dos arquivos de um exercício prático: validação de segurança (limites, só .java, sem path traversal),
 * conversão da submissão antiga em texto único e serialização para o banco.
 */
public final class ExerciseFiles {

	public static final int MAX_FILES = 20;
	public static final int MAX_TOTAL_BYTES = 100 * 1024;

	/** Pastas e nome com caracteres de identificador Java, separados por "/", terminando em .java. */
	private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(/[A-Za-z_$][A-Za-z0-9_$]*)*\\.java");
	private static final Pattern PUBLIC_TYPE = Pattern.compile(
			"public\\s+(?:(?:abstract|final|sealed|non-sealed|static|strictfp)\\s+)*(?:class|interface|enum|record|@interface)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
	private static final Pattern ANY_TYPE = Pattern.compile(
			"(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<List<ExerciseFile>> LIST_TYPE = new TypeReference<>() {};

	private ExerciseFiles() {
	}

	/** Valida e normaliza (barras invertidas viram "/"); lança 400 com mensagem clara quando algo é inválido. */
	public static List<ExerciseFile> validate(List<ExerciseFile> files) {
		if (files == null || files.isEmpty()) {
			throw new InvalidInputException("Envie ao menos um arquivo .java.");
		}
		if (files.size() > MAX_FILES) {
			throw new InvalidInputException("O projeto pode ter no máximo " + MAX_FILES + " arquivos.");
		}
		Set<String> seen = new HashSet<>();
		long total = 0;
		List<ExerciseFile> normalized = new ArrayList<>();
		for (ExerciseFile file : files) {
			if (file == null || file.path() == null || file.path().isBlank()) {
				throw new InvalidInputException("Todo arquivo precisa de um caminho.");
			}
			String path = file.path().trim().replace('\\', '/');
			if (path.startsWith("/") || path.contains("..") || path.contains(":")) {
				throw new InvalidInputException("Caminho de arquivo não permitido: " + path);
			}
			if (!SAFE_PATH.matcher(path).matches()) {
				throw new InvalidInputException("Somente arquivos .java com nomes válidos são aceitos: " + path);
			}
			if (!seen.add(path.toLowerCase(Locale.ROOT))) {
				throw new InvalidInputException("Arquivo duplicado: " + path);
			}
			String content = file.content() == null ? "" : file.content();
			total += content.getBytes(StandardCharsets.UTF_8).length;
			if (total > MAX_TOTAL_BYTES) {
				throw new InvalidInputException("O projeto passou do limite de 100 KB de código.");
			}
			normalized.add(new ExerciseFile(path, content));
		}
		return List.copyOf(normalized);
	}

	/** Submissão antiga (texto único): vira um arquivo com o nome da classe pública, ou Main.java. */
	public static List<ExerciseFile> fromSingleSource(String source) {
		String code = source == null ? "" : source;
		return List.of(new ExerciseFile(fileNameFor(code), code));
	}

	/** Nome do arquivo para um código: o tipo público, senão o primeiro tipo declarado, senão Main.java. */
	public static String fileNameFor(String code) {
		Matcher pub = PUBLIC_TYPE.matcher(code == null ? "" : code);
		if (pub.find()) {
			return pub.group(1) + ".java";
		}
		Matcher any = ANY_TYPE.matcher(code == null ? "" : code);
		return any.find() ? any.group(1) + ".java" : "Main.java";
	}

	/** Texto único com todos os arquivos, para histórico, tutoria e telas antigas. */
	public static String joined(List<ExerciseFile> files) {
		if (files.size() == 1) {
			return files.get(0).content();
		}
		StringBuilder sb = new StringBuilder();
		for (ExerciseFile file : files) {
			if (!sb.isEmpty()) {
				sb.append("\n\n");
			}
			sb.append("// Arquivo: ").append(file.path()).append('\n').append(file.content());
		}
		return sb.toString();
	}

	public static String toJson(List<ExerciseFile> files) {
		try {
			return MAPPER.writeValueAsString(files);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Não foi possível serializar os arquivos do exercício.", e);
		}
	}

	/** Lê o JSON gravado; vazio ou inválido devolve lista vazia. */
	public static List<ExerciseFile> fromJson(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			List<ExerciseFile> files = MAPPER.readValue(json, LIST_TYPE);
			return files == null ? List.of() : List.copyOf(files);
		} catch (JsonProcessingException e) {
			return List.of();
		}
	}
}
