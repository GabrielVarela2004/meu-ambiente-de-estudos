package br.com.estudos.estudos_backend.material;

/** Baixa uma página web pública e extrai o texto principal para uso como fonte de estudo. */
public interface WebPageExtractor {

	ExtractedWebPage extract(String url);

	record ExtractedWebPage(String finalUrl, String title, String text) {
	}
}
