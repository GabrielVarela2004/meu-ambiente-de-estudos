import type { ReactNode } from 'react'
import { parseTimestamp, TIMESTAMP_PATTERN } from '../../utils/timestamps'

type Props = {
  text: string
  /** Sem onSeek (fonte sem vídeo), o texto é exibido sem links. */
  onSeek?: (seconds: number) => void
}

/** Texto em que cada minuto citado (03:15, [1:02:05]) vira um link que leva o vídeo àquele ponto. */
export function TimestampText({ text, onSeek }: Props) {
  if (!onSeek || !text) return <>{text}</>

  const nodes: ReactNode[] = []
  let lastIndex = 0
  for (const match of text.matchAll(TIMESTAMP_PATTERN)) {
    const seconds = parseTimestamp(match[0])
    if (seconds === null || match.index === undefined) continue
    if (match.index > lastIndex) nodes.push(text.slice(lastIndex, match.index))
    const label = match[0].replace(/[[\]]/g, '')
    nodes.push(
      <button
        key={`${match.index}-${label}`}
        type="button"
        className="btn btn-xs btn-ghost timestamp-link"
        onClick={() => onSeek(seconds)}
        title={`Ir para ${label} no vídeo`}
      >
        ▶ {label}
      </button>,
    )
    lastIndex = match.index + match[0].length
  }
  if (lastIndex < text.length) nodes.push(text.slice(lastIndex))
  return <>{nodes}</>
}
