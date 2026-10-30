package br.com.estudos.estudos_backend.material;

import java.util.UUID;
import java.util.concurrent.Executor;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.common.exception.NotFoundException;
import br.com.estudos.estudos_backend.config.YoutubeProcessingConfiguration;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicService;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;
import br.com.estudos.estudos_backend.studyblock.StudyBlockStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Vídeos do YouTube como fonte: prévia (oEmbed), importação assíncrona e nova tentativa.
 * O processamento tenta primeiro a análise por IA do vídeo (uma única vez) e, se ela não estiver disponível
 * ou falhar, usa a transcrição. Nada aqui segura transação durante chamadas externas.
 */
@Service
public class YoutubeImportService {

	private static final Logger log = LoggerFactory.getLogger(YoutubeImportService.class);
	private static final String PLACEHOLDER_TITLE_PREFIX = "Vídeo do YouTube (";
	static final String NO_CONTENT_MESSAGE = "Não foi possível obter o conteúdo deste vídeo: a análise por IA não "
			+ "estava disponível ou falhou, e o vídeo não tem legendas. Só vídeos públicos podem ser analisados.";

	private final StudyMaterialRepository repository;
	private final StudyBlockService blockService;
	private final YoutubeOEmbedClient oEmbedClient;
	private final YoutubeVideoAnalyzer videoAnalyzer;
	private final YoutubeTranscriptExtractor transcriptExtractor;
	private final KnowledgeTopicService knowledgeTopicService;
	private final CurrentUserResolver currentUserResolver;
	private final Executor executor;

	public YoutubeImportService(StudyMaterialRepository repository, StudyBlockService blockService,
			YoutubeOEmbedClient oEmbedClient, YoutubeVideoAnalyzer videoAnalyzer,
			YoutubeTranscriptExtractor transcriptExtractor, KnowledgeTopicService knowledgeTopicService,
			CurrentUserResolver currentUserResolver,
			@Qualifier(YoutubeProcessingConfiguration.EXECUTOR) Executor executor) {
		this.repository = repository;
		this.blockService = blockService;
		this.oEmbedClient = oEmbedClient;
		this.videoAnalyzer = videoAnalyzer;
		this.transcriptExtractor = transcriptExtractor;
		this.knowledgeTopicService = knowledgeTopicService;
		this.currentUserResolver = currentUserResolver;
		this.executor = executor;
	}

	/** Dados exibidos antes de salvar (miniatura, título e canal). */
	public YoutubePreview preview(UUID studyBlockId, String rawUrl) {
		blockService.get(studyBlockId);
		String videoId = YoutubeUrlParser.extractVideoId(rawUrl);
		YoutubeOEmbedClient.VideoInfo info = oEmbedClient.fetch(videoId);
		boolean alreadyImported = repository.existsByStudyBlockIdAndExternalVideoIdAndStatus(
				studyBlockId, videoId, StudyMaterialStatus.ACTIVE);
		return new YoutubePreview(videoId, YoutubeUrlParser.canonicalUrl(videoId), embedUrl(videoId), info.title(),
				info.channel(), info.thumbnailUrl(), alreadyImported, videoAnalyzer.isAvailable());
	}

	/**
	 * Salva a fonte como "processando" e agenda a análise. A resposta volta antes do fim do processamento
	 * (no perfil de teste o executor é síncrono e a fonte já volta concluída).
	 */
	public StudyMaterial importVideo(UUID studyBlockId, String rawUrl) {
		return importVideo(studyBlockId, rawUrl, null, true);
	}

	/**
	 * Com título personalizado, ele é mantido (a análise não o troca); {@code enabledForChat} vale desde a criação,
	 * sem edição posterior que poderia sobrescrever o conteúdo gerado em segundo plano.
	 */
	public StudyMaterial importVideo(UUID studyBlockId, String rawUrl, String customTitle, boolean enabledForChat) {
		StudyBlock block = activeBlock(studyBlockId);
		String videoId = YoutubeUrlParser.extractVideoId(rawUrl);
		if (repository.existsByStudyBlockIdAndExternalVideoIdAndStatus(studyBlockId, videoId, StudyMaterialStatus.ACTIVE)) {
			throw new BusinessRuleException("Este vídeo do YouTube já foi importado neste bloco de estudo.");
		}
		YoutubeOEmbedClient.VideoInfo info = metadataOrNull(videoId);
		String title = customTitle != null && !customTitle.isBlank() ? customTitle.strip()
				: info != null && info.title() != null ? info.title() : PLACEHOLDER_TITLE_PREFIX + videoId + ")";
		StudyMaterial material = new StudyMaterial(block, title.length() <= 160 ? title : title.substring(0, 160), "",
				StudyMaterialSourceType.YOUTUBE, YoutubeUrlParser.canonicalUrl(videoId), videoId, null,
				StudyMaterialProcessingStatus.PROCESSING, null, null, enabledForChat);
		if (info != null) {
			material.applyVideoMetadata(info.channel(), info.thumbnailUrl());
		}
		StudyMaterial saved = repository.save(material);
		blockService.touch(block);
		schedule(saved.getId());
		return repository.findById(saved.getId()).orElse(saved);
	}

