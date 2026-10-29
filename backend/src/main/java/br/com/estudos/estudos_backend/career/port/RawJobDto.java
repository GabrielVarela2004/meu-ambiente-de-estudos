package br.com.estudos.estudos_backend.career.port;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RawJobDto(
		String externalId,
		String url,
		String title,
		String company,
		String location,
		String modality,
		String experienceLevel,
		BigDecimal salaryMin,
		BigDecimal salaryMax,
		String salaryCurrency,
		String description,
		String requirementsSummary,
		List<String> tags,
		OffsetDateTime publishedAt
) {}
