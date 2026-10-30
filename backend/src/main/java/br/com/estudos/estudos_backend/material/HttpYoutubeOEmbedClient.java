package br.com.estudos.estudos_backend.material;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** oEmbed público do YouTube ({@code https://www.youtube.com/oembed}), chamado só pelo backend. */
@Component
public class HttpYoutubeOEmbedClient implements YoutubeOEmbedClient {

	private static final String ENDPOINT = "https://www.youtube.com/oembed?format=json&url=";
	static final String UNAVAILABLE_MESSAGE = "Vídeo não encontrado, privado ou com incorporação desativada. "
			+ "Confira o link e se o vídeo é público.";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient;

	@Autowired
	public HttpYoutubeOEmbedClient() {
		this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL)
				.build());
	}

	public HttpYoutubeOEmbedClient(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public VideoInfo fetch(String videoId) {
		String videoUrl = YoutubeUrlParser.canonicalUrl(videoId);
		HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + URLEncoder.encode(videoUrl, StandardCharsets.UTF_8)))
				.timeout(Duration.ofSeconds(8))
				.header("Accept", "application/json")
				.GET()
				.build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			if (status == 400 || status == 401 || status == 403 || status == 404) {
				throw BusinessRuleException.unprocessable(UNAVAILABLE_MESSAGE);
			}
			if (status < 200 || status >= 300) {
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
						"O YouTube não respondeu à consulta do vídeo (HTTP " + status + "). Tente novamente.");
			}
			JsonNode root = objectMapper.readTree(response.body());
			String title = root.path("title").asText("").strip();
			String channel = root.path("author_name").asText("").strip();
			String thumbnail = root.path("thumbnail_url").asText("").strip();
			return new VideoInfo(videoId,
					title.isEmpty() ? null : title,
					channel.isEmpty() ? null : channel,
					thumbnail.startsWith("https://") ? thumbnail : "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg");
		} catch (ResponseStatusException e) {
			throw e;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "A consulta ao YouTube foi interrompida.");
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Não foi possível consultar o YouTube agora. Verifique a conexão e tente novamente.");
		}
	}
}
