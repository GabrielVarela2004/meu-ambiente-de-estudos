package br.com.estudos.estudos_backend.material;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "study_materials")
public class StudyMaterial {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "study_block_id", nullable = false)
	private StudyBlock studyBlock;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 30)
	private StudyMaterialSourceType sourceType;

	@Column(name = "source_url", length = 1000)
	private String sourceUrl;

	@Column(name = "external_video_id", length = 64)
	private String externalVideoId;

	@Column(name = "language", length = 24)
	private String language;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 25)
	private StudyMaterialProcessingStatus processingStatus;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	@Column(name = "metadata_json", columnDefinition = "TEXT")
	private String metadataJson;

	@Column(name = "enabled_for_chat", nullable = false)
	private boolean enabledForChat;

	@Column(name = "video_channel", length = 255)
	private String videoChannel;

	@Column(name = "video_thumbnail_url", length = 1000)
	private String videoThumbnailUrl;

	@Column(name = "video_duration_seconds")
	private Integer videoDurationSeconds;

	@Enumerated(EnumType.STRING)
	@Column(name = "analysis_method", length = 20)
	private VideoAnalysisMethod analysisMethod;

	@Column(columnDefinition = "TEXT")
	private String summary;

	@Column(name = "summary_source", length = 160)
	private String summarySource;

	@Column(name = "summary_generated_at")
	private OffsetDateTime summaryGeneratedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StudyMaterialStatus status;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected StudyMaterial() {
	}

	public StudyMaterial(StudyBlock studyBlock, String title, String content) {
		this(studyBlock, title, content, StudyMaterialSourceType.TEXT, null, null, null,
				StudyMaterialProcessingStatus.COMPLETED, null, null, true);
	}

	public StudyMaterial(
			StudyBlock studyBlock,
			String title,
			String content,
			StudyMaterialSourceType sourceType,
			String sourceUrl,
			String externalVideoId,
			String language,
			StudyMaterialProcessingStatus processingStatus,
			String errorMessage) {
		this(studyBlock, title, content, sourceType, sourceUrl, externalVideoId, language,
				processingStatus, errorMessage, null, true);
	}

	public StudyMaterial(
			StudyBlock studyBlock,
			String title,
			String content,
			StudyMaterialSourceType sourceType,
			String sourceUrl,
			String externalVideoId,
			String language,
			StudyMaterialProcessingStatus processingStatus,
			String errorMessage,
			String metadataJson) {
		this(studyBlock, title, content, sourceType, sourceUrl, externalVideoId, language,
				processingStatus, errorMessage, metadataJson, true);
	}

	public StudyMaterial(
			StudyBlock studyBlock,
			String title,
			String content,
			StudyMaterialSourceType sourceType,
			String sourceUrl,
			String externalVideoId,
			String language,
			StudyMaterialProcessingStatus processingStatus,
			String errorMessage,
			String metadataJson,
			boolean enabledForChat) {
		this.studyBlock = studyBlock;
		this.title = title;
		this.content = content;
		this.sourceType = sourceType == null ? StudyMaterialSourceType.TEXT : sourceType;
		this.sourceUrl = sourceUrl;
		this.externalVideoId = externalVideoId;
		this.language = language;
		this.processingStatus = processingStatus == null ? StudyMaterialProcessingStatus.COMPLETED : processingStatus;
		this.errorMessage = errorMessage;
		this.metadataJson = metadataJson;
		this.enabledForChat = enabledForChat;
		this.status = StudyMaterialStatus.ACTIVE;
	}

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (sourceType == null) {
			sourceType = StudyMaterialSourceType.TEXT;
		}
		if (processingStatus == null) {
			processingStatus = StudyMaterialProcessingStatus.COMPLETED;
		}
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = OffsetDateTime.now();
	}

	public void update(String title, String content) {
		if (content != null && !content.equals(this.content)) {
			// O resumo descreve o conteúdo antigo; deixa de valer quando o texto muda.
			clearSummary();
		}
		this.title = title;
		this.content = content;
	}

	public void applySummary(String summary, String summarySource) {
		this.summary = summary;
		this.summarySource = summarySource == null || summarySource.length() <= 160 ? summarySource : summarySource.substring(0, 160);
		this.summaryGeneratedAt = OffsetDateTime.now();
	}

	public void clearSummary() {
		this.summary = null;
		this.summarySource = null;
		this.summaryGeneratedAt = null;
	}

	public String getSummary() {
		return summary;
	}

	public String getSummarySource() {
		return summarySource;
	}

	public OffsetDateTime getSummaryGeneratedAt() {
		return summaryGeneratedAt;
	}

	public void setMetadataJson(String metadataJson) {
		this.metadataJson = metadataJson;
	}

	public void setEnabledForChat(boolean enabledForChat) {
		this.enabledForChat = enabledForChat;
	}

	public void archive() {
		status = StudyMaterialStatus.ARCHIVED;
	}

	/** Canal e miniatura do vídeo (oEmbed). Valores longos são cortados ao tamanho da coluna. */
	public void applyVideoMetadata(String channel, String thumbnailUrl) {
		this.videoChannel = channel == null || channel.length() <= 255 ? channel : channel.substring(0, 255);
		this.videoThumbnailUrl = thumbnailUrl == null || thumbnailUrl.length() > 1000 ? null : thumbnailUrl;
	}

	/** Volta a fonte para "processando" (importação ou nova tentativa). */
	public void startProcessing() {
		this.processingStatus = StudyMaterialProcessingStatus.PROCESSING;
		this.errorMessage = null;
	}

	/** Conclui o processamento do vídeo com o conteúdo obtido e o método usado. */
	public void completeVideoProcessing(String title, String content, String language, VideoAnalysisMethod method,
			Integer durationSeconds) {
		if (title != null && !title.isBlank()) {
			this.title = title.length() <= 160 ? title : title.substring(0, 160);
		}
		update(this.title, content);
		this.language = language;
		this.analysisMethod = method;
		if (durationSeconds != null && durationSeconds >= 0) {
			this.videoDurationSeconds = durationSeconds;
		}
		this.processingStatus = StudyMaterialProcessingStatus.COMPLETED;
		this.errorMessage = null;
	}

	public void failProcessing(String message) {
		this.processingStatus = StudyMaterialProcessingStatus.FAILED;
		this.errorMessage = message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
	}

	public String getVideoChannel() {
		return videoChannel;
	}

	public String getVideoThumbnailUrl() {
		return videoThumbnailUrl;
	}

	public Integer getVideoDurationSeconds() {
		return videoDurationSeconds;
	}

	public VideoAnalysisMethod getAnalysisMethod() {
		return analysisMethod;
	}

	public UUID getId() {
		return id;
	}

	public StudyBlock getStudyBlock() {
		return studyBlock;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public StudyMaterialSourceType getSourceType() {
		return sourceType;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public String getExternalVideoId() {
		return externalVideoId;
	}

	public String getLanguage() {
		return language;
	}

	public StudyMaterialProcessingStatus getProcessingStatus() {
		return processingStatus;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public String getMetadataJson() {
		return metadataJson;
	}

	public boolean isEnabledForChat() {
		return enabledForChat;
	}

	public StudyMaterialStatus getStatus() {
		return status;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
