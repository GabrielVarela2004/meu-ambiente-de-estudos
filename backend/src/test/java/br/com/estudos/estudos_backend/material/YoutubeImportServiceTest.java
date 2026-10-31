package br.com.estudos.estudos_backend.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.ai.AiSettingsService;
import br.com.estudos.estudos_backend.ai.AiUsageService;
import br.com.estudos.estudos_backend.ai.GeminiModelProvider;
import br.com.estudos.estudos_backend.ai.GeminiProperties;
import br.com.estudos.estudos_backend.ai.GeminiYoutubeVideoAnalyzer;
import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;

/**
 * Importação de vídeos com o analisador real do Gemini sobre HTTP simulado, oEmbed e transcrição simulados:
 * nenhuma chamada sai para a internet.
 */
class YoutubeImportServiceTest {

	private static final String SHARE_LINK = "https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123";
	private static final String VIDEO_ID = "dQw4w9WgXcQ";
	private static final String ANALYSIS_JSON = """
			{"title":"Streams em Java","durationSeconds":754,
			 "summary":"O vídeo explica a API de Streams.",
			 "topics":["Streams","Operações intermediárias"],
			 "notes":[
			   {"timestamp":"03:10","title":"filter e map","content":"filter seleciona elementos; map transforma cada um.",
			    "code":"lista.stream().filter(x -> x > 2).map(x -> x * 2).toList();"},
			   {"timestamp":"00:15","title":"Introdução","content":"Streams processam coleções de forma declarativa.","code":""}
			 ]}
			""";

	private final Map<UUID, StudyMaterial> stored = new HashMap<>();
	private final StudyMaterialRepository repository = mock(StudyMaterialRepository.class);
	private final StudyBlockService blockService = mock(StudyBlockService.class);
	private final YoutubeOEmbedClient oEmbed = mock(YoutubeOEmbedClient.class);
	private final YoutubeTranscriptExtractor transcripts = mock(YoutubeTranscriptExtractor.class);
	private final AiSettingsService aiSettings = mock(AiSettingsService.class);
	private final AiUsageService aiUsage = mock(AiUsageService.class);
	private final HttpClient geminiHttp = mock(HttpClient.class);
	private final UUID blockId = UUID.randomUUID();
	private YoutubeImportService service;

