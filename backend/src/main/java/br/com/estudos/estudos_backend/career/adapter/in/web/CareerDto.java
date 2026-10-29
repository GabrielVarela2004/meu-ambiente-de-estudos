package br.com.estudos.estudos_backend.career.adapter.in.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.career.domain.AgentExecutionStatus;
import br.com.estudos.estudos_backend.career.domain.AgentTriggerType;
import br.com.estudos.estudos_backend.career.domain.CareerAgentExecution;
import br.com.estudos.estudos_backend.career.domain.CareerJob;
import br.com.estudos.estudos_backend.career.domain.CareerJobSource;
import br.com.estudos.estudos_backend.career.domain.CareerPreferences;
import br.com.estudos.estudos_backend.career.domain.ExperienceLevel;
import br.com.estudos.estudos_backend.career.domain.JobModality;
import br.com.estudos.estudos_backend.career.service.CareerJobCompatibilityService.JobMatchEvaluation;

public class CareerDto {

	public record JobItemResponse(
			UUID id,
			String title,
			String company,
			String location,
			JobModality modality,
			ExperienceLevel experienceLevel,
			BigDecimal salaryMin,
			BigDecimal salaryMax,
			String salaryCurrency,
			String url,
			String sourceName,
			String description,
			String tags,
			OffsetDateTime publishedAt,
			OffsetDateTime discoveredAt,
			int compatibilityPercent,
			List<String> matchedRequirements,
			List<String> partialRequirements,
			List<String> missingRequirements,
			List<String> recommendedSkillsToDevelop,
			String compatibilityNote
	) {
		public static JobItemResponse from(CareerJob job, JobMatchEvaluation eval) {
			return new JobItemResponse(
					job.getId(),
					job.getTitle(),
					job.getCompany(),
					job.getLocation(),
					job.getModality(),
					job.getExperienceLevel(),
					job.getSalaryMin(),
					job.getSalaryMax(),
					job.getSalaryCurrency(),
					job.getUrl(),
					job.getSourceName(),
					job.getDescription(),
					job.getTags(),
					job.getPublishedAt(),
					job.getDiscoveredAt(),
					eval.matchPercent(),
					eval.matchedRequirements(),
					eval.partialRequirements(),
					eval.missingRequirements(),
					eval.recommendedSkillsToDevelop(),
					eval.summaryNote()
			);
		}
	}

	public record PreferencesRequest(
			String targetRoles,
			String experienceLevels,
			String technologies,
			String mandatoryKeywords,
			String preferredKeywords,
			String location,
			JobModality modality,
			BigDecimal minSalary,
			String searchDays,
			int searchTimeHour,
			int searchTimeMinute,
			int maxRoundMinutes,
			boolean agentEnabled
	) {}

	public record PreferencesResponse(
			UUID id,
			String targetRoles,
			String experienceLevels,
			String technologies,
			String mandatoryKeywords,
			String preferredKeywords,
			String location,
			JobModality modality,
			BigDecimal minSalary,
			String searchDays,
			int searchTimeHour,
			int searchTimeMinute,
			int maxRoundMinutes,
			boolean agentEnabled,
			OffsetDateTime updatedAt
	) {
		public static PreferencesResponse from(CareerPreferences p) {
			return new PreferencesResponse(
					p.getId(),
					p.getTargetRoles(),
					p.getExperienceLevels(),
					p.getTechnologies(),
					p.getMandatoryKeywords(),
					p.getPreferredKeywords(),
					p.getLocation(),
					p.getModality(),
					p.getMinSalary(),
					p.getSearchDays(),
					p.getSearchTimeHour(),
					p.getSearchTimeMinute(),
					p.getMaxRoundMinutes(),
					p.isAgentEnabled(),
					p.getUpdatedAt()
			);
		}
	}

	public record JobSourceResponse(
			UUID id,
			String name,
			String integrationType,
			String baseUrl,
			boolean enabled,
			OffsetDateTime lastCheckedAt,
			String lastStatus,
			String lastError,
			long rateLimitDelayMs,
			boolean requiresAuth
	) {
		public static JobSourceResponse from(CareerJobSource s) {
			return new JobSourceResponse(
					s.getId(),
					s.getName(),
					s.getIntegrationType(),
					s.getBaseUrl(),
					s.isEnabled(),
					s.getLastCheckedAt(),
					s.getLastStatus(),
					s.getLastError(),
					s.getRateLimitDelayMs(),
					s.isRequiresAuth()
			);
		}
	}

	public record AgentStatusResponse(
			boolean running,
			AgentExecutionResponse latestExecution,
			List<AgentExecutionResponse> recentExecutions
	) {}

	public record AgentExecutionResponse(
			UUID id,
			AgentTriggerType triggerType,
			AgentExecutionStatus status,
			OffsetDateTime startedAt,
			OffsetDateTime finishedAt,
			int jobsFound,
			int newJobsCount,
			long durationSeconds,
			String errorMessage,
			String executionDetails
	) {
		public static AgentExecutionResponse from(CareerAgentExecution e) {
			return new AgentExecutionResponse(
					e.getId(),
					e.getTriggerType(),
					e.getStatus(),
					e.getStartedAt(),
					e.getFinishedAt(),
					e.getJobsFound(),
					e.getNewJobsCount(),
					e.getDurationSeconds(),
					e.getErrorMessage(),
					e.getExecutionDetails()
			);
		}
	}
}
