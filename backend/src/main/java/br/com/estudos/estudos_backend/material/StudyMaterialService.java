package br.com.estudos.estudos_backend.material;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

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

	public StudyMaterialService(
			StudyMaterialRepository repository,
			StudyBlockService studyBlockService,
			MaterialFileStorage fileStorage,
			YoutubeTranscriptExtractor youtubeTranscriptExtractor) {
		this(repository, studyBlockService, fileStorage, youtubeTranscriptExtractor, null);
	}

	@Autowired
	public StudyMaterialService(
			StudyMaterialRepository repository,
			StudyBlockService studyBlockService,
			MaterialFileStorage fileStorage,
			YoutubeTranscriptExtractor youtubeTranscriptExtractor,
			KnowledgeTopicService knowledgeTopicService) {
		this.repository = repository;
		this.studyBlockService = studyBlockService;
		this.fileStorage = fileStorage;
		this.youtubeTranscriptExtractor = youtubeTranscriptExtractor;
		this.knowledgeTopicService = knowledgeTopicService;
	}

	@Transactional
	public StudyMaterial importTextFile(UUID studyBlockId, MultipartFile file) {
		StudyBlock block = getActiveBlock(studyBlockId);
		String content = fileStorage.readAndStore(file);
		String filename = file.getOriginalFilename() == null ? "Material importado" : file.getOriginalFilename();
		StudyMaterialSourceType sourceType = detectFileSourceType(filename);
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
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"Este vídeo do YouTube já foi importado neste bloco de estudo.");
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
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Study material is archived");
		}
		return material;
	}

	@Transactional(readOnly = true)
	public StudyMaterial get(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study material not found"));
	}

	@Transactional
	public StudyMaterial update(UUID studyBlockId, UUID id, String title, String content) {
		StudyMaterial material = get(id);
		validateOwnership(material, studyBlockId);
		if (material.getStatus() == StudyMaterialStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived study materials cannot be edited");
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
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived study materials cannot be modified");
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

	private StudyMaterialSourceType detectFileSourceType(String filename) {
		String lower = filename.toLowerCase();
		if (lower.endsWith(".pdf")) {
			return StudyMaterialSourceType.FILE_PDF;
		}
		if (lower.endsWith(".docx")) {
			return StudyMaterialSourceType.FILE_DOCX;
		}
		if (lower.endsWith(".md")) {
			return StudyMaterialSourceType.FILE_MD;
		}
		return StudyMaterialSourceType.FILE_TXT;
	}

	private StudyBlock getActiveBlock(UUID id) {
		StudyBlock block = studyBlockService.get(id);
		if (block.getStatus() == StudyBlockStatus.ARCHIVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived study blocks cannot receive materials");
		}
		return block;
	}

	private void validateOwnership(StudyMaterial material, UUID studyBlockId) {
		if (!material.getStudyBlock().getId().equals(studyBlockId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study material not found");
		}
	}
}
