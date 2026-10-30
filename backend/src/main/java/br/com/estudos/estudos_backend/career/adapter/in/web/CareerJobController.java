package br.com.estudos.estudos_backend.career.adapter.in.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerJobRepository;
import br.com.estudos.estudos_backend.career.domain.CareerJob;
import br.com.estudos.estudos_backend.career.domain.JobModality;
import br.com.estudos.estudos_backend.career.service.CareerJobCompatibilityService;
import br.com.estudos.estudos_backend.career.service.CareerJobCompatibilityService.JobMatchEvaluation;
import br.com.estudos.estudos_backend.career.service.CareerJobInsightService;
import br.com.estudos.estudos_backend.career.service.CareerSkillsProfileService;
import br.com.estudos.estudos_backend.career.service.CareerSkillsProfileService.UserCareerSkillsProfile;

@RestController
@RequestMapping("/api/career/jobs")
public class CareerJobController {

	private final CareerJobRepository jobRepository;
	private final CareerJobCompatibilityService compatibilityService;
	private final CareerSkillsProfileService skillsProfileService;
	private final CareerJobInsightService insightService;

	public CareerJobController(
			CareerJobRepository jobRepository,
			CareerJobCompatibilityService compatibilityService,
			CareerSkillsProfileService skillsProfileService,
			CareerJobInsightService insightService) {
		this.jobRepository = jobRepository;
		this.compatibilityService = compatibilityService;
		this.skillsProfileService = skillsProfileService;
		this.insightService = insightService;
	}

	@GetMapping
	public Page<CareerDto.JobItemResponse> listJobs(
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String modality,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {

		JobModality mod = null;
		if (modality != null && !modality.isBlank() && !modality.equalsIgnoreCase("ANY")) {
			try {
				mod = JobModality.valueOf(modality.toUpperCase());
			} catch (Exception ignored) {
			}
		}

		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
		Page<CareerJob> jobsPage;
		if (query != null && !query.isBlank()) {
			String pattern = "%" + query.trim().toLowerCase() + "%";
			jobsPage = jobRepository.searchJobsWithPattern(pattern, mod, pageable);
		} else {
			jobsPage = jobRepository.findAllByModality(mod, pageable);
		}

		UserCareerSkillsProfile profile = skillsProfileService.getUserSkillsProfile();
		List<CareerDto.JobItemResponse> items = jobsPage.getContent().stream()
				.map(j -> {
					JobMatchEvaluation eval = compatibilityService.evaluateCompatibility(j, profile);
					return CareerDto.JobItemResponse.from(j, eval);
				})
				.toList();

		return new PageImpl<>(items, pageable, jobsPage.getTotalElements());
	}

	@PatchMapping("/{id}/status")
	public CareerDto.JobItemResponse updateStatus(
			@PathVariable UUID id, @Valid @RequestBody CareerDto.JobStatusRequest request) {
		CareerJob saved = insightService.updateStatus(id, request.status(), request.notes());
		JobMatchEvaluation eval = compatibilityService.evaluateCompatibility(
				saved, skillsProfileService.getUserSkillsProfile());
		return CareerDto.JobItemResponse.from(saved, eval);
	}

	@PostMapping("/{id}/analysis")
	public CareerJobInsightService.JobAnalysis analyze(@PathVariable UUID id) {
		return insightService.analyze(id);
	}

	@PostMapping("/{id}/study-block")
	public ResponseEntity<CareerDto.JobStudyBlockResponse> createStudyBlock(@PathVariable UUID id) {
		CareerJobInsightService.JobStudyBlock result = insightService.createStudyBlock(id);
		CareerDto.JobStudyBlockResponse body = new CareerDto.JobStudyBlockResponse(
				result.studyBlockId(), result.title(), result.topics(), result.created());
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(body);
	}

	@GetMapping("/{id}")
	public ResponseEntity<CareerDto.JobItemResponse> getJob(@PathVariable UUID id) {
		return jobRepository.findById(id)
				.map(j -> {
					UserCareerSkillsProfile profile = skillsProfileService.getUserSkillsProfile();
					JobMatchEvaluation eval = compatibilityService.evaluateCompatibility(j, profile);
					return ResponseEntity.ok(CareerDto.JobItemResponse.from(j, eval));
				})
				.orElse(ResponseEntity.notFound().build());
	}
}
