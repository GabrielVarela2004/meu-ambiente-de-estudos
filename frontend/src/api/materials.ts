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