	@BeforeEach
	void setUp() {
		StudyBlock block = new StudyBlock("Java", "Streams", "Aprender");
		ReflectionTestUtils.setField(block, "id", blockId);
		when(blockService.get(blockId)).thenReturn(block);
		when(repository.save(any(StudyMaterial.class))).thenAnswer(inv -> {
			StudyMaterial material = inv.getArgument(0);
			if (material.getId() == null) {
				ReflectionTestUtils.setField(material, "id", UUID.randomUUID());
			}
			stored.put(material.getId(), material);
			return material;
		});
		when(repository.findById(any(UUID.class))).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.getArgument(0))));
		when(oEmbed.fetch(VIDEO_ID)).thenReturn(new YoutubeOEmbedClient.VideoInfo(VIDEO_ID, "Streams em Java",
				"Canal do Professor", "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"));
		when(transcripts.extract(VIDEO_ID)).thenReturn(new YoutubeTranscriptExtractor.ExtractedYoutubeTranscript(
				VIDEO_ID, "Streams em Java", "pt", "[00:00] Olá, hoje vamos falar de Streams."));
		geminiAvailable(true);
		service = serviceWith(new GeminiYoutubeVideoAnalyzer(
				new GeminiModelProvider(GeminiProperties.withKey("chave-falsa"), geminiHttp), aiSettings, aiUsage));
	}

	private YoutubeImportService serviceWith(YoutubeVideoAnalyzer analyzer) {
		return new YoutubeImportService(repository, blockService, oEmbed, analyzer, transcripts,
				new CurrentUserResolver(), Runnable::run);
	}

	private void geminiAvailable(boolean available) {
		when(aiSettings.unavailabilityReason(any())).thenReturn(
				available ? Optional.empty() : Optional.of("sem chave de API configurada"));
		when(aiSettings.effectiveModel(any())).thenReturn("gemini-3.8-flash");
	}

	@SuppressWarnings("unchecked")
	private void geminiReplies(int status, String modelTextOrErrorBody) throws Exception {
		String body = status == 200
				? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
						+ new ObjectMapper().writeValueAsString(modelTextOrErrorBody) + "}]},\"finishReason\":\"STOP\"}]}"
				: modelTextOrErrorBody;
		HttpResponse<String> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(status);
		when(response.body()).thenReturn(body);
		when(geminiHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
	}

	@Test
	@SuppressWarnings("unchecked")
	void shareLinkIsAnalyzedByGeminiOnceAndBecomesTimestampedStudyNotes() throws Exception {
		geminiReplies(200, ANALYSIS_JSON);

		StudyMaterial material = service.importVideo(blockId, SHARE_LINK);

		assertEquals(StudyMaterialProcessingStatus.COMPLETED, material.getProcessingStatus());
		assertEquals(VideoAnalysisMethod.ANALISE_IA, material.getAnalysisMethod());
		assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", material.getSourceUrl());
		assertEquals(VIDEO_ID, material.getExternalVideoId());
		assertEquals("Canal do Professor", material.getVideoChannel());
		assertEquals(754, material.getVideoDurationSeconds());
		String content = material.getContent();
		assertTrue(content.startsWith("Resumo do vídeo\nO vídeo explica a API de Streams."));
		assertTrue(content.indexOf("[00:15] Introdução") < content.indexOf("[03:10] filter e map"),
				"anotações em ordem de tempo");
		assertTrue(content.contains("lista.stream().filter(x -> x > 2)"), "código mostrado na tela é preservado");
		verify(transcripts, never()).extract(anyString());

		ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
		verify(geminiHttp).send(request.capture(), any(HttpResponse.BodyHandler.class));
		assertTrue(request.getValue().uri().toString().endsWith(":generateContent"));
	}

	@Test
	void geminiQuotaExhaustedFallsBackToTranscript() throws Exception {
		geminiReplies(429, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"Quota exceeded per day\"}}");

		StudyMaterial material = service.importVideo(blockId, SHARE_LINK);

		assertEquals(StudyMaterialProcessingStatus.COMPLETED, material.getProcessingStatus());
		assertEquals(VideoAnalysisMethod.TRANSCRICAO, material.getAnalysisMethod());
		assertEquals("[00:00] Olá, hoje vamos falar de Streams.", material.getContent());
	}

	@Test
	void invalidJsonFromGeminiFallsBackToTranscript() throws Exception {
		geminiReplies(200, "{\"summary\":\"sem anotações\",\"notes\":[{\"timestamp\":\"agora\",\"content\":\"x\"}]}");

		StudyMaterial material = service.importVideo(blockId, SHARE_LINK);

		assertEquals(VideoAnalysisMethod.TRANSCRICAO, material.getAnalysisMethod());
	}

	@Test
	@SuppressWarnings("unchecked")
	void withoutGeminiConfiguredUsesTranscriptWithoutCallingTheApi() throws Exception {
		geminiAvailable(false);

		StudyMaterial material = service.importVideo(blockId, SHARE_LINK);

		assertEquals(VideoAnalysisMethod.TRANSCRICAO, material.getAnalysisMethod());
		verify(geminiHttp, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	void privateVideoFailsWithClearMessageAndCanBeRetried() throws Exception {
		when(oEmbed.fetch(VIDEO_ID)).thenThrow(BusinessRuleException.unprocessable(HttpYoutubeOEmbedClient.UNAVAILABLE_MESSAGE));
		geminiReplies(403, "{\"error\":{\"code\":403,\"message\":\"The video is private\"}}");
		when(transcripts.extract(VIDEO_ID)).thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
				"Este vídeo é privado ou exige login."));

		StudyMaterial failed = service.importVideo(blockId, SHARE_LINK);

		assertEquals(StudyMaterialProcessingStatus.FAILED, failed.getProcessingStatus());
		assertTrue(failed.getErrorMessage().startsWith(YoutubeImportService.NO_CONTENT_MESSAGE));
		assertNull(failed.getAnalysisMethod());

		// O vídeo ficou público: a nova tentativa usa a análise por IA.
		geminiReplies(200, ANALYSIS_JSON);
		StudyMaterial retried = service.reprocess(blockId, failed.getId());
		assertEquals(StudyMaterialProcessingStatus.COMPLETED, retried.getProcessingStatus());
		assertEquals(VideoAnalysisMethod.ANALISE_IA, retried.getAnalysisMethod());
		assertEquals("Streams em Java", retried.getTitle(), "título provisório trocado pelo da análise");
	}

	@Test
	void previewUsesOEmbedAndFlagsDuplicates() {
		when(repository.existsByStudyBlockIdAndExternalVideoIdAndStatus(blockId, VIDEO_ID, StudyMaterialStatus.ACTIVE))
				.thenReturn(true);

		YoutubeImportService.YoutubePreview preview = service.preview(blockId, SHARE_LINK);

		assertEquals("Streams em Java", preview.title());
		assertEquals("Canal do Professor", preview.channel());
		assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ", preview.embedUrl());
		assertTrue(preview.alreadyImported());
		assertTrue(preview.aiAnalysisAvailable());
		assertThrows(BusinessRuleException.class, () -> service.importVideo(blockId, SHARE_LINK));
	}

	@Test
	void archivedDuringProcessingIsNotOverwritten() {
		YoutubeVideoAnalyzer archivingAnalyzer = new YoutubeVideoAnalyzer() {
			@Override public boolean isAvailable() { return true; }
			@Override
			public VideoAnalysis analyze(String videoUrl, String knownTitle) {
				stored.values().forEach(StudyMaterial::archive);
				return new VideoAnalysis("t", "conteúdo", null);
			}
		};

		StudyMaterial material = serviceWith(archivingAnalyzer).importVideo(blockId, SHARE_LINK);

		assertEquals(StudyMaterialStatus.ARCHIVED, material.getStatus());
		assertFalse(material.getProcessingStatus() == StudyMaterialProcessingStatus.COMPLETED);
	}
}
