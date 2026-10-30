package br.com.estudos.estudos_backend.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.estudos.estudos_backend.common.exception.InvalidInputException;

/** Formatos de link do YouTube aceitos; parâmetros de rastreamento e de tempo/playlist são ignorados. */
class YoutubeUrlParserTest {

	private static final String ID = "dQw4w9WgXcQ";

	@ParameterizedTest
	@ValueSource(strings = {
			// Botão "Compartilhar" do YouTube (com rastreamento ?si=)
			"https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123_-xyz",
			"https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123&t=42",
			"youtu.be/dQw4w9WgXcQ",
			// watch com tempo, playlist e rastreamento
			"https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90s",
			"https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG&index=3",
			"https://www.youtube.com/watch?app=desktop&v=dQw4w9WgXcQ&si=xyz&pp=ygUE",
			"https://youtube.com/watch?feature=shared&v=dQw4w9WgXcQ",
			// shorts, embed, live e versão mobile
			"https://www.youtube.com/shorts/dQw4w9WgXcQ?feature=share",
			"https://www.youtube.com/embed/dQw4w9WgXcQ?start=30",
			"https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
			"https://www.youtube.com/live/dQw4w9WgXcQ?si=abc",
			"https://m.youtube.com/watch?v=dQw4w9WgXcQ&feature=youtu.be",
			"https://m.youtube.com/shorts/dQw4w9WgXcQ",
			"  https://www.youtube.com/watch?v=dQw4w9WgXcQ  "
	})
	void extractsTheVideoIdFromEverySupportedFormat(String url) {
		assertEquals(ID, YoutubeUrlParser.extractVideoId(url));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"",
			"https://vimeo.com/123456789",
			"https://www.youtube.com/watch?v=curto",
			"https://www.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG",
			"https://www.youtube.com/@canal",
			"ftp://youtu.be/dQw4w9WgXcQ",
			"https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ"
	})
	void rejectsLinksWithoutAVideo(String url) {
		assertThrows(InvalidInputException.class, () -> YoutubeUrlParser.extractVideoId(url));
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://youtu.be/dQw4w9WgXcQ?si=AbCdEf", "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1&t=5"})
	void canonicalUrlDropsTrackingAndTimeParameters(String url) {
		assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
				YoutubeUrlParser.canonicalUrl(YoutubeUrlParser.extractVideoId(url)));
	}
}
