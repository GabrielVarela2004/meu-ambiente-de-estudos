package br.com.estudos.estudos_backend.material;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

@Component
public class HttpYoutubeTranscriptExtractor implements YoutubeTranscriptExtractor {

	private static final int MAX_TRANSCRIPT_CHARS = 100_000;
	private static final Pattern INITIAL_PLAYER_RESPONSE_PATTERN = Pattern.compile(
			"ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\})\\s*;\\s*(?:var\\s+|</script>)",
			Pattern.DOTALL);
	private static final Pattern NUMERIC_ENTITY_PATTERN = Pattern.compile("&#(\\d+);");
	private static final Pattern HEX_ENTITY_PATTERN = Pattern.compile("&#x([0-9a-fA-F]+);");

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	public HttpYoutubeTranscriptExtractor() {
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public ExtractedYoutubeTranscript extract(String videoId) {
		JsonNode playerJson = fetchPlayerMetadata(videoId);
		CaptionMetadata metadata = parsePlayerResponseJson(videoId, playerJson);
		String rawCaptionPayload = downloadCaptionTrack(metadata.captionBaseUrl());
		String transcript = parseTranscriptPayload(rawCaptionPayload);
		if (transcript.isBlank()) {
			throw new ResponseStatusException(
					HttpStatus.valueOf(422),
					"Não foi possível extrair texto das legendas deste vídeo do YouTube.");
		}
		String truncated = transcript.length() > MAX_TRANSCRIPT_CHARS
				? transcript.substring(0, MAX_TRANSCRIPT_CHARS)
				: transcript;
		return new ExtractedYoutubeTranscript(
				videoId,
				metadata.title(),
				metadata.languageCode(),
				truncated);
	}

	private JsonNode fetchPlayerMetadata(String videoId) {
		try {
			String innertubeBody = """
					{
						"videoId": "%s",
						"context": {
							"client": {
								"hl": "pt",
								"gl": "BR",
								"clientName": "ANDROID",
								"clientVersion": "19.09.37",
								"androidSdkVersion": 30
							}
						}
					}
					""".formatted(videoId);
			HttpRequest playerReq = HttpRequest.newBuilder()
					.uri(URI.create("https://www.youtube.com/youtubei/v1/player?prettyPrint=false"))
					.timeout(Duration.ofSeconds(15))
					.header("Content-Type", "application/json")
					.header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
					.POST(HttpRequest.BodyPublishers.ofString(innertubeBody, StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> playerResp = httpClient.send(playerReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (playerResp.statusCode() == 200 && playerResp.body() != null && !playerResp.body().isBlank()) {
				JsonNode node = objectMapper.readTree(playerResp.body());
				if (node.has("captions") || node.has("playabilityStatus")) {
					JsonNode tracks = node.path("captions").path("playerCaptionsTracklistRenderer").path("captionTracks");
					if (tracks.isArray() && !tracks.isEmpty()) {
						return node;
					}
				}
			}

			HttpRequest watchReq = HttpRequest.newBuilder()
					.uri(URI.create("https://www.youtube.com/watch?v=" + videoId + "&hl=pt"))
					.timeout(Duration.ofSeconds(15))
					.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
					.header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
					.GET()
					.build();
			HttpResponse<String> watchResp = httpClient.send(watchReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (watchResp.statusCode() != 200 || watchResp.body() == null) {
				throw new ResponseStatusException(
						HttpStatus.BAD_GATEWAY,
						"Não foi possível consultar o vídeo no YouTube no momento (HTTP " + watchResp.statusCode() + ").");
			}
			Matcher matcher = INITIAL_PLAYER_RESPONSE_PATTERN.matcher(watchResp.body());
			if (matcher.find()) {
				return objectMapper.readTree(matcher.group(1));
			}
			throw new ResponseStatusException(
					HttpStatus.valueOf(422),
					"Não foi possível localizar os metadados de reprodução do vídeo informado.");
		} catch (ResponseStatusException exception) {
			throw exception;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"A consulta ao YouTube foi interrompida. Tente novamente.");
		} catch (IOException exception) {
			throw new ResponseStatusException(
					HttpStatus.BAD_GATEWAY,
					"Falha de comunicação ao consultar o YouTube. Verifique a conexão e tente novamente.");
		}
	}

	CaptionMetadata parsePlayerResponseJson(String videoId, JsonNode playerRoot) {
		if (playerRoot == null || playerRoot.isNull()) {
			throw new ResponseStatusException(
					HttpStatus.valueOf(422),
					"Resposta inválida ao consultar os dados do vídeo no YouTube.");
		}
		String playabilityStatus = playerRoot.path("playabilityStatus").path("status").asText("");
		if ("ERROR".equalsIgnoreCase(playabilityStatus)
				|| "LOGIN_REQUIRED".equalsIgnoreCase(playabilityStatus)
				|| ("UNPLAYABLE".equalsIgnoreCase(playabilityStatus) && playerRoot.path("videoDetails").isMissingNode())) {
			throw new ResponseStatusException(
					HttpStatus.valueOf(422),
					"O vídeo informado está indisponível, é privado ou possui restrição de acesso.");
		}

		String rawTitle = playerRoot.path("videoDetails").path("title").asText("").trim();
		String title = rawTitle.isBlank() ? "Vídeo YouTube (" + videoId + ")" : rawTitle;
		if (title.length() > 160) {
			title = title.substring(0, 160);
		}

		JsonNode captionTracks = playerRoot
				.path("captions")
				.path("playerCaptionsTracklistRenderer")
				.path("captionTracks");

		if (!captionTracks.isArray() || captionTracks.isEmpty()) {
			throw new ResponseStatusException(
					HttpStatus.valueOf(422),
					"Este vídeo não possui legendas ou transcrição disponíveis para extração.");
		}

		List<TrackCandidate> candidates = new ArrayList<>();
		for (JsonNode track : captionTracks) {
			String baseUrl = track.path("baseUrl").asText("").trim();
			String languageCode = track.path("languageCode").asText("und").trim();
			String kind = track.path("kind").asText("").trim();
			boolean isTranslatable = track.path("isTranslatable").asBoolean(false);
			if (!baseUrl.isBlank()) {
				candidates.add(new TrackCandidate(baseUrl, languageCode, "asr".equalsIgnoreCase(kind), isTranslatable));
			}
		}

		if (candidates.isEmpty()) {
			throw new ResponseStatusException(
					HttpStatus.valueOf(422),
					"Este vídeo não possui faixa de legenda acessível para extração.");
		}

		candidates.sort(Comparator
				.comparingInt(HttpYoutubeTranscriptExtractor::languagePriority)
				.thenComparing(TrackCandidate::autoGenerated));

		TrackCandidate selected = candidates.get(0);
		String lang = selected.languageCode().length() > 24
				? selected.languageCode().substring(0, 24)
				: selected.languageCode();
		return new CaptionMetadata(title, lang, selected.baseUrl());
	}

	private static int languagePriority(TrackCandidate candidate) {
		String lang = candidate.languageCode().toLowerCase();
		if (lang.equals("pt-br") || lang.equals("pt_br")) return 0;
		if (lang.startsWith("pt")) return 1;
		if (lang.startsWith("en")) return 2;
		if (lang.startsWith("es")) return 3;
		return 4;
	}

	private String downloadCaptionTrack(String captionBaseUrl) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(captionBaseUrl))
					.timeout(Duration.ofSeconds(15))
					.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
					.GET()
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
				throw new ResponseStatusException(
						HttpStatus.BAD_GATEWAY,
						"Não foi possível baixar a faixa de legendas do vídeo no YouTube.");
			}
			return response.body();
		} catch (ResponseStatusException exception) {
			throw exception;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"O download das legendas foi interrompido. Tente novamente.");
		} catch (IOException exception) {
			throw new ResponseStatusException(
					HttpStatus.BAD_GATEWAY,
					"Erro de rede ao baixar a transcrição do YouTube.");
		}
	}

	String parseTranscriptPayload(String payload) {
		if (payload == null || payload.isBlank()) {
			return "";
		}
		String trimmed = payload.trim();
		if (trimmed.startsWith("{")) {
			return parseJson3Transcript(trimmed);
		}
		return parseXmlTranscript(trimmed);
	}

	private String parseJson3Transcript(String jsonPayload) {
		try {
			JsonNode root = objectMapper.readTree(jsonPayload);
			JsonNode events = root.path("events");
			if (!events.isArray()) {
				return "";
			}
			TimedText sb = new TimedText();
			for (JsonNode event : events) {
				JsonNode segs = event.path("segs");
				if (segs.isArray()) {
					StringBuilder line = new StringBuilder();
					for (JsonNode seg : segs) {
						String utf8 = seg.path("utf8").asText("");
						if (!utf8.isBlank()) {
							line.append(utf8);
						}
					}
					sb.append(event.has("tStartMs") ? event.path("tStartMs").asLong() / 1000.0 : null, line.toString());
				}
			}
			return sb.toString().trim();
		} catch (Exception exception) {
			throw new ResponseStatusException(
					HttpStatus.valueOf(422),
					"Formato de transcrição JSON do YouTube inválido.");
		}
	}

	private String parseXmlTranscript(String xmlPayload) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(new ByteArrayInputStream(xmlPayload.getBytes(StandardCharsets.UTF_8)));

			TimedText sb = new TimedText();
			NodeList textNodes = doc.getElementsByTagName("text");
			if (textNodes.getLength() > 0) {
				for (int i = 0; i < textNodes.getLength(); i++) {
					sb.append(secondsAttribute(textNodes.item(i), "start", 1.0), textNodes.item(i).getTextContent());
				}
			} else {
				NodeList pNodes = doc.getElementsByTagName("p");
				for (int i = 0; i < pNodes.getLength(); i++) {
					sb.append(secondsAttribute(pNodes.item(i), "t", 1000.0), pNodes.item(i).getTextContent());
				}
			}
			return sb.toString().trim();
		} catch (Exception exception) {
			throw new ResponseStatusException(
					HttpStatus.valueOf(422),
					"Não foi possível processar o arquivo XML de legendas do YouTube.");
		}
	}

	/**
	 * Texto da transcrição com marcações [MM:SS] no início e a cada {@value #MARKER_INTERVAL_SECONDS} s ou mais,
	 * para o chat e as questões citarem o minuto do vídeo. Segmentos sem tempo entram sem marcação.
	 */
	static final class TimedText {
		static final int MARKER_INTERVAL_SECONDS = 30;
		private final StringBuilder sb = new StringBuilder();
		private double lastMarker = -MARKER_INTERVAL_SECONDS;

		void append(Double startSeconds, String rawSegment) {
			if (startSeconds != null && startSeconds >= 0 && startSeconds - lastMarker >= MARKER_INTERVAL_SECONDS
					&& rawSegment != null && !rawSegment.isBlank()) {
				lastMarker = startSeconds;
				if (!sb.isEmpty()) {
					sb.append('\n');
				}
				sb.append('[').append(VideoAnalysisParser.formatTimestamp((int) Math.floor(startSeconds))).append(']');
			}
			appendCleanSegment(sb, rawSegment);
		}

		@Override
		public String toString() {
			return sb.toString();
		}
	}

	private static Double secondsAttribute(org.w3c.dom.Node node, String attribute, double divisor) {
		org.w3c.dom.Node value = node.getAttributes() == null ? null : node.getAttributes().getNamedItem(attribute);
		if (value == null) {
			return null;
		}
		try {
			return Double.parseDouble(value.getNodeValue()) / divisor;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static void appendCleanSegment(StringBuilder sb, String rawSegment) {
		if (rawSegment == null || rawSegment.isBlank()) {
			return;
		}
		String decoded = decodeHtmlEntities(rawSegment)
				.replaceAll("<[^>]+>", " ")
				.replaceAll("\\s+", " ")
				.trim();
		if (decoded.isBlank()) {
			return;
		}
		if (!sb.isEmpty()) {
			sb.append(' ');
		}
		sb.append(decoded);
	}

	static String decodeHtmlEntities(String input) {
		if (input == null || input.isEmpty()) {
			return "";
		}
		String result = input
				.replace("&amp;", "&")
				.replace("&lt;", "<")
				.replace("&gt;", ">")
				.replace("&quot;", "\"")
				.replace("&apos;", "'")
				.replace("&#39;", "'")
				.replace("&nbsp;", " ");

		Matcher decMatcher = NUMERIC_ENTITY_PATTERN.matcher(result);
		StringBuilder decBuffer = new StringBuilder();
		while (decMatcher.find()) {
			try {
				int codePoint = Integer.parseInt(decMatcher.group(1));
				decMatcher.appendReplacement(decBuffer, Matcher.quoteReplacement(Character.toString(codePoint)));
			} catch (IllegalArgumentException ignored) {
				decMatcher.appendReplacement(decBuffer, decMatcher.group(0));
			}
		}
		decMatcher.appendTail(decBuffer);

		Matcher hexMatcher = HEX_ENTITY_PATTERN.matcher(decBuffer.toString());
		StringBuilder hexBuffer = new StringBuilder();
		while (hexMatcher.find()) {
			try {
				int codePoint = Integer.parseInt(hexMatcher.group(1), 16);
				hexMatcher.appendReplacement(hexBuffer, Matcher.quoteReplacement(Character.toString(codePoint)));
			} catch (IllegalArgumentException ignored) {
				hexMatcher.appendReplacement(hexBuffer, hexMatcher.group(0));
			}
		}
		hexMatcher.appendTail(hexBuffer);
		return hexBuffer.toString();
	}

	record CaptionMetadata(String title, String languageCode, String captionBaseUrl) {
	}

	private record TrackCandidate(String baseUrl, String languageCode, boolean autoGenerated, boolean translatable) {
	}
}
