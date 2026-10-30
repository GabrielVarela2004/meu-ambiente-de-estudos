package br.com.estudos.estudos_backend.material;

/** Como o conteúdo de uma fonte do YouTube foi obtido. */
public enum VideoAnalysisMethod {
	/** Vídeo analisado pela IA (Gemini) a partir da URL: resumo, tópicos e anotações com tempo. */
	ANALISE_IA,
	/** Plano B: transcrição (legendas) do vídeo. */
	TRANSCRICAO
}
