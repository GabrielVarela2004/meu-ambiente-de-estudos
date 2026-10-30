import { useCallback, useState } from 'react'
import type { KeyboardEvent } from 'react'
import {
  approveRelevantTopics,
  getTopicReview,
  publishTopics,
  refineTopics,
  reviewTopic,
} from '../../api/topicReview'
import type { TopicReviewBoard, TopicReviewState, TopicSuggestion } from '../../api/topicReview'
import { TimestampText } from '../../components/video/TimestampText'
import { useDeferredEffect } from '../../hooks/useDeferredEffect'
import { useVideoPlayer } from '../video/videoPlayer'
import '../../styles/pages/learning-panels.css'

type Props = {
  blockId: string
  /** Muda depois de uma nova extração, para recarregar as sugestões. */
  reloadKey: number
  /** Chamado depois de oficializar, para a lista de tópicos oficiais recarregar. */
  onPublished: () => void
}

/** Relevância mínima do botão "aprovar todos". */
const BULK_MIN_RELEVANCE = 80

const stateLabels: Record<TopicReviewState, string> = {
  PENDING: 'Sem decisão',
  APPROVED: 'Aprovado',
  REJECTED: 'Excluído',
  FEEDBACK: 'Com comentário',
}

function relevanceTone(score: number | null): string {
  if (score === null) return 'is-unknown'
  if (score >= 80) return 'is-high'
  if (score >= 60) return 'is-mid'
  return 'is-low'
}

/**
 * Validação manual dos tópicos sugeridos: aprovar, excluir, comentar ou renomear cada um; revisar com IA os
 * comentados (nova rodada) e oficializar os aprovados. Atalhos no card focado: A aprova, E exclui, C comenta.
 */
