/** Limite de vídeos por envio em lote (cada um é processado em segundo plano). */
export const MAX_BATCH_LINKS = 50

// Mesmo critério do YoutubeUrlParser do backend: youtu.be/ID, watch?v=ID, shorts/, embed/, live/.
const VIDEO_ID_IN_LINK = /(?:youtu\.be\/|[?&]v=|\/shorts\/|\/embed\/|\/live\/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])/

export type ExtractedLinks = {
  /** Links de vídeo únicos, na ordem em que apareceram (repetições do mesmo vídeo descartadas). */
  links: string[]
  /** Trechos que não parecem link de vídeo do YouTube. */
  ignored: string[]
  /** Quantos links passaram do limite e foram deixados de fora. */
  overLimit: number
}

/**
 * Separa os links de vídeo de um texto colado (um por linha, ou separados por espaço/vírgula), descartando
 * repetições do mesmo vídeo. A validação definitiva é do backend.
 */
export function extractYoutubeLinks(text: string): ExtractedLinks {
  const seen = new Set<string>()
  const links: string[] = []
  const ignored: string[] = []
  let overLimit = 0
  for (const token of text.split(/[\s,;]+/)) {
    const candidate = token.trim()
    if (!candidate) continue
    const match = VIDEO_ID_IN_LINK.exec(candidate)
    if (!match || !/youtu\.?be|youtube/i.test(candidate)) {
      ignored.push(candidate)
      continue
    }
    if (seen.has(match[1])) continue
    seen.add(match[1])
    if (links.length >= MAX_BATCH_LINKS) {
      overLimit++
      continue
    }
    links.push(candidate)
  }
  return { links, ignored, overLimit }
}
