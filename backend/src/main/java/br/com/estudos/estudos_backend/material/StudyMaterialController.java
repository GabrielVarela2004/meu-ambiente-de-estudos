package br.com.estudos.estudos_backend.material;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-blocks/{studyBlockId}/materials")
public class StudyMaterialController {

	private final StudyMaterialService service;
	private final YoutubeImportService youtubeImportService;

	public StudyMaterialController(StudyMaterialService service, YoutubeImportService youtubeImportService) {
		this.service = service;
		this.youtubeImportService = youtubeImportService;
	}

	@PostMapping
	public ResponseEntity<StudyMaterialResponse> create(
			@PathVariable UUID studyBlockId, @Valid @RequestBody StudyMaterialRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(StudyMaterialResponse.from(service.create(studyBlockId, request.title(), request.content())));
	}

	@PostMapping(path = "/import", consumes = "multipart/form-data")
	public ResponseEntity<StudyMaterialResponse> importFile(
			@PathVariable UUID studyBlockId, @RequestPart("file") MultipartFile file) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(StudyMaterialResponse.from(service.importTextFile(studyBlockId, file)));
	}

	@PostMapping("/youtube")
	public ResponseEntity<StudyMaterialResponse> importYoutube(
			@PathVariable UUID studyBlockId, @Valid @RequestBody YoutubeMaterialRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(StudyMaterialResponse.from(youtubeImportService.importVideo(studyBlockId, request.url())));
	}

	/** Prévia do vídeo antes de salvar (oEmbed público, sem chave). */
	@GetMapping("/youtube/preview")
	public YoutubeImportService.YoutubePreview previewYoutube(
			@PathVariable UUID studyBlockId, @RequestParam @NotBlank @Size(max = 1000) String url) {
		return youtubeImportService.preview(studyBlockId, url);
	}

	/** Nova tentativa de processar um vídeo (após erro ou para tentar a análise por IA de novo). */
	@PostMapping("/{id}/reprocess")
	public StudyMaterialResponse reprocessYoutube(@PathVariable UUID studyBlockId, @PathVariable UUID id) {
		return StudyMaterialResponse.from(youtubeImportService.reprocess(studyBlockId, id));
	}

	@PostMapping("/web")
	public ResponseEntity<StudyMaterialResponse> importWebPage(
			@PathVariable UUID studyBlockId, @Valid @RequestBody WebPageMaterialRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(StudyMaterialResponse.from(service.importWebPage(studyBlockId, request.url())));
	}

	@PostMapping("/{id}/summary")
	public StudyMaterialResponse generateSummary(@PathVariable UUID studyBlockId, @PathVariable UUID id) {
		return StudyMaterialResponse.from(service.generateSummary(studyBlockId, id));
	}

	@GetMapping
	public List<StudyMaterialResponse> list(
			@PathVariable UUID studyBlockId, @RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.list(studyBlockId, includeArchived).stream().map(StudyMaterialResponse::from).toList();
	}

	@PutMapping("/{id}")
	public StudyMaterialResponse update(
			@PathVariable UUID studyBlockId, @PathVariable UUID id,
			@Valid @RequestBody StudyMaterialRequest request) {
		return StudyMaterialResponse.from(service.update(
				studyBlockId, id, request.title(), request.content()));
	}

	@PatchMapping("/{id}/toggle-chat")
	public StudyMaterialResponse toggleChat(
			@PathVariable UUID studyBlockId, @PathVariable UUID id) {
		return StudyMaterialResponse.from(service.toggleChatInclusion(studyBlockId, id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> archive(@PathVariable UUID studyBlockId, @PathVariable UUID id) {
		service.archive(studyBlockId, id);
		return ResponseEntity.noContent().build();
	}

	public record StudyMaterialRequest(
			@NotBlank @Size(max = 160) String title,
			@NotBlank @Size(max = 100000) String content) {
	}

	public record WebPageMaterialRequest(
			@NotBlank @Size(max = 1000) String url) {
	}

	public record YoutubeMaterialRequest(
			@NotBlank @Size(max = 1000) String url) {
	}

	public record StudyMaterialResponse(
			UUID id,
			UUID studyBlockId,
			String title,
			String content,
			StudyMaterialSourceType sourceType,
			String sourceUrl,
			String externalVideoId,
			String language,
			StudyMaterialProcessingStatus processingStatus,
			String errorMessage,
			String metadataJson,
			boolean enabledForChat,
			String videoChannel,
			String videoThumbnailUrl,
			Integer videoDurationSeconds,
			VideoAnalysisMethod analysisMethod,
			int wordCount,
			int charCount,
			StudyMaterialStatus status,
			String summary,
			String summarySource,
			OffsetDateTime summaryGeneratedAt,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {

		public static StudyMaterialResponse from(StudyMaterial material) {
			String content = material.getContent() == null ? "" : material.getContent();
			int charCount = content.length();
			int wordCount = content.isBlank() ? 0 : content.trim().split("\\s+").length;
			return new StudyMaterialResponse(
					material.getId(),
					material.getStudyBlock().getId(),
					material.getTitle(),
					material.getContent(),
					material.getSourceType(),
					material.getSourceUrl(),
					material.getExternalVideoId(),
					material.getLanguage(),
					material.getProcessingStatus(),
					material.getErrorMessage(),
					material.getMetadataJson(),
					material.isEnabledForChat(),
					material.getVideoChannel(),
					material.getVideoThumbnailUrl(),
					material.getVideoDurationSeconds(),
					material.getAnalysisMethod(),
					wordCount,
					charCount,
					material.getStatus(),
					material.getSummary(),
					material.getSummarySource(),
					material.getSummaryGeneratedAt(),
					material.getCreatedAt(),
					material.getUpdatedAt());
		}
	}
}
