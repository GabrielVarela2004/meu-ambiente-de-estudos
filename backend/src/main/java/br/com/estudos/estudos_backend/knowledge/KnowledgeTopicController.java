package br.com.estudos.estudos_backend.knowledge;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
	private final TopicReviewService reviewService;

	public KnowledgeTopicController(KnowledgeTopicService service, TopicReviewService reviewService) {
		this.service = service;
		this.reviewService = reviewService;
	}

	/** Sugestões aguardando validação (aba Mapeamento de Fontes), da mais para a menos relevante. */
	@GetMapping("/review")
	public ReviewBoardResponse reviewBoard(@PathVariable UUID studyBlockId) {
		TopicReviewService.ReviewBoard board = reviewService.board(studyBlockId);
		return new ReviewBoardResponse(board.items().stream().map(ReviewItemResponse::from).toList(), board.summary());
	}

	/** Aprovar, excluir ou comentar (e opcionalmente renomear) uma sugestão. */
	@PostMapping("/review/{topicId}")
	public ReviewItemResponse review(@PathVariable UUID studyBlockId, @PathVariable UUID topicId,
			@Valid @RequestBody ReviewRequest request) {
		KnowledgeTopic topic = reviewService.review(studyBlockId, topicId, request.action(), request.feedback(),
				request.title());
		return ReviewItemResponse.from(new TopicReviewService.ReviewItem(topic, null, null, null));
	}

	@PostMapping("/review/approve-relevant")
	public BulkResponse approveRelevant(@PathVariable UUID studyBlockId, @Valid @RequestBody BulkApproveRequest request) {
		return new BulkResponse(reviewService.approveRelevant(studyBlockId, request.minRelevance()));
	}

	/** Revisa com IA os tópicos com comentário e devolve-os numa nova rodada. */
	@PostMapping("/review/refine")
	public BulkResponse refine(@PathVariable UUID studyBlockId) {
		return new BulkResponse(reviewService.refine(studyBlockId));
	}

	/** Oficializa os aprovados e arquiva os excluídos. */
	@PostMapping("/review/publish")
	public TopicReviewService.PublishResult publish(@PathVariable UUID studyBlockId) {
		return reviewService.publish(studyBlockId);
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

	@PostMapping("/extract")
	public List<TopicResponse> extractFromSources(@PathVariable UUID studyBlockId) {
		return service.extractFromSources(studyBlockId, true).stream().map(TopicResponse::from).toList();
	}

	@PostMapping("/{topicId}/validate")
	public TopicResponse validateTopic(@PathVariable UUID studyBlockId, @PathVariable UUID topicId) {
		return TopicResponse.from(service.validateTopic(studyBlockId, topicId));
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

	public record ReviewRequest(
			@NotNull TopicReviewState action,
			@Size(max = 1000) String feedback,
			@Size(max = 160) String title) {}

	public record BulkApproveRequest(@Min(0) @Max(100) int minRelevance) {}

	public record BulkResponse(int count) {}

	public record ReviewBoardResponse(List<ReviewItemResponse> items, TopicReviewService.ReviewSummary summary) {}

	public record ReviewItemResponse(
			UUID id,
			UUID parentTopicId,
			String officialParentTitle,
			String title,
			String previousTitle,
			String description,
			String supportingExcerpt,
			UUID sourceMaterialId,
			String sourceMaterialTitle,
			String sourceVideoId,
			Integer relevanceScore,
			String relevanceReason,
			int reviewRound,
			TopicReviewState reviewState,
			String userFeedback) {

		static ReviewItemResponse from(TopicReviewService.ReviewItem item) {
			KnowledgeTopic topic = item.topic();
			return new ReviewItemResponse(topic.getId(), topic.getParentTopicId(), item.officialParentTitle(),
					topic.getTitle(), topic.getPreviousTitle(), topic.getDescription(), topic.getSupportingExcerpt(),
					topic.getSourceMaterialId(), item.sourceMaterialTitle(), item.sourceVideoId(),
					topic.getRelevanceScore(), topic.getRelevanceReason(), topic.getReviewRound(), topic.getReviewState(),
					topic.getUserFeedback());
		}
	}

	public record TopicResponse(
			UUID id,
			UUID studyBlockId,
			UUID parentTopicId,
			UUID prerequisiteTopicId,
			String prerequisiteWarning,
			UUID sourceMaterialId,
			String sourceMaterialTitle,
			String supportingExcerpt,
			KnowledgeTopicOrigin origin,
			boolean userValidated,
			OffsetDateTime extractedAt,
			String title,
			String description,
			KnowledgeTopicStatus status,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt,
			List<UUID> questionIds,
			TopicLearningState learningState,
			String stateReason,
			int attempts,
			Integer recentAccuracyPercent,
			Integer masteryPercent,
			Integer theoryPercent,
			Integer practicalPercent,
			int practicalExercises,
			boolean mastered,
			String masteryReason) {

		static TopicResponse from(KnowledgeTopicService.TopicView view) {
			KnowledgeTopic topic = view.topic();
			TopicLearningAssessment assessment = view.assessment();
			// Visões sem cálculo de domínio (criação, validação) partem só da teoria.
			TopicMasteryCalculator.TopicMastery mastery = view.mastery() != null
					? view.mastery()
					: new TopicMasteryCalculator().calculate(assessment, List.of());
			return new TopicResponse(
					topic.getId(),
					topic.getStudyBlock().getId(),
					topic.getParentTopicId(),
					topic.getPrerequisiteTopicId(),
					view.prerequisiteWarning(),
					topic.getSourceMaterialId(),
					view.sourceMaterialTitle(),
					topic.getSupportingExcerpt(),
					topic.getOrigin() == null ? KnowledgeTopicOrigin.MANUAL : topic.getOrigin(),
					topic.isUserValidated(),
					topic.getExtractedAt(),
					topic.getTitle(),
					topic.getDescription(),
					topic.getStatus(),
					topic.getCreatedAt(),
					topic.getUpdatedAt(),
					view.questionIds(),
					assessment.state(),
					assessment.reason(),
					assessment.attempts(),
					assessment.recentAccuracyPercent(),
					mastery.combinedPercent(),
					mastery.theoryPercent(),
					mastery.practicalPercent(),
					mastery.practicalExercises(),
					mastery.mastered(),
					mastery.reason());
		}
	}
}
