package br.com.estudos.estudos_backend.material;

public interface YoutubeTranscriptExtractor {

	ExtractedYoutubeTranscript extract(String videoId);

	record ExtractedYoutubeTranscript(
			String videoId,
			String title,
			String language,
			String transcriptText) {
	}
}
