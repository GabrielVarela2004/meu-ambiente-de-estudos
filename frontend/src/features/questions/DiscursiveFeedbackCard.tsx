import type { AnswerClassification, DiscursiveFeedback } from '../../api/questions'
import { TimestampText } from '../../components/video/TimestampText'
import { parseTimestamp } from '../../utils/timestamps'
import { useVideoPlayer } from '../video/videoPlayer'
import '../../styles/pages/open-questions.css'

const classificationLabels: Record<AnswerClassification, string> = {
  CORRECT: 'CORRETA',
  PARTIAL: 'PARCIAL',
  INCORRECT: 'INCORRETA',
  PENDING_EVALUATION: 'PENDENTE DE AVALIAÇÃO',
  UNGRADABLE: 'NÃO AVALIÁVEL',
}

const criteriaLabels: Record<string, string> = {
  conceptualAccuracy: 'Exatidão conceitual',
  essentialCoverage: 'Conceitos essenciais',
  depth: 'Profundidade',
  terminology: 'Terminologia do material',
  noErrors: 'Sem erros ou contradições',
}

type Props = {
  feedback: DiscursiveFeedback
  /** Responder de novo a mesma pergunta (nova tentativa). */
  onAnswerAgain?: () => void
  /** Reavaliar uma resposta pendente quando a IA voltar. */
  onReevaluate?: () => void
  reevaluating?: boolean
}

function ListSection({ title, items, tone }: { title: string; items: string[]; tone: 'ok' | 'missing' | 'error' }) {
  if (items.length === 0) return null
  return (
    <section className={`open-feedback-section is-${tone}`}>
      <h5>{title}</h5>
      <ul>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  )
}

/**
 * Feedback da questão aberta: classificação e nota, o que acertou, essenciais faltando, termos do material,
 * erros, resposta-modelo, trecho do material (minuto clicável se for vídeo) e como melhorar.
 */
export function DiscursiveFeedbackCard({ feedback, onAnswerAgain, onReevaluate, reevaluating = false }: Props) {
  const videoPlayer = useVideoPlayer()
  const label = classificationLabels[feedback.classification] ?? feedback.classification
  const videoId = feedback.sourceVideoId
  const openAt = videoId && videoPlayer
    ? (seconds: number) =>
        videoPlayer.openVideo({ videoId, title: feedback.sourceMaterialTitle ?? 'Vídeo da fonte', seconds })
    : undefined
  const timestampSeconds = feedback.sourceTimestamp ? parseTimestamp(feedback.sourceTimestamp) : null

  if (feedback.pending) {
    return (
      <div className="open-feedback-card is-pending" role="status">
        <div className="open-feedback-header">
          <span className="badge badge-warning">{label}</span>
        </div>
        <p>{feedback.pendingMessage}</p>
        <div className="open-feedback-actions">
          {onReevaluate && (
            <button type="button" className="btn btn-secondary btn-sm" onClick={onReevaluate} disabled={reevaluating}>
              {reevaluating ? 'Reavaliando…' : 'Reavaliar agora'}
            </button>
          )}
          {onAnswerAgain && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={onAnswerAgain}>
              Responder de novo
            </button>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className={`open-feedback-card is-${feedback.classification.toLowerCase()}`}>
      <div className="open-feedback-header">
        <span className="open-feedback-classification">{label}</span>
        {feedback.scorePercent !== null && <span className="open-feedback-score">{feedback.scorePercent}/100</span>}
        {feedback.rigorLevel && (
          <span className="badge badge-subtle">Rigor: {feedback.rigorLevel === 'RIGOROSO' ? 'rigoroso' : 'moderado'}</span>
        )}
      </div>

      {Object.keys(feedback.criteriaScores).length > 0 && (
        <dl className="open-feedback-criteria">
          {Object.entries(feedback.criteriaScores).map(([key, value]) => (
            <div key={key}>
              <dt>{criteriaLabels[key] ?? key}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      )}

      <p className="open-feedback-headline">
        {feedback.classification === 'CORRECT'
          ? 'Parabéns, você acertou! Confira abaixo o que fez bem.'
          : 'Correção: veja onde a resposta falhou, a resposta correta e a explicação.'}
      </p>

      <ListSection title="O que você acertou" items={feedback.correctPoints} tone="ok" />
      <ListSection title="Correção: erros na sua resposta" items={feedback.errors} tone="error" />
      <ListSection title="Conceitos essenciais que faltaram" items={feedback.missingEssentialConcepts} tone="missing" />
      <ListSection title="Termos do material que você deveria usar" items={feedback.missingTerms} tone="missing" />

      {feedback.improvementTip && (
        <p className="open-feedback-tip">
          <strong>Como melhorar:</strong> {feedback.improvementTip}
        </p>
      )}

      {feedback.modelAnswer && (
        <section className="open-feedback-section">
          <h5>Resposta correta</h5>
          <p className="open-feedback-model">{feedback.modelAnswer}</p>
        </section>
      )}

      {feedback.explanation && (
        <section className="open-feedback-section">
          <h5>Explicação detalhada</h5>
          <p>{feedback.explanation}</p>
        </section>
      )}

      {feedback.classification !== 'CORRECT' && (
        <ListSection title="Erros comuns sobre este tema" items={feedback.commonMistakes ?? []} tone="missing" />
      )}

      {feedback.sourceExcerpt && (
        <blockquote className="excerpt-blockquote">
          {feedback.sourceMaterialTitle && <strong>Fonte: {feedback.sourceMaterialTitle}</strong>}
          <p>
            <TimestampText text={feedback.sourceExcerpt} onSeek={openAt} />
          </p>
          {openAt && timestampSeconds !== null && (
            <button type="button" className="btn btn-secondary btn-xs" onClick={() => openAt(timestampSeconds)}>
              ▶ Ver no vídeo em {feedback.sourceTimestamp}
            </button>
          )}
        </blockquote>
      )}

      {onAnswerAgain && (
        <div className="open-feedback-actions">
          <button type="button" className="btn btn-secondary btn-sm" onClick={onAnswerAgain}>
            Responder de novo
          </button>
        </div>
      )}
    </div>
  )
}
