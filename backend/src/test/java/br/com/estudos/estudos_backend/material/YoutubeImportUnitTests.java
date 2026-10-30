package br.com.estudos.estudos_backend.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YoutubeImportUnitTests {

	private final ObjectMapper mapper = new ObjectMapper();
	private final HttpYoutubeTranscriptExtractor extractor = new HttpYoutubeTranscriptExtractor();

	@Test
	void extractsVideoIdFromStandardShortAndQueryYoutubeUrls() {
		assertEquals("dQw4w9WgXcQ",
				YoutubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
		assertEquals("dQw4w9WgXcQ",
				YoutubeUrlParser.extractVideoId("https://youtube.com/watch?feature=shared&v=dQw4w9WgXcQ&t=90s"));
		assertEquals("dQw4w9WgXcQ",
				YoutubeUrlParser.extractVideoId("https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123"));
		assertEquals("dQw4w9WgXcQ",
				YoutubeUrlParser.extractVideoId("https://m.youtube.com/shorts/dQw4w9WgXcQ"));
		assertEquals("dQw4w9WgXcQ",
				YoutubeUrlParser.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"));
	}

	@Test
	void rejectsInvalidYoutubeUrls() {
		assertThrows(ResponseStatusException.class, () -> YoutubeUrlParser.extractVideoId(""));
		assertThrows(ResponseStatusException.class, () -> YoutubeUrlParser.extractVideoId("https://vimeo.com/123456789"));
		assertThrows(ResponseStatusException.class, () -> YoutubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=curto"));
		assertThrows(ResponseStatusException.class, () -> YoutubeUrlParser.extractVideoId("ftp://youtu.be/dQw4w9WgXcQ"));
	}

	@Test
	void prioritizesPortugueseManualCaptionTrackAndExtractsMetadata() throws Exception {
		String json = """
				{
					"playabilityStatus": { "status": "OK" },
					"videoDetails": { "title": "Curso Completo de Spring Boot" },
					"captions": {
						"playerCaptionsTracklistRenderer": {
							"captionTracks": [
								{ "baseUrl": "https://youtube.com/api/timedtext?lang=en", "languageCode": "en", "kind": "asr" },
								{ "baseUrl": "https://youtube.com/api/timedtext?lang=pt-BR&kind=asr", "languageCode": "pt-BR", "kind": "asr" },
								{ "baseUrl": "https://youtube.com/api/timedtext?lang=pt-BR", "languageCode": "pt-BR" }
							]
						}
					}
				}
				""";
		JsonNode root = mapper.readTree(json);
		HttpYoutubeTranscriptExtractor.CaptionMetadata meta = extractor.parsePlayerResponseJson("dQw4w9WgXcQ", root);

		assertEquals("Curso Completo de Spring Boot", meta.title());
		assertEquals("pt-BR", meta.languageCode());
		assertEquals("https://youtube.com/api/timedtext?lang=pt-BR", meta.captionBaseUrl());
	}

	@Test
	void rejectsVideoWithoutCaptionsOrRestrictedAccess() throws Exception {
		JsonNode noCaptions = mapper.readTree("""
				{
					"playabilityStatus": { "status": "OK" },
					"videoDetails": { "title": "Vídeo sem legenda" }
				}
				""");
		ResponseStatusException ex1 = assertThrows(
				ResponseStatusException.class,
				() -> extractor.parsePlayerResponseJson("dQw4w9WgXcQ", noCaptions));
		assertEquals(422, ex1.getStatusCode().value());

		JsonNode restricted = mapper.readTree("""
				{
					"playabilityStatus": { "status": "LOGIN_REQUIRED" }
				}
				""");
		ResponseStatusException ex2 = assertThrows(
				ResponseStatusException.class,
				() -> extractor.parsePlayerResponseJson("dQw4w9WgXcQ", restricted));
		assertEquals(422, ex2.getStatusCode().value());
	}

	@Test
	void parsesXmlAndJson3TranscriptPayloadsAndDecodesEntities() {
		String xml = """
				<?xml version="1.0" encoding="utf-8" ?>
				<transcript>
					<text start="0.5" dur="3.2">Olá &amp; bem-vindos ao curso de &#39;Java&#39;!</text>
					<text start="3.7" dur="4.0">Hoje estudaremos &quot;Coleções&quot; e Streams.</text>
				</transcript>
				""";
		assertEquals(
				"[00:00] Olá & bem-vindos ao curso de 'Java'! Hoje estudaremos \"Coleções\" e Streams.",
				extractor.parseTranscriptPayload(xml));

		String json3 = """
				{
					"events": [
						{ "segs": [ { "utf8": "Primeira frase " }, { "utf8": "do vídeo." } ] },
						{ "segs": [ { "utf8": "Segunda frase com &amp; entidade." } ] }
					]
				}
				""";
		assertEquals(
				"Primeira frase do vídeo. Segunda frase com & entidade.",
				extractor.parseTranscriptPayload(json3));
	}

	@Test
	void transcriptGetsTimestampMarkersEveryThirtySecondsOrMore() {
		String json3 = """
				{
					"events": [
						{ "tStartMs": 0, "segs": [ { "utf8": "Abertura." } ] },
						{ "tStartMs": 12000, "segs": [ { "utf8": "Ainda no começo." } ] },
						{ "tStartMs": 31500, "segs": [ { "utf8": "Segundo trecho." } ] },
						{ "tStartMs": 3725000, "segs": [ { "utf8": "Depois de uma hora." } ] }
					]
				}
				""";
		assertEquals(
				"[00:00] Abertura. Ainda no começo.\n[00:31] Segundo trecho.\n[1:02:05] Depois de uma hora.",
				extractor.parseTranscriptPayload(json3));

		String srv3 = """
				<timedtext><body><p t="0" d="1000">Primeiro.</p><p t="45000" d="1000">Quarenta e cinco.</p></body></timedtext>
				""";
		assertEquals("[00:00] Primeiro.\n[00:45] Quarenta e cinco.", extractor.parseTranscriptPayload(srv3));
	}
}
