package br.com.estudos.estudos_backend.knowledge;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-blocks/{studyBlockId}/topics")
public class KnowledgeTopicController {

	private final KnowledgeTopicService service;

	public KnowledgeTopicController(KnowledgeTopicService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<TopicResponse> create(@PathVariable UUID studyBlockId,
			@Valid @RequestBody TopicRequest request) {
		KnowledgeTopic topic = service.create(
				studyBlockId, request.title(), request.description(), request.parentTopicId(), request.prerequisiteTopicId());
		TopicLearningAssessment noEvidence = new TopicLearningStateEvaluator().evaluate(List.of(), 0);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(TopicResponse.from(new KnowledgeTopicService.TopicView(topic, List.of(), noEvidence, null)));
	}

	@GetMapping
	public List<TopicResponse> list(@PathVariable UUID studyBlockId,
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.list(studyBlockId, includeArchived).stream().map(TopicResponse::from).toList();
	}

	@PutMapping("/{topicId}")
	public ResponseEntity<Void> update(@PathVariable UUID studyBlockId, @PathVariable UUID topicId,
			@Valid @RequestBody TopicUpdateRequest request) {
		service.update(studyBlockId, topicId, request.title(), request.description());
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{topicId}/prerequisite")
	public ResponseEntity<Void> updatePrerequisite(@PathVariable UUID studyBlockId, @PathVariable UUID topicId,
			@RequestBody PrerequisiteRequest request) {
		service.updatePrerequisite(studyBlockId, topicId, request == null ? null : request.prerequisiteTopicId());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{topicId}")
	public ResponseEntity<Void> archive(@PathVariable UUID studyBlockId, @PathVariable UUID topicId) {
		service.archive(studyBlockId, topicId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{topicId}/questions/{questionId}")
	public ResponseEntity<Void> linkQuestion(@PathVariable UUID studyBlockId, @PathVariable UUID topicId,
			@PathVariable UUID questionId) {
		service.linkQuestion(studyBlockId, topicId, questionId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{topicId}/questions/{questionId}")
	public ResponseEntity<Void> unlinkQuestion(@PathVariable UUID studyBlockId, @PathVariable UUID topicId,
			@PathVariable UUID questionId) {
		service.unlinkQuestion(studyBlockId, topicId, questionId);
		return ResponseEntity.noContent().build();
	}

	public record TopicRequest(
			@NotBlank @Size(max = 160) String title,
			@Size(max = 2000) String description,
			UUID parentTopicId,
			UUID prerequisiteTopicId) {}

	public record TopicUpdateRequest(
			@NotBlank @Size(max = 160) String title,
			@Size(max = 2000) String description) {}

	public record PrerequisiteRequest(UUID prerequisiteTopicId) {}

	public record TopicResponse(
			UUID id,
			UUID studyBlockId,
			UUID parentTopicId,
			UUID prerequisiteTopicId,
			String prerequisiteWarning,
			String title,
			String description,
			KnowledgeTopicStatus status,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt,
			List<UUID> questionIds,
			TopicLearningState learningState,
			String stateReason,
			int attempts,
			Integer recentAccuracyPercent) {

		static TopicResponse from(KnowledgeTopicService.TopicView view) {
			KnowledgeTopic topic = view.topic();
			TopicLearningAssessment assessment = view.assessment();
			return new TopicResponse(
					topic.getId(),
					topic.getStudyBlock().getId(),
					topic.getParentTopicId(),
					topic.getPrerequisiteTopicId(),
					view.prerequisiteWarning(),
					topic.getTitle(),
					topic.getDescription(),
					topic.getStatus(),
					topic.getCreatedAt(),
					topic.getUpdatedAt(),
					view.questionIds(),
					assessment.state(),
					assessment.reason(),
					assessment.attempts(),
					assessment.recentAccuracyPercent());
		}
	}
}
