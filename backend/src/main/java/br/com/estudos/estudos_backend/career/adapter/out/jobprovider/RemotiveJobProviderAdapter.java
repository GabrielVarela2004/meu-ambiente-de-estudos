package br.com.estudos.estudos_backend.career.adapter.out.jobprovider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
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
public class RemotiveJobProviderAdapter implements JobProviderPort {

	private static final Logger log = LoggerFactory.getLogger(RemotiveJobProviderAdapter.class);
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	public RemotiveJobProviderAdapter() {
		this.objectMapper = new ObjectMapper();
		this.httpClient = CareerHttpClientFactory.create();
	}

	@Override
	public boolean supports(CareerJobSource source) {
		return source != null && source.getName() != null &&
				source.getName().toLowerCase().contains("remotive");
	}

	@Override
	public List<RawJobDto> fetchJobs(CareerJobSource source, CareerPreferences preferences, int limit) throws Exception {
		String targetUrl = source.getBaseUrl();
		if (targetUrl == null || targetUrl.isBlank()) {
			targetUrl = "https://remotive.com/api/remote-jobs";
		}
		if (!targetUrl.contains("?")) {
			targetUrl += "?category=software-dev&limit=" + Math.max(limit, 25);
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(targetUrl))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", "EstudosPlatform-CareerAgent/1.0")
				.GET()
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() >= 400) {
			throw new RuntimeException("Remotive API respondeu com status HTTP " + response.statusCode());
		}

		JsonNode root = objectMapper.readTree(response.body());
		JsonNode jobsNode = root.path("jobs");
		List<RawJobDto> results = new ArrayList<>();

		if (jobsNode.isArray()) {
			for (JsonNode item : jobsNode) {
				String externalId = String.valueOf(item.path("id").asText());
				String url = item.path("url").asText();
				String title = item.path("title").asText("Desenvolvedor");
				String company = item.path("company_name").asText("Empresa Confidencial");
				String location = item.path("candidate_required_location").asText("Remoto Global");
				String jobType = item.path("job_type").asText("full_time");
				String rawDate = item.path("publication_date").asText();
				String rawDesc = item.path("description").asText("");

				OffsetDateTime publishedAt;
				try {
					publishedAt = rawDate != null && !rawDate.isBlank()
							? OffsetDateTime.parse(rawDate)
							: OffsetDateTime.now();
				} catch (Exception ex) {
					publishedAt = OffsetDateTime.now();
				}

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
						externalId,
						url,
						title,
						company,
						location,
						"REMOTE",
						experienceLevel,
						null,
						null,
						"USD",
						rawDesc,
						"Tags: " + String.join(", ", tags),
						tags,
						publishedAt
				));
			}
		}

		log.info("Remotive API coletou {} vagas estruturadas.", results.size());
		return results;
	}

	@Override
	public boolean testConnection(CareerJobSource source) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(source.getBaseUrl() + "?limit=1"))
					.timeout(Duration.ofSeconds(6))
					.header("User-Agent", "EstudosPlatform-CareerAgent/1.0")
					.GET()
					.build();
			HttpResponse<Void> res = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
			return res.statusCode() == 200;
		} catch (Exception e) {
			log.warn("Teste de conexão falhou para Remotive: {}", e.getMessage());
			return false;
		}
	}
}
