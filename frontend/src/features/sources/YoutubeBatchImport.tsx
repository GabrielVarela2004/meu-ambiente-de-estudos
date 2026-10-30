import { useState } from 'react'
import type { FormEvent } from 'react'
import { importYoutubeVideo } from '../../api/materials'
import { extractYoutubeLinks, MAX_BATCH_LINKS } from '../../utils/youtubeLinks'

type Props = {
  blockId: string
  disabled?: boolean
  onImported: (blockId: string) => void
}

type LinkResult = {
  link: string
  ok: boolean
  message: string
}

/**
 * Vários vídeos de uma vez (decisão D2): cola os links, um por linha, e cada um é importado pela rota normal.
 * Os envios são em sequência; o processamento (IA ou transcrição) segue em segundo plano no backend.
 */
export function YoutubeBatchImport({ blockId, disabled = false, onImported }: Props) {
  const [text, setText] = useState('')
  const [enabledForChat, setEnabledForChat] = useState(true)
  const [running, setRunning] = useState(false)
  const [progress, setProgress] = useState(0)
  const [results, setResults] = useState<LinkResult[]>([])

  const { links, ignored, overLimit } = extractYoutubeLinks(text)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!blockId || links.length === 0) return
    setRunning(true)
    setProgress(0)
    setResults([])
    const collected: LinkResult[] = []
    for (const link of links) {
      try {
        const created = await importYoutubeVideo<{ title: string }>(blockId, { url: link, enabledForChat })
        collected.push({ link, ok: true, message: created.title })
      } catch (err) {
        collected.push({
          link,
          ok: false,
          message: err instanceof Error && err.message ? err.message : 'Não foi possível adicionar este vídeo.',
        })
      }
      setProgress(collected.length)
      setResults([...collected])
    }
    setRunning(false)
    if (collected.some((r) => r.ok)) {
      setText(collected.filter((r) => !r.ok).map((r) => r.link).join('\n'))
      onImported(blockId)
    }
  }

  const added = results.filter((r) => r.ok).length

  return (
    <form onSubmit={handleSubmit} className="card source-form-card">
      <div className="form-field-group">
        <label htmlFor="yt-batch-links">Links dos vídeos (um por linha)</label>
        <textarea
          id="yt-batch-links"
          className="form-textarea"
          rows={8}
          placeholder={'https://youtu.be/...\nhttps://youtu.be/...\nhttps://www.youtube.com/watch?v=...'}
          value={text}
          onChange={(e) => setText(e.target.value)}
          disabled={running}
        />
        <p className="form-hint">
          {links.length} vídeo(s) reconhecido(s)
          {ignored.length > 0 && ` · ${ignored.length} trecho(s) ignorado(s) por não serem links de vídeo`}
          {overLimit > 0 && ` · ${overLimit} acima do limite de ${MAX_BATCH_LINKS} por envio`}. Vídeos repetidos entram
          uma vez só. Para uma playlist, abra cada aula e copie o link pelo botão Compartilhar.
        </p>
      </div>

      <div className="form-checkbox-row">
        <label className="checkbox-label-styled">
          <input type="checkbox" checked={enabledForChat} onChange={(e) => setEnabledForChat(e.target.checked)} />
          <span className="checkbox-text">Habilitar estes vídeos para o Chat do Bloco</span>
        </label>
      </div>

      {(running || results.length > 0) && (
        <div className="youtube-batch-results" aria-live="polite">
          <p className="form-hint">
            {running
              ? `Adicionando ${Math.min(progress + 1, links.length)} de ${links.length}…`
              : `${added} de ${results.length} vídeo(s) adicionado(s). Acompanhe o processamento na área do bloco.`}
          </p>
          <ul className="youtube-batch-list">
            {results.map((r) => (
              <li key={r.link} className={r.ok ? 'is-ok' : 'is-error'}>
                <span className={`badge ${r.ok ? 'badge-success' : 'badge-danger'}`}>{r.ok ? 'Adicionado' : 'Erro'}</span>
                <span className="youtube-batch-link">{r.ok ? r.message : r.link}</span>
                {!r.ok && <span className="youtube-batch-error">{r.message}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="form-footer-actions">
        <button type="submit" className="btn btn-primary" disabled={disabled || running || links.length === 0}>
          {running ? 'Adicionando…' : `Adicionar ${links.length} vídeo(s) como fontes`}
        </button>
      </div>
    </form>
  )
}
