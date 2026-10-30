// Tipos das fontes de estudo (StudyMaterialController › StudyMaterialResponse).

export type SourceType =
  | 'TEXT'
  | 'FILE_TXT'
  | 'FILE_MD'
  | 'FILE_PDF'
  | 'FILE_DOCX'
  | 'FILE_PPTX'
  | 'FILE_CODE'
  | 'WEB'
  | 'YOUTUBE'
export type ProcessingStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

/** Como o conteúdo de um vídeo do YouTube foi obtido (análise do vídeo pela IA ou transcrição). */
export type VideoAnalysisMethod = 'ANALISE_IA' | 'TRANSCRICAO'

export type StudyMaterialItem = {
  id: string
  studyBlockId: string
  title: string
  content: string
  sourceType: SourceType
  sourceUrl: string | null
  externalVideoId: string | null
  language: string | null
  processingStatus: ProcessingStatus
  errorMessage: string | null
  metadataJson?: string | null
  enabledForChat?: boolean
  videoChannel?: string | null
  videoThumbnailUrl?: string | null
  videoDurationSeconds?: number | null
  analysisMethod?: VideoAnalysisMethod | null
  wordCount?: number
  charCount?: number
  status: 'ACTIVE' | 'ARCHIVED'
  summary?: string | null
  summarySource?: string | null
  summaryGeneratedAt?: string | null
  createdAt: string
  updatedAt: string
}
