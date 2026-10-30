package br.com.estudos.estudos_backend.material;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;

/**
 * Rotas de vídeos do YouTube (prévia, importação e nova tentativa) de ponta a ponta, com oEmbed, transcrição e
 * análise por IA simulados. No perfil de teste o processamento é síncrono.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class YoutubeEndpointsIntegrationTest {

	private static final String SHARE_LINK = "https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private YoutubeOEmbedClient oEmbedClient;

	@MockitoBean
	private YoutubeTranscriptExtractor transcriptExtractor;

	@MockitoBean
	private YoutubeVideoAnalyzer videoAnalyzer;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private String userId;
	private String blockId;

	@BeforeEach
	void setUp() throws Exception {
		userId = "youtube-it-" + UUID.randomUUID();
		String block = mockMvc.perform(post("/api/study-blocks").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Bloco de vídeos\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		blockId = objectMapper.readTree(block).get("id").asText();
		when(oEmbedClient.fetch("dQw4w9WgXcQ")).thenReturn(new YoutubeOEmbedClient.VideoInfo("dQw4w9WgXcQ",
				"Aula de Streams", "Canal do Professor", "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"));
		when(transcriptExtractor.extract("dQw4w9WgXcQ")).thenReturn(new YoutubeTranscriptExtractor.ExtractedYoutubeTranscript(
				"dQw4w9WgXcQ", "Aula de Streams", "pt", "[00:00] Streams processam coleções. [00:31] filter e map."));
		when(videoAnalyzer.isAvailable()).thenReturn(false);
	}

	private String materials() {
		return "/api/study-blocks/" + blockId + "/materials";
	}

	@Test
	void previewShowsTitleChannelAndThumbnail() throws Exception {
		mockMvc.perform(get(materials() + "/youtube/preview").param("url", SHARE_LINK)
				.header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.videoId").value("dQw4w9WgXcQ"))
				.andExpect(jsonPath("$.title").value("Aula de Streams"))
				.andExpect(jsonPath("$.channel").value("Canal do Professor"))
				.andExpect(jsonPath("$.embedUrl").value("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))
				.andExpect(jsonPath("$.alreadyImported").value(false))
				.andExpect(jsonPath("$.aiAnalysisAvailable").value(false));
	}

	@Test
	void previewOfPrivateVideoIsUnprocessableWithMessage() throws Exception {
		when(oEmbedClient.fetch("dQw4w9WgXcQ"))
				.thenThrow(BusinessRuleException.unprocessable(HttpYoutubeOEmbedClient.UNAVAILABLE_MESSAGE));

		mockMvc.perform(get(materials() + "/youtube/preview").param("url", SHARE_LINK)
				.header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.message").value(HttpYoutubeOEmbedClient.UNAVAILABLE_MESSAGE));
	}

	@Test
	void importKeepsCustomTitleAndChatChoiceAndFallsBackToTranscript() throws Exception {
		mockMvc.perform(post(materials() + "/youtube").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\"" + SHARE_LINK + "\",\"title\":\"Aula 3 — Streams\",\"enabledForChat\":false}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Aula 3 — Streams"))
				.andExpect(jsonPath("$.enabledForChat").value(false))
				.andExpect(jsonPath("$.sourceType").value("YOUTUBE"))
				.andExpect(jsonPath("$.sourceUrl").value("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
				.andExpect(jsonPath("$.processingStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.analysisMethod").value("TRANSCRICAO"))
				.andExpect(jsonPath("$.videoChannel").value("Canal do Professor"))
				.andExpect(jsonPath("$.content").value("[00:00] Streams processam coleções. [00:31] filter e map."));

		mockMvc.perform(post(materials() + "/youtube").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void failedVideoCanBeReprocessedWithAiAnalysis() throws Exception {
		when(transcriptExtractor.extract(anyString()))
				.thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Sem legendas."));
		String body = mockMvc.perform(post(materials() + "/youtube").header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"" + SHARE_LINK + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.processingStatus").value("FAILED"))
				.andExpect(jsonPath("$.errorMessage").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		String materialId = objectMapper.readTree(body).get("id").asText();

		when(videoAnalyzer.isAvailable()).thenReturn(true);
		when(videoAnalyzer.analyze(anyString(), anyString())).thenReturn(new YoutubeVideoAnalyzer.VideoAnalysis(
				"Aula de Streams", "Resumo do vídeo\nStreams.\n\n[03:10] filter e map\nExplicação.", 600));

		mockMvc.perform(post(materials() + "/" + materialId + "/reprocess").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.processingStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.analysisMethod").value("ANALISE_IA"))
				.andExpect(jsonPath("$.videoDurationSeconds").value(600));
	}

	@Test
	void reprocessIsOnlyForYoutubeSources() throws Exception {
		String body = mockMvc.perform(post(materials()).header(CurrentUserResolver.USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Texto\",\"content\":\"Conteúdo qualquer.\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String materialId = objectMapper.readTree(body).get("id").asText();

		mockMvc.perform(post(materials() + "/" + materialId + "/reprocess").header(CurrentUserResolver.USER_HEADER, userId))
				.andExpect(status().isConflict());
	}
}
