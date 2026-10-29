package br.com.estudos.estudos_backend.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {
	private final OpenAiChatService service;
	private final AiTutorProvider provider;

	public ChatController(OpenAiChatService service, AiTutorProvider provider) {
		this.service = service;
		this.provider = provider;
	}

	@PostMapping("/api/chat")
	public OpenAiChatService.ChatResponse ask(@Valid @RequestBody ChatRequest request) {
		return service.ask(request.message(), request.studyBlockId(), request.studyBlockIds(), request.sourceIds());
	}

	@GetMapping("/api/chat/status")
	public ProviderStatusResponse status() {
		return new ProviderStatusResponse(
				provider.providerName(),
				provider.isConfigured(),
				provider.configuredModel());
	}

	@GetMapping("/api/study-blocks/{studyBlockId}/chat/messages")
	public List<ChatHistoryItemResponse> listBlockHistory(@PathVariable UUID studyBlockId) {
		return service.listHistory(studyBlockId).stream()
				.map(ChatHistoryItemResponse::from)
				.toList();
	}

	@GetMapping("/api/chat/universal/messages")
	public List<ChatHistoryItemResponse> listUniversalHistory() {
		return service.listUniversalHistory().stream()
				.map(ChatHistoryItemResponse::from)
				.toList();
	}

	@GetMapping("/api/study-blocks/{studyBlockId}/chat/suggested-questions")
	public List<OpenAiChatService.SuggestedQuestion> suggestQuestions(@PathVariable UUID studyBlockId) {
		return service.suggestQuestions(studyBlockId);
	}

	public record ChatRequest(
			@NotBlank @Size(max = 10000) String message,
			UUID studyBlockId,
			List<UUID> studyBlockIds,
			List<UUID> sourceIds) {
	}

	public record ProviderStatusResponse(String providerName, boolean configured, String model) {
		public ProviderStatusResponse(String providerName, boolean configured) {
			this(providerName, configured, "gpt-4o-mini");
		}
	}

	public record ChatHistoryItemResponse(
			UUID id,
			UUID studyBlockId,
			String userMessage,
			String assistantAnswer,
			String providerName,
			String modelName,
			String referencedMaterials,
			OffsetDateTime createdAt) {

		static ChatHistoryItemResponse from(ChatConversationEntry entry) {
			return new ChatHistoryItemResponse(
					entry.getId(),
					entry.getStudyBlockId(),
					entry.getUserMessage(),
					entry.getAssistantAnswer(),
					entry.getProviderName(),
					entry.getModelName(),
					entry.getReferencedMaterials(),
					entry.getCreatedAt());
		}
	}
}
