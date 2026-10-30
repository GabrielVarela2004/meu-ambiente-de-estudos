package br.com.estudos.estudos_backend.material;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Valida o JSON da análise de vídeo devolvido pela IA e monta o texto da fonte:
 * resumo, tópicos e anotações em ordem, cada uma marcada com [MM:SS] (ou [H:MM:SS]).
 * JSON fora do formato lança {@link InvalidVideoAnalysisException}, e a importação cai na transcrição.
 */
public final class VideoAnalysisParser {

	/** Esquema pedido à IA; também usado no prompt. */
	public static final String JSON_SCHEMA = """
			{"title":"título do vídeo","durationSeconds":1234,
			 "summary":"resumo do vídeo em 1 a 3 parágrafos",
			 "topics":["tópico abordado", "..."],
			 "notes":[{"timestamp":"MM:SS","title":"assunto do trecho",
			           "content":"anotação de estudo detalhada do trecho, com definições e exemplos explicados",
			           "code":"código mostrado na tela neste trecho, literal (ou vazio)"}]}""";

	private static final Pattern TIMESTAMP = Pattern.compile("^(?:(\\d{1,2}):)?([0-5]?\\d):([0-5]\\d)$");
	private static final int MAX_CONTENT_CHARS = 200_000;
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private VideoAnalysisParser() {
	}

	public static YoutubeVideoAnalyzer.VideoAnalysis parse(String rawJson) {
		JsonNode root;
		try {
			root = MAPPER.readTree(stripCodeFence(rawJson));
		} catch (JsonProcessingException | IllegalArgumentException e) {
			throw new InvalidVideoAnalysisException("A análise do vídeo não veio em JSON válido.");
		}
		if (root == null || !root.isObject()) {
			throw new InvalidVideoAnalysisException("A análise do vídeo não veio no formato esperado.");
		}
		String summary = root.path("summary").asText("").strip();
		if (summary.isEmpty()) {
			throw new InvalidVideoAnalysisException("A análise do vídeo veio sem resumo.");
		}

		List<Note> notes = new ArrayList<>();
		for (JsonNode node : root.path("notes")) {
			Integer seconds = toSeconds(node.path("timestamp").asText(""));
			String content = node.path("content").asText("").strip();
			if (seconds == null || content.isEmpty()) {
				continue;
			}
			notes.add(new Note(seconds, node.path("title").asText("").strip(), content, node.path("code").asText("").strip()));
		}
		if (notes.isEmpty()) {
			throw new InvalidVideoAnalysisException("A análise do vídeo veio sem anotações com marcação de tempo.");
		}
		notes.sort((a, b) -> Integer.compare(a.seconds(), b.seconds()));

		List<String> topics = new ArrayList<>();
		for (JsonNode topic : root.path("topics")) {
			String text = topic.asText("").strip();
			if (!text.isEmpty()) {
				topics.add(text);
			}
		}

		StringBuilder text = new StringBuilder("Resumo do vídeo\n").append(summary).append("\n");
		if (!topics.isEmpty()) {
			text.append("\nTópicos abordados\n");
			topics.forEach(topic -> text.append("- ").append(topic).append("\n"));
		}
		text.append("\nAnotações de estudo (com marcação de tempo)\n");
		for (Note note : notes) {
			text.append("\n[").append(formatTimestamp(note.seconds())).append("] ");
			text.append(note.title().isEmpty() ? "Trecho" : note.title()).append("\n");
			text.append(note.content()).append("\n");
			if (!note.code().isEmpty()) {
				text.append("Código mostrado na tela:\n```\n").append(note.code()).append("\n```\n");
			}
		}
		String content = text.toString().strip();
		if (content.length() > MAX_CONTENT_CHARS) {
			content = content.substring(0, MAX_CONTENT_CHARS);
		}

		String title = root.path("title").asText("").strip();
		JsonNode duration = root.path("durationSeconds");
		Integer durationSeconds = duration.canConvertToInt() && duration.asInt() > 0 ? duration.asInt() : null;
		return new YoutubeVideoAnalyzer.VideoAnalysis(title.isEmpty() ? null : title, content, durationSeconds);
	}

	/** "MM:SS" ou "H:MM:SS" em segundos; formato inválido devolve null. */
	static Integer toSeconds(String timestamp) {
		Matcher matcher = TIMESTAMP.matcher(timestamp == null ? "" : timestamp.strip());
		if (!matcher.matches()) {
			return null;
		}
		int hours = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
		return hours * 3600 + Integer.parseInt(matcher.group(2)) * 60 + Integer.parseInt(matcher.group(3));
	}

	public static String formatTimestamp(int totalSeconds) {
		int hours = totalSeconds / 3600;
		int minutes = (totalSeconds % 3600) / 60;
		int seconds = totalSeconds % 60;
		return hours > 0
				? String.format("%d:%02d:%02d", hours, minutes, seconds)
				: String.format("%02d:%02d", minutes, seconds);
	}

	private static String stripCodeFence(String raw) {
		String trimmed = raw == null ? "" : raw.strip();
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			trimmed = firstNewline < 0 ? "" : trimmed.substring(firstNewline + 1);
			if (trimmed.endsWith("```")) {
				trimmed = trimmed.substring(0, trimmed.length() - 3);
			}
		}
		return trimmed.strip();
	}

	private record Note(int seconds, String title, String content, String code) {}

	/** Resposta da IA fora do formato: a importação usa a transcrição. */
	public static class InvalidVideoAnalysisException extends RuntimeException {
		public InvalidVideoAnalysisException(String message) {
			super(message);
		}
	}
}
