package br.com.estudos.estudos_backend.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VideoAnalysisParserTest {

	@Test
	void buildsOrderedStudyNotesAndDropsInvalidTimestamps() {
		YoutubeVideoAnalyzer.VideoAnalysis analysis = VideoAnalysisParser.parse("""
				```json
				{"summary":"Resumo.","topics":["A"],"durationSeconds":"não é número",
				 "notes":[{"timestamp":"1:02:03","title":"Fim","content":"Encerramento."},
				          {"timestamp":"agora","title":"Inválida","content":"Some."},
				          {"timestamp":"00:05","title":"Início","content":"Começo."}]}
				```
				""");

		assertTrue(analysis.content().indexOf("[00:05] Início") < analysis.content().indexOf("[1:02:03] Fim"));
		assertFalse(analysis.content().contains("Inválida"));
		assertNull(analysis.durationSeconds());
		assertNull(analysis.title());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"não é json",
			"[]",
			"{\"notes\":[{\"timestamp\":\"00:01\",\"content\":\"x\"}]}",
			"{\"summary\":\"Resumo.\",\"notes\":[]}",
			"{\"summary\":\"Resumo.\",\"notes\":[{\"timestamp\":\"99\",\"content\":\"x\"}]}"
	})
	void rejectsAnalysesOutsideTheExpectedFormat(String json) {
		assertThrows(VideoAnalysisParser.InvalidVideoAnalysisException.class, () -> VideoAnalysisParser.parse(json));
	}

	@Test
	void convertsAndFormatsTimestamps() {
		assertEquals(195, VideoAnalysisParser.toSeconds("03:15"));
		assertEquals(3725, VideoAnalysisParser.toSeconds("1:02:05"));
		assertNull(VideoAnalysisParser.toSeconds("3:75"));
		assertEquals("03:15", VideoAnalysisParser.formatTimestamp(195));
		assertEquals("1:02:05", VideoAnalysisParser.formatTimestamp(3725));
	}
}
