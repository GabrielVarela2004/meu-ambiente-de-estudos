package br.com.estudos.estudos_backend.material;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Importa páginas web públicas. Proteções:
 * <ul>
 *   <li>apenas http/https e portas padrão;</li>
 *   <li>bloqueia endereços internos (loopback, rede privada, link-local, multicast) — também em cada redirecionamento;</li>
 *   <li>limite de tamanho, tempo e número de redirecionamentos; só aceita HTML ou texto.</li>
 * </ul>
 */
@Component
public class HttpWebPageExtractor implements WebPageExtractor {

	private static final int MAX_REDIRECTS = 4;
	private static final int MAX_BODY_BYTES = 3_000_000;
	private static final int MAX_TEXT_CHARS = 100_000;
	private static final Pattern CHARSET = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	@Override
	public ExtractedWebPage extract(String rawUrl) {
		URI uri = validate(rawUrl);
		try {
			for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
				HttpRequest request = HttpRequest.newBuilder(uri)
						.timeout(Duration.ofSeconds(20))
						.header("User-Agent", "EstudosIA/1.0 (+importação de fontes de estudo)")
						.header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9")
						.GET()
						.build();
				HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
				int status = response.statusCode();
				if (status >= 300 && status < 400) {
					String location = response.headers().firstValue("Location").orElse(null);
					response.body().close();
					if (location == null) {
						throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A página redirecionou sem informar o destino.");
					}
					uri = validate(uri.resolve(location).toString());
					continue;
				}
				if (status < 200 || status >= 300) {
					response.body().close();
					throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
							"Não foi possível acessar a página (HTTP " + status + ").");
				}
				String contentType = response.headers().firstValue("Content-Type").orElse("text/html").toLowerCase(Locale.ROOT);
				if (!contentType.contains("text/html") && !contentType.contains("application/xhtml") && !contentType.contains("text/plain")) {
					response.body().close();
					throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
							"O link não aponta para uma página HTML ou de texto (tipo: " + contentType + ").");
				}
				byte[] body = readLimited(response.body());
				String html = new String(body, charsetOf(contentType));
				if (contentType.contains("text/plain")) {
					return new ExtractedWebPage(uri.toString(), uri.getHost(), truncate(html.trim()));
				}
				return parseHtml(html, uri.toString());
			}
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A página excedeu o limite de redirecionamentos.");
		} catch (ResponseStatusException e) {
			throw e;
		} catch (HttpTimeoutException e) {
			throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Tempo esgotado ao baixar a página.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O download da página foi interrompido.");
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha de rede ao baixar a página.");
		}
	}

	/** Extrai título e texto principal (artigo/conteúdo), descartando menus, rodapés e scripts. */
	static ExtractedWebPage parseHtml(String html, String url) {
		Document document = Jsoup.parse(html, url);
		document.select("script, style, noscript, template, svg, iframe, form, nav, header, footer, aside, "
				+ "[role=navigation], [role=banner], [role=contentinfo], [aria-hidden=true], .cookie, .advertisement").remove();

		Element root = document.selectFirst("article");
		if (root == null) {
			root = document.selectFirst("main, [role=main]");
		}
		if (root == null) {
			root = document.body();
		}

		StringBuilder text = new StringBuilder();
		if (root != null) {
			for (Element element : root.select("h1, h2, h3, h4, h5, h6, p, li, pre, blockquote, dt, dd, td, th, figcaption")) {
				// Evita duplicar texto de elementos aninhados (ex.: <p> dentro de <li>).
				if (element.parents().stream().anyMatch(parent -> parent != element && isCollected(parent))) {
					continue;
				}
				String line = element.tagName().equals("pre") ? element.wholeText().strip() : element.text().strip();
				if (line.isBlank()) {
					continue;
				}
				if (element.tagName().matches("h[1-6]")) {
					text.append('\n').append(line).append('\n');
				} else if (element.tagName().equals("li")) {
					text.append("• ").append(line).append('\n');
				} else {
					text.append(line).append('\n');
				}
			}
			// Páginas sem marcação semântica (texto solto em <div>): usa o texto bruto do conteúdo.
			String rawText = root.text();
			if (text.length() < rawText.length() / 2) {
				text.setLength(0);
				text.append(rawText);
			}
		}

		String title = document.title() == null || document.title().isBlank()
				? URI.create(url).getHost()
				: document.title().strip();
		String cleaned = text.toString().replaceAll("\n{3,}", "\n\n").strip();
		if (cleaned.isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
					"Não foi encontrado texto legível nesta página (ela pode depender de JavaScript).");
		}
		return new ExtractedWebPage(url, title, truncate(cleaned));
	}

	private static boolean isCollected(Element element) {
		return element.tagName().matches("p|li|pre|blockquote|dd|td|th|figcaption");
	}

	/** Valida esquema, porta e destino da URL; lança 400 para endereços não permitidos. */
	static URI validate(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a URL da página.");
		}
		URI uri;
		try {
			uri = URI.create(rawUrl.trim());
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL inválida.");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use um link http:// ou https://.");
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL sem endereço de servidor.");
		}
		if (uri.getUserInfo() != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URLs com usuário e senha não são aceitas.");
		}
		int port = uri.getPort();
		if (port != -1 && port != 80 && port != 443) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Somente as portas padrão (80 e 443) são permitidas.");
		}
		try {
			for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
				if (isInternal(address)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"Endereços internos ou locais não podem ser importados.");
				}
			}
		} catch (UnknownHostException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Servidor não encontrado: " + uri.getHost() + ".");
		}
		return uri;
	}

	static boolean isInternal(InetAddress address) {
		if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return true;
		}
		byte[] bytes = address.getAddress();
		if (address instanceof Inet6Address) {
			return (bytes[0] & 0xFE) == 0xFC; // fc00::/7 (unique local)
		}
		int first = bytes[0] & 0xFF;
		int second = bytes[1] & 0xFF;
		return first == 0 // 0.0.0.0/8
				|| (first == 100 && second >= 64 && second <= 127) // 100.64.0.0/10 (CGNAT)
				|| (first == 169 && second == 254) // link-local / metadados de nuvem
				|| first >= 224; // multicast e reservados
	}

	private static byte[] readLimited(InputStream in) throws IOException {
		try (in) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			int total = 0;
			while ((read = in.read(buffer)) != -1) {
				total += read;
				if (total > MAX_BODY_BYTES) {
					throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "A página é grande demais para importar (limite de 3 MB).");
				}
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		}
	}

	private static Charset charsetOf(String contentType) {
		Matcher matcher = CHARSET.matcher(contentType);
		if (matcher.find()) {
			try {
				return Charset.forName(matcher.group(1));
			} catch (Exception ignored) {
				// cai no padrão abaixo
			}
		}
		return StandardCharsets.UTF_8;
	}

	private static String truncate(String text) {
		return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
	}
}
