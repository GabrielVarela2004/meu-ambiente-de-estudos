import { request } from './http'

// Rotas de StudyMaterialController (/api/study-blocks/{studyBlockId}/materials)
const base = (blockId: string) => `/api/study-blocks/${blockId}/materials`

/** Alterna se a fonte entra no contexto do chat; o backend devolve o material atualizado. */
export function toggleMaterialChat<T>(blockId: string, materialId: string): Promise<T> {
  return request<T>(`${base(blockId)}/${materialId}/toggle-chat`, { method: 'PATCH' })
}

/** Envia um arquivo (multipart, campo "file") para ser importado como fonte do bloco. */
export function importMaterialFile<T>(blockId: string, file: File): Promise<T> {
  const formData = new FormData()
  formData.append('file', file)
  return request<T>(`${base(blockId)}/import`, { method: 'POST', body: formData })
}

/** Prévia de um vídeo do YouTube antes de salvar (GET …/materials/youtube/preview, via oEmbed no backend). */
export type YoutubePreview = {
  videoId: string
  canonicalUrl: string
  embedUrl: string
  title: string | null
  channel: string | null
  thumbnailUrl: string | null
  alreadyImported: boolean
  aiAnalysisAvailable: boolean
}

export function previewYoutube(blockId: string, url: string): Promise<YoutubePreview> {
  return request<YoutubePreview>(`${base(blockId)}/youtube/preview?url=${encodeURIComponent(url)}`)
}

/**
 * Importa um vídeo do YouTube. A fonte volta como PROCESSING e é concluída em segundo plano
 * (análise do vídeo pela IA ou, como plano B, a transcrição).
 */
export function importYoutubeVideo<T>(
  blockId: string,
  input: { url: string; title?: string; enabledForChat: boolean },
): Promise<T> {
  return request<T>(`${base(blockId)}/youtube`, { method: 'POST', body: JSON.stringify(input) })
}

/** Nova tentativa de processar um vídeo (após erro ou para tentar de novo a análise por IA). */
export function reprocessMaterial<T>(blockId: string, materialId: string): Promise<T> {
  return request<T>(`${base(blockId)}/${materialId}/reprocess`, { method: 'POST' })
}
