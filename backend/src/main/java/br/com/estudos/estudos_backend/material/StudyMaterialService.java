package br.com.estudos.estudos_backend.material;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.common.exception.InvalidInputException;
import br.com.estudos.estudos_backend.common.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.knowledge.KnowledgeTopicService;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockStatus;
import br.com.estudos.estudos_backend.studyblock.StudyBlockService;

@Service
public class StudyMaterialService {

	private final StudyMaterialRepository repository;
	private final StudyBlockService studyBlockService;
	private final MaterialFileStorage fileStorage;
	private final YoutubeTranscriptExtractor youtubeTranscriptExtractor;
	private final KnowledgeTopicService knowledgeTopicService;
	private final WebPageExtractor webPageExtractor;
	private final MaterialSummarizer summarizer;

	public StudyMaterialService(
			StudyMaterialRepository repository,
			StudyBlockService studyBlockService,
			MaterialFileStorage fileStorage,
			YoutubeTranscriptExtractor youtubeTranscriptExtractor) {
		this(repository, studyBlockService, fileStorage, youtubeTranscriptExtractor, null, null, null);
	}

	public StudyMaterialService(
			StudyMaterialRepository repository,
			StudyBlockService studyBlockService,
			MaterialFileStorage fileStorage,
			YoutubeTranscriptExtractor youtubeTranscriptExtractor,
			KnowledgeTopicService knowledgeTopicService) {
		this(repository, studyBlockService, fileStorage, youtubeTranscriptExtractor, knowledgeTopicService, null, null);
	}

	@Autowired
	public StudyMaterialService(
			StudyMaterialRepository repository,
			StudyBlockService studyBlockService,
			MaterialFileStorage fileStorage,
			YoutubeTranscriptExtractor youtubeTranscriptExtractor,
			KnowledgeTopicService knowledgeTopicService,
			WebPageExtractor webPageExtractor,
			MaterialSummarizer summarizer) {
		this.repository = repository;
		this.studyBlockService = studyBlockService;
		this.fileStorage = fileStorage;
		this.youtubeTranscriptExtractor = youtubeTranscriptExtractor;
		this.knowledgeTopicService = knowledgeTopicService;
		this.webPageExtractor = webPageExtractor;
		this.summarizer = summarizer;
	}

	@Transactional
	public StudyMaterial importTextFile(UUID studyBlockId, MultipartFile file) {
		StudyBlock block = getActiveBlock(studyBlockId);
		String content = fileStorage.readAndStore(file);
		String filename = file.getOriginalFilename() == null ? "Material importado" : file.getOriginalFilename();
		StudyMaterialSourceType sourceType = fileStorage.sourceTypeFor(filename);
		StudyMaterial saved = repository.save(new StudyMaterial(
				block,
				filename,
				content,
				sourceType,
				null,
				null,
				null,
				StudyMaterialProcessingStatus.COMPLETED,
				null));
		studyBlockService.touch(block);
		syncTopicsIfAvailable(studyBlockId);
		return saved;
	}

	@Transactional
	public StudyMaterial importYoutubeVideo(UUID studyBlockId, String rawUrl) {
		StudyBlock block = getActiveBlock(studyBlockId);
		String videoId = YoutubeUrlParser.extractVideoId(rawUrl);
		if (repository.existsByStudyBlockIdAndExternalVideoIdAndStatus(
				studyBlockId, videoId, StudyMaterialStatus.ACTIVE)) {
			throw new BusinessRuleException("Este vídeo do YouTube já foi importado neste bloco de estudo.");
		}
		YoutubeTranscriptExtractor.ExtractedYoutubeTranscript extracted = youtubeTranscriptExtractor.extract(videoId);
		if (extracted == null || extracted.transcriptText() == null || extracted.transcriptText().isBlank()) {
			throw new ResponseStatusException(
					HttpStatus.valueOf(422),
					"Este vídeo não possui legendas ou transcrição disponíveis para extração.");
		}
		String title = (extracted.title() == null || extracted.title().isBlank())
				? "Vídeo YouTube (" + videoId + ")"
				: extracted.title().trim();
		if (title.length() > 160) {
			title = title.substring(0, 160);
		}
		StudyMaterial saved = repository.save(new StudyMaterial(
				block,
				title,
				extracted.transcriptText().trim(),
				StudyMaterialSourceType.YOUTUBE,
				YoutubeUrlParser.canonicalUrl(videoId),
				videoId,
				extracted.language(),
				StudyMaterialProcessingStatus.COMPLETED,
				null));
		studyBlockService.touch(block);
		syncTopicsIfAvailable(studyBlockId);
		return saved;
	}

	/**
	 * Importa uma página web pública como fonte. Não é transacional durante o download
	 * (rede lenta não deve segurar conexão com o banco); a gravação em si é atômica.
	 */
	public StudyMaterial importWebPage(UUID studyBlockId, String rawUrl) {
		if (webPageExtractor == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Importação de páginas web indisponível.");
		}
		StudyBlock block = getActiveBlock(studyBlockId);
		WebPageExtractor.ExtractedWebPage page = webPageExtractor.extract(rawUrl);
		String url = page.finalUrl().length() > 1000 ? page.finalUrl().substring(0, 1000) : page.finalUrl();
		if (repository.existsByStudyBlockIdAndSourceUrlAndStatus(studyBlockId, url, StudyMaterialStatus.ACTIVE)) {
			throw new BusinessRuleException("Esta página já foi importada neste bloco de estudo.");
		}
		String title = page.title() == null || page.title().isBlank() ? url : page.title().strip();
		if (title.length() > 160) {
			title = title.substring(0, 160);
		}
		StudyMaterial saved = repository.save(new StudyMaterial(
				block,
				title,
				fileStorage.sanitizeExtractedText(page.text()),
				StudyMaterialSourceType.WEB,
				url,
				null,
				null,
				StudyMaterialProcessingStatus.COMPLETED,
				null));
		studyBlockService.touch(block);
		syncTopicsIfAvailable(studyBlockId);
		return saved;
	}

