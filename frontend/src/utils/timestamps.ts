// MM:SS ou H:MM:SS, opcionalmente entre colchetes ([03:15]), como nas anotações e transcrições de vídeo.
export const TIMESTAMP_PATTERN = /\[?\b(?:(\d{1,2}):)?(\d{1,2}):([0-5]\d)\b\]?/g

/** Converte "MM:SS" ou "H:MM:SS" (com ou sem colchetes) em segundos; formato inválido devolve null. */
export function parseTimestamp(text: string): number | null {
  const match = /^\[?(?:(\d{1,2}):)?(\d{1,2}):([0-5]\d)\]?$/.exec(text.trim())
  if (!match) return null
  const hours = match[1] ? Number(match[1]) : 0
  const minutes = Number(match[2])
  if (match[1] && minutes > 59) return null
  return hours * 3600 + minutes * 60 + Number(match[3])
}
