package br.com.estudos.estudos_backend.material;

/**
 * Análise de um vídeo do YouTube por IA a partir da URL (hoje só o Gemini aceita).
 * Qualquer exceção em {@link #analyze} faz a importação cair na transcrição.
 */
public interface YoutubeVideoAnalyzer {

	/** Há provedor configurado e disponível (chave, habilitado, limite diário) para analisar vídeos. */
	boolean isAvailable();

	VideoAnalysis analyze(String videoUrl, String knownTitle);

	/** Conteúdo pronto para virar o texto da fonte, com as anotações marcadas por [MM:SS]. */
	record VideoAnalysis(String title, String content, Integer durationSeconds) {}
}