	/** Gera (ou regenera) o resumo do material — sem transação durante a chamada à IA. */
	public StudyMaterial generateSummary(UUID studyBlockId, UUID id) {
		if (summarizer == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Geração de resumos indisponível.");
		}
		StudyMaterial material = get(id);
		validateOwnership(material, studyBlockId);
		if (material.getStatus() == StudyMaterialStatus.ARCHIVED) {
			throw new BusinessRuleException("Fontes arquivadas não podem ser resumidas.");
		}
		MaterialSummarizer.Summary summary = summarizer.summarize(material.getTitle(), material.getContent());
		material.applySummary(summary.text(), summary.source());
		return repository.save(material);
	}

	@Transactional
	public StudyMaterial create(UUID studyBlockId, String title, String content) {
		StudyBlock block = getActiveBlock(studyBlockId);
		StudyMaterial saved = repository.save(new StudyMaterial(block, title, content));
		studyBlockService.touch(block);
		syncTopicsIfAvailable(studyBlockId);
		return saved;
	}

	@Transactional(readOnly = true)
	public List<StudyMaterial> list(UUID studyBlockId, boolean includeArchived) {
		studyBlockService.get(studyBlockId);
		if (includeArchived) {
			return repository.findAllByStudyBlockIdOrderByUpdatedAtDesc(studyBlockId);
		}
		return repository.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(
				studyBlockId, StudyMaterialStatus.ACTIVE);
	}

	@Transactional(readOnly = true)
	public List<StudyMaterial> getActiveMaterialsForBlock(UUID studyBlockId) {
		studyBlockService.get(studyBlockId);
		return repository.findAllByStudyBlockIdAndStatusOrderByUpdatedAtDesc(
				studyBlockId, StudyMaterialStatus.ACTIVE);
	}

	@Transactional(readOnly = true)
	public List<StudyMaterial> getActiveMaterialsForBlocks(Collection<UUID> studyBlockIds) {
		if (studyBlockIds == null || studyBlockIds.isEmpty()) {
			return List.of();
		}
		return repository.findAllByStudyBlockIdInAndStatusOrderByUpdatedAtDesc(
				studyBlockIds, StudyMaterialStatus.ACTIVE);
	}

	@Transactional(readOnly = true)
	public StudyMaterial getActiveMaterialInBlock(UUID studyBlockId, UUID materialId) {
		StudyMaterial material = get(materialId);
		validateOwnership(material, studyBlockId);
		if (material.getStatus() != StudyMaterialStatus.ACTIVE) {
			throw new InvalidInputException("A fonte está arquivada.");
		}
		return material;
	}

	@Transactional(readOnly = true)
	public StudyMaterial get(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new NotFoundException("Fonte não encontrada."));
	}

	@Transactional
	public StudyMaterial update(UUID studyBlockId, UUID id, String title, String content) {
		StudyMaterial material = get(id);
		validateOwnership(material, studyBlockId);
		if (material.getStatus() == StudyMaterialStatus.ARCHIVED) {
			throw new BusinessRuleException("Fontes arquivadas não podem ser editadas.");
		}
		material.update(title, content);
		StudyMaterial saved = repository.save(material);
		studyBlockService.touch(material.getStudyBlock());
		syncTopicsIfAvailable(studyBlockId);
		return saved;
	}

	@Transactional
	public StudyMaterial toggleChatInclusion(UUID studyBlockId, UUID id) {
		StudyMaterial material = get(id);
		validateOwnership(material, studyBlockId);
		if (material.getStatus() == StudyMaterialStatus.ARCHIVED) {
			throw new BusinessRuleException("Fontes arquivadas não podem ser modificadas.");
		}
		material.setEnabledForChat(!material.isEnabledForChat());
		StudyMaterial saved = repository.save(material);
		studyBlockService.touch(material.getStudyBlock());
		return saved;
	}

	@Transactional
	public void archive(UUID studyBlockId, UUID id) {
		StudyMaterial material = get(id);
		validateOwnership(material, studyBlockId);
		material.archive();
		repository.save(material);
		studyBlockService.touch(material.getStudyBlock());
		syncTopicsIfAvailable(studyBlockId);
	}

	private void syncTopicsIfAvailable(UUID studyBlockId) {
		if (knowledgeTopicService != null) {
			knowledgeTopicService.extractFromSources(studyBlockId, false);
		}
	}

	private StudyBlock getActiveBlock(UUID id) {
		StudyBlock block = studyBlockService.get(id);
		if (block.getStatus() == StudyBlockStatus.ARCHIVED) {
			throw new BusinessRuleException("Blocos de estudo arquivados não podem receber fontes.");
		}
		return block;
	}

	private void validateOwnership(StudyMaterial material, UUID studyBlockId) {
		if (!material.getStudyBlock().getId().equals(studyBlockId)) {
			throw new NotFoundException("Fonte não encontrada.");
		}
	}
}
