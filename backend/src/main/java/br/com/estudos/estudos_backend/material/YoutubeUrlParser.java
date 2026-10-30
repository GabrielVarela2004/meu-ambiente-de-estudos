package br.com.estudos.estudos_backend.material;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

import br.com.estudos.estudos_backend.common.exception.InvalidInputException;

public final class YoutubeUrlParser {

	private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{11}$");

	private YoutubeUrlParser() {
	}

	public static String extractVideoId(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			throw invalidUrl();
		}
		String trimmed = rawUrl.trim();
		// Link colado sem esquema (ex.: "youtu.be/ID") é tratado como https.
		if (!trimmed.contains("://")) {
			trimmed = "https://" + trimmed;
		}
		URI uri;
		try {
			uri = URI.create(trimmed);
		} catch (IllegalArgumentException exception) {
			throw invalidUrl();
		}

		String scheme = uri.getScheme();
		if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
			throw invalidUrl();
		}

		String host = uri.getHost();
		if (host == null) {
			throw invalidUrl();
		}
		String normalizedHost = host.toLowerCase(Locale.ROOT);
		if (normalizedHost.startsWith("www.")) {
			normalizedHost = normalizedHost.substring(4);
		} else if (normalizedHost.startsWith("m.")) {
			normalizedHost = normalizedHost.substring(2);
		}

		String candidateId = null;
		if (normalizedHost.equals("youtu.be")) {
			String path = uri.getPath();
			if (path != null && path.length() > 1) {
				String firstSegment = path.substring(1).split("/")[0];
				candidateId = firstSegment;
			}
		} else if (normalizedHost.equals("youtube.com") || normalizedHost.equals("youtube-nocookie.com")) {
			String path = uri.getPath() == null ? "" : uri.getPath();
			if (path.equals("/watch") || path.startsWith("/watch/")) {
				candidateId = extractQueryParam(uri.getRawQuery(), "v");
			} else if (path.startsWith("/shorts/")
					|| path.startsWith("/embed/")
					|| path.startsWith("/v/")
					|| path.startsWith("/live/")) {
				String[] parts = path.split("/");
				if (parts.length >= 3) {
					candidateId = parts[2];
				}
			}
			if (candidateId == null) {
				candidateId = extractQueryParam(uri.getRawQuery(), "v");
			}
		} else {
			throw invalidUrl();
		}

		if (candidateId == null || !VIDEO_ID_PATTERN.matcher(candidateId).matches()) {
			throw invalidUrl();
		}
		return candidateId;
	}

	public static String canonicalUrl(String videoId) {
		return "https://www.youtube.com/watch?v=" + videoId;
	}

	private static String extractQueryParam(String rawQuery, String paramName) {
		if (rawQuery == null || rawQuery.isBlank()) {
			return null;
		}
		for (String pair : rawQuery.split("&")) {
			int idx = pair.indexOf('=');
			if (idx > 0) {
				String key = pair.substring(0, idx);
				String value = pair.substring(idx + 1);
				if (paramName.equals(key) && !value.isBlank()) {
					return value;
				}
			}
		}
		return null;
	}

	private static InvalidInputException invalidUrl() {
		return new InvalidInputException(
				"URL do YouTube inválida. Informe um link válido como https://www.youtube.com/watch?v=... ou https://youtu.be/...");
	}
}