export function TopicReviewPanel({ blockId, reloadKey, onPublished }: Props) {
  const videoPlayer = useVideoPlayer()
  const [board, setBoard] = useState<TopicReviewBoard | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [commentingId, setCommentingId] = useState<string | null>(null)
  const [commentDraft, setCommentDraft] = useState('')
  const [renamingId, setRenamingId] = useState<string | null>(null)
  const [renameDraft, setRenameDraft] = useState('')

  const load = useCallback(async () => {
    try {
      setBoard(await getTopicReview(blockId))
    } catch {
      setError('Não foi possível carregar as sugestões de tópicos.')
    }
  }, [blockId])

  useDeferredEffect(() => load(), [load, reloadKey])

  const run = async (key: string, action: () => Promise<string | null>) => {
    setBusy(key)
    setError(null)
    setNotice(null)
    try {
      const message = await action()
      if (message) setNotice(message)
      await load()
    } catch (err) {
      setError(err instanceof Error && err.message ? err.message : 'Não foi possível concluir a ação.')
    } finally {
      setBusy(null)
    }
  }

  const decide = (topic: TopicSuggestion, action: 'APPROVED' | 'REJECTED', title?: string) =>
    run(topic.id, async () => {
      await reviewTopic(blockId, topic.id, { action, title })
      setRenamingId(null)
      return null
    })

  const sendComment = (topic: TopicSuggestion) =>
    run(topic.id, async () => {
      await reviewTopic(blockId, topic.id, { action: 'FEEDBACK', feedback: commentDraft })
      setCommentingId(null)
      setCommentDraft('')
      return null
    })

  const openComment = (topic: TopicSuggestion) => {
    setRenamingId(null)
    setCommentingId(topic.id)
    setCommentDraft(topic.userFeedback ?? '')
  }

  const openRename = (topic: TopicSuggestion) => {
    setCommentingId(null)
    setRenamingId(topic.id)
    setRenameDraft(topic.title)
  }

  const onCardKey = (event: KeyboardEvent<HTMLElement>, topic: TopicSuggestion) => {
    // Atalhos só com o card focado, nunca enquanto se digita.
    if (event.target !== event.currentTarget || busy) return
    const key = event.key.toLowerCase()
    if (key === 'a') void decide(topic, 'APPROVED')
    else if (key === 'e') void decide(topic, 'REJECTED')
    else if (key === 'c') openComment(topic)
    else return
    event.preventDefault()
  }

  if (!board || board.items.length === 0) {
    return error ? <p className="error" role="alert">{error}</p> : null
  }

  const { items, summary } = board
  const pendingIds = new Set(items.map((item) => item.id))
  const roots = items.filter((item) => item.parentTopicId === null || !pendingIds.has(item.parentTopicId))
  const childrenOf = (id: string) => items.filter((item) => item.parentTopicId === id)
  const decided = summary.approved + summary.rejected
  const progress = summary.total === 0 ? 0 : Math.round((decided / summary.total) * 100)

  const renderCard = (topic: TopicSuggestion, isSubtopic: boolean) => {
    const videoId = topic.sourceVideoId
    const openAt = videoId && videoPlayer
      ? (seconds: number) => videoPlayer.openVideo({ videoId, title: topic.sourceMaterialTitle ?? 'Vídeo da fonte', seconds })
      : undefined
    const isBusy = busy === topic.id
    return (
      <article
        key={topic.id}
        className={`topic-suggestion is-${topic.reviewState.toLowerCase()} ${isSubtopic ? 'subtopic' : ''}`}
        tabIndex={0}
        aria-label={`Sugestão: ${topic.title}`}
        onKeyDown={(event) => onCardKey(event, topic)}
      >
        <div className="topic-suggestion-head">
          <div>
            <h4>
              {isSubtopic ? '↳ ' : ''}
              {topic.title}
            </h4>
            {topic.previousTitle && <p className="review-detail">Antes: {topic.previousTitle}</p>}
            {!isSubtopic && topic.officialParentTitle && (
              <p className="review-detail">Subtópico de "{topic.officialParentTitle}" (já oficial)</p>
            )}
          </div>
          <div className="topic-suggestion-badges">
            <span className={`relevance-badge ${relevanceTone(topic.relevanceScore)}`}
              title={topic.relevanceReason ?? undefined}>
              Relevância {topic.relevanceScore ?? '—'}
            </span>
            <span className={`review-state-badge is-${topic.reviewState.toLowerCase()}`}>
              {stateLabels[topic.reviewState]}
            </span>
          </div>
        </div>

        {topic.relevanceReason && <p className="review-detail">Por que essa relevância: {topic.relevanceReason}</p>}
        {topic.description && <p className="review-detail">{topic.description}</p>}
        {topic.supportingExcerpt && (
          <blockquote className="topic-supporting-excerpt">
            <strong>{topic.sourceMaterialTitle ? `Trecho de "${topic.sourceMaterialTitle}":` : 'Trecho da fonte:'}</strong>{' '}
            “<TimestampText text={topic.supportingExcerpt} onSeek={openAt} />”
          </blockquote>
        )}
        {topic.userFeedback && commentingId !== topic.id && (
          <p className="topic-suggestion-feedback">Seu comentário: {topic.userFeedback}</p>
        )}

        {commentingId === topic.id && (
          <div className="topic-suggestion-form">
            <label htmlFor={`comment-${topic.id}`}>O que pode ser melhorado ou acrescentado?</label>
            <textarea
              id={`comment-${topic.id}`}
              className="form-textarea"
              rows={2}
              maxLength={1000}
              autoFocus
              value={commentDraft}
              placeholder='Ex.: "separe int e long em subtópicos" ou "inclua o tamanho em bytes de cada tipo"'
              onChange={(event) => setCommentDraft(event.target.value)}
            />
            <div className="card-actions">
              <button type="button" className="btn btn-primary btn-sm" disabled={isBusy || !commentDraft.trim()}
                onClick={() => void sendComment(topic)}>
                Salvar comentário
              </button>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setCommentingId(null)}>
                Cancelar
              </button>
            </div>
          </div>
        )}

        {renamingId === topic.id && (
          <div className="topic-suggestion-form">
            <label htmlFor={`rename-${topic.id}`}>Novo título</label>
            <input
              id={`rename-${topic.id}`}
              className="form-input"
              maxLength={160}
              autoFocus
              value={renameDraft}
              onChange={(event) => setRenameDraft(event.target.value)}
            />
            <div className="card-actions">
              <button type="button" className="btn btn-primary btn-sm" disabled={isBusy || !renameDraft.trim()}
                onClick={() => void decide(topic, 'APPROVED', renameDraft.trim())}>
                Salvar nome e aprovar
              </button>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setRenamingId(null)}>
                Cancelar
              </button>
            </div>
          </div>
        )}

        {commentingId !== topic.id && renamingId !== topic.id && (
          <div className="card-actions">
            <button type="button" className="btn btn-primary btn-sm" disabled={isBusy || topic.reviewState === 'APPROVED'}
              onClick={() => void decide(topic, 'APPROVED')}>
              ✓ Aprovar
            </button>
            <button type="button" className="btn btn-danger btn-sm" disabled={isBusy || topic.reviewState === 'REJECTED'}
              onClick={() => void decide(topic, 'REJECTED')}>
              ✕ Excluir
            </button>
            <button type="button" className="btn btn-secondary btn-sm" disabled={isBusy} onClick={() => openComment(topic)}>
              Comentar
            </button>
            <button type="button" className="btn btn-ghost btn-sm" disabled={isBusy} onClick={() => openRename(topic)}>
              Renomear
            </button>
          </div>
        )}
      </article>
    )
  }

  return (
    <section className="card topic-review-panel" aria-labelledby="topic-review-heading">
      <div className="section-heading">
        <div>
          <h3 id="topic-review-heading">
            Sugestões para validar · Rodada {summary.round}
          </h3>
          <p className="subtitle">
            Nada aqui entra em questões ou no desempenho até você oficializar. Atalhos com o card selecionado:
            A aprova, E exclui, C comenta.
          </p>
        </div>
        <div className="topic-review-progress" aria-label={`${decided} de ${summary.total} decididos`}>
          <strong>{decided} de {summary.total} decididos</strong>
          <div className="topic-mastery-bar" aria-hidden="true">
            <span style={{ width: `${progress}%` }} />
          </div>
          {summary.firstRoundApprovalPercent !== null && (
            <span className="review-detail">
              Precisão da extração (aprovados na 1ª rodada): {summary.firstRoundApprovalPercent}%
            </span>
          )}
        </div>
      </div>

      <div className="card-actions topic-review-toolbar">
        <button type="button" className="btn btn-secondary btn-sm" disabled={busy !== null || summary.undecided === 0}
          onClick={() => void run('bulk', async () => {
            const { count } = await approveRelevantTopics(blockId, BULK_MIN_RELEVANCE)
            return count === 0
              ? `Nenhuma sugestão sem decisão com relevância ${BULK_MIN_RELEVANCE} ou mais.`
              : `${count} sugestão(ões) com relevância ${BULK_MIN_RELEVANCE}+ aprovada(s).`
          })}>
          Aprovar todos com relevância ≥ {BULK_MIN_RELEVANCE}
        </button>
        <button type="button" className="btn btn-secondary btn-sm" disabled={busy !== null || summary.withFeedback === 0}
          onClick={() => void run('refine', async () => {
            const { count } = await refineTopics(blockId)
            return `${count} tópico(s) revisado(s) pela IA com base nos seus comentários. Valide a nova rodada.`
          })}>
          {busy === 'refine' ? 'Revisando…' : `Revisar com IA (${summary.withFeedback} com comentário)`}
        </button>
        <button type="button" className="btn btn-primary btn-sm" disabled={busy !== null || decided === 0}
          onClick={() => void run('publish', async () => {
            const result = await publishTopics(blockId)
            onPublished()
            return `${result.published} tópico(s) oficializado(s), ${result.archived} excluído(s)`
              + (result.waitingForParent > 0
                ? `; ${result.waitingForParent} subtópico(s) aguardam a aprovação do tópico principal.`
                : '.')
          })}>
          {busy === 'publish' ? 'Oficializando…' : `Oficializar ${summary.approved} aprovado(s)`}
        </button>
      </div>

      {error && <p className="error" role="alert">{error}</p>}
      {notice && <p className="feedback-banner success" role="status">{notice}</p>}

      <div className="topic-suggestion-list">
        {roots.map((topic) => (
          <div key={topic.id} className="topic-suggestion-group">
            {renderCard(topic, false)}
            {childrenOf(topic.id).map((child) => renderCard(child, true))}
          </div>
        ))}
      </div>
    </section>
  )
}
