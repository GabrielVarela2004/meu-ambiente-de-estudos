package br.com.estudos.estudos_backend.material;

/** Metadados públicos de um vídeo (oEmbed do YouTube, sem chave de API). */
public interface YoutubeOEmbedClient {

	/**
	 * Busca título, canal e miniatura. Vídeo inexistente, privado ou com incorporação desativada vira
	 * 422; falha de rede vira 503.
	 */
	VideoInfo fetch(String videoId);

	record VideoInfo(String videoId, String title, String channel, String thumbnailUrl) {}
}