	/** Nova tentativa (ex.: depois de erro ou quando a cota do Gemini voltar). */
	public StudyMaterial reprocess(UUID studyBlockId, UUID materialId) {
		activeBlock(studyBlockId);
		StudyMaterial material = repository.findById(materialId)
				.filter(m -> m.getStudyBlock().getId().equals(studyBlockId))
				.filter(m -> m.getStatus() == StudyMaterialStatus.ACTIVE)
				.orElseThrow(() -> new NotFoundException("Fonte não encontrada."));
		if (material.getSourceType() != StudyMaterialSourceType.YOUTUBE) {
			throw new BusinessRuleException("Só fontes do YouTube podem ser processadas novamente.");
		}
		if (material.getProcessingStatus() == StudyMaterialProcessingStatus.PROCESSING) {
			throw new BusinessRuleException("Este vídeo já está sendo processado.");
		}
		material.startProcessing();
		repository.save(material);
		schedule(materialId);
		return repository.findById(materialId).orElse(material);
	}

	private void schedule(UUID materialId) {
		String userId = currentUserResolver.currentUserId();
		executor.execute(() -> {
			currentUserResolver.setOverrideUser(userId);
			try {
				process(materialId);
			} finally {
				currentUserResolver.setOverrideUser(null);
			}
		});
	}

	/** Análise por IA (uma vez) → senão transcrição → senão falha com mensagem. */
	void process(UUID materialId) {
		StudyMaterial material = repository.findById(materialId).orElse(null);
		if (material == null || material.getStatus() != StudyMaterialStatus.ACTIVE) {
			return;
		}
		String videoId = material.getExternalVideoId();
		String videoUrl = YoutubeUrlParser.canonicalUrl(videoId);
		Outcome outcome = null;

		if (videoAnalyzer.isAvailable()) {
			try {
				YoutubeVideoAnalyzer.VideoAnalysis analysis = videoAnalyzer.analyze(videoUrl, material.getTitle());
				outcome = Outcome.completed(analysis.title(), analysis.content(), null, VideoAnalysisMethod.ANALISE_IA,
						analysis.durationSeconds());
			} catch (RuntimeException e) {
				log.info("Análise por IA do vídeo {} indisponível ({}); usando a transcrição.", videoId, describe(e));
			}
		}
		if (outcome == null) {
			outcome = fromTranscript(videoId);
		}
		apply(materialId, outcome);
	}

	private Outcome fromTranscript(String videoId) {
		try {
			YoutubeTranscriptExtractor.ExtractedYoutubeTranscript transcript = transcriptExtractor.extract(videoId);
			if (transcript == null || transcript.transcriptText() == null || transcript.transcriptText().isBlank()) {
				return Outcome.failed(NO_CONTENT_MESSAGE);
			}
			return Outcome.completed(transcript.title(), transcript.transcriptText().strip(), transcript.language(),
					VideoAnalysisMethod.TRANSCRICAO, null);
		} catch (ResponseStatusException e) {
			return Outcome.failed(NO_CONTENT_MESSAGE + (e.getReason() == null ? "" : " Detalhe: " + e.getReason()));
		} catch (RuntimeException e) {
			log.warn("Falha ao obter a transcrição do vídeo {}", videoId, e);
			return Outcome.failed(NO_CONTENT_MESSAGE);
		}
	}

	/** Relê a fonte antes de gravar: se foi arquivada durante o processamento, nada é sobrescrito. */
	private void apply(UUID materialId, Outcome outcome) {
		StudyMaterial fresh = repository.findById(materialId).orElse(null);
		if (fresh == null || fresh.getStatus() != StudyMaterialStatus.ACTIVE) {
			return;
		}
		if (outcome.failureMessage() != null) {
			fresh.failProcessing(outcome.failureMessage());
			repository.save(fresh);
			return;
		}
		String title = fresh.getTitle().startsWith(PLACEHOLDER_TITLE_PREFIX) ? outcome.title() : null;
		fresh.completeVideoProcessing(title, outcome.content(), outcome.language(), outcome.method(),
				outcome.durationSeconds());
		repository.save(fresh);
		try {
			knowledgeTopicService.extractFromSources(fresh.getStudyBlock().getId(), false);
		} catch (RuntimeException e) {
			log.warn("Vídeo {} importado, mas a atualização dos tópicos falhou: {}", materialId, describe(e));
		}
	}

	private YoutubeOEmbedClient.VideoInfo metadataOrNull(String videoId) {
		try {
			return oEmbedClient.fetch(videoId);
		} catch (RuntimeException e) {
			// Sem metadados a importação continua: o título vem da análise ou da transcrição.
			log.info("oEmbed indisponível para {}: {}", videoId, describe(e));
			return null;
		}
	}

	private StudyBlock activeBlock(UUID studyBlockId) {
		StudyBlock block = blockService.get(studyBlockId);
		if (block.getStatus() == StudyBlockStatus.ARCHIVED) {
			throw new BusinessRuleException("Blocos de estudo arquivados não podem receber fontes.");
		}
		return block;
	}

	static String embedUrl(String videoId) {
		return "https://www.youtube-nocookie.com/embed/" + videoId;
	}

	private static String describe(RuntimeException e) {
		return e instanceof ResponseStatusException rse && rse.getReason() != null ? rse.getReason() : e.getMessage();
	}

	public record YoutubePreview(String videoId, String canonicalUrl, String embedUrl, String title, String channel,
			String thumbnailUrl, boolean alreadyImported, boolean aiAnalysisAvailable) {}

	private record Outcome(String title, String content, String language, VideoAnalysisMethod method,
			Integer durationSeconds, String failureMessage) {
		static Outcome completed(String title, String content, String language, VideoAnalysisMethod method,
				Integer durationSeconds) {
			return new Outcome(title, content, language, method, durationSeconds, null);
		}

		static Outcome failed(String message) {
			return new Outcome(null, null, null, null, null, message);
		}
	}
}
