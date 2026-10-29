package br.com.estudos.estudos_backend.career.adapter.out.jobprovider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.career.domain.CareerJobSource;
import br.com.estudos.estudos_backend.career.domain.CareerPreferences;
import br.com.estudos.estudos_backend.career.port.JobProviderPort;
import br.com.estudos.estudos_backend.career.port.RawJobDto;

@Component
public class ArbeitnowJobProviderAdapter implements JobProviderPort {

	private static final Logger log = LoggerFactory.getLogger(ArbeitnowJobProviderAdapter.class);
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	public ArbeitnowJobProviderAdapter() {
		this.objectMapper = new ObjectMapper();
		this.httpClient = CareerHttpClientFactory.create();
	}

	@Override
	public boolean supports(CareerJobSource source) {
		return source != null && source.getName() != null &&
				source.getName().toLowerCase().contains("arbeitnow");
	}

	@Override
	public List<RawJobDto> fetchJobs(CareerJobSource source, CareerPreferences preferences, int limit) throws Exception {
		String targetUrl = source.getBaseUrl();
		if (targetUrl == null || targetUrl.isBlank()) {
			targetUrl = "https://www.arbeitnow.com/api/job-board-api";
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(targetUrl))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", "EstudosPlatform-CareerAgent/1.0")
				.GET()
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() >= 400) {
			throw new RuntimeException("Arbeitnow API respondeu com status HTTP " + response.statusCode());
		}

		JsonNode root = objectMapper.readTree(response.body());
		JsonNode dataNode = root.path("data");
		List<RawJobDto> results = new ArrayList<>();

		if (dataNode.isArray()) {
			for (JsonNode item : dataNode) {
				String slug = item.path("slug").asText();
				String url = item.path("url").asText("https://www.arbeitnow.com/jobs/" + slug);
				String title = item.path("title").asText("Software Engineer");
				String company = item.path("company_name").asText("Tech Company");
				String location = item.path("location").asText("Remote");
				boolean isRemote = item.path("remote").asBoolean(true);
				String rawDesc = item.path("description").asText("");
				long epochSeconds = item.path("created_at").asLong(Instant.now().getEpochSecond());

				OffsetDateTime publishedAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);

				List<String> tags = new ArrayList<>();
				JsonNode tagsNode = item.path("tags");
				if (tagsNode.isArray()) {
					for (JsonNode t : tagsNode) {
						tags.add(t.asText());
					}
				}

				String lowerTitle = title.toLowerCase();
				String experienceLevel = "JUNIOR";
				if (lowerTitle.contains("intern") || lowerTitle.contains("estagi")) {
					experienceLevel = "INTERNSHIP";
				} else if (lowerTitle.contains("trainee")) {
					experienceLevel = "TRAINEE";
				} else if (lowerTitle.contains("senior") || lowerTitle.contains("sr.") || lowerTitle.contains("lead")) {
					experienceLevel = "SENIOR";
				} else if (lowerTitle.contains("mid") || lowerTitle.contains("pleno")) {
					experienceLevel = "MID";
				}

				results.add(new RawJobDto(
						slug,
						url,
						title,
						company,
						location,
						isRemote ? "REMOTE" : "HYBRID",
						experienceLevel,
						null,
						null,
						"EUR",
						rawDesc,
						"Tags: " + String.join(", ", tags),
						tags,
						publishedAt
				));

				if (results.size() >= limit) {
					break;
				}
			}
		}

		log.info("Arbeitnow API coletou {} vagas estruturadas.", results.size());
		return results;
	}

	@Override
	public boolean testConnection(CareerJobSource source) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(source.getBaseUrl()))
					.timeout(Duration.ofSeconds(6))
					.header("User-Agent", "EstudosPlatform-CareerAgent/1.0")
					.GET()
					.build();
			HttpResponse<Void> res = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
			return res.statusCode() == 200;
		} catch (Exception e) {
			log.warn("Teste de conexão falhou para Arbeitnow: {}", e.getMessage());
			return false;
		}
	}
}
