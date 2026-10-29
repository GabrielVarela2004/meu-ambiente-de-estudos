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
		return service.ask(request.message(), request.studyBlockId());
	}

	@GetMapping("/api/chat/status")
	public ProviderStatusResponse status() {
		return new ProviderStatusResponse(provider.providerName(), provider.isConfigured());
	}

	@GetMapping("/api/study-blocks/{studyBlockId}/chat/messages")
	public List<ChatHistoryItemResponse> listBlockHistory(@PathVariable UUID studyBlockId) {
		return service.listHistory(studyBlockId).stream()
				.map(ChatHistoryItemResponse::from)
				.toList();
	}

	public record ChatRequest(@NotBlank @Size(max = 10000) String message, UUID studyBlockId) {}

	public record ProviderStatusResponse(String providerName, boolean configured) {}

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
