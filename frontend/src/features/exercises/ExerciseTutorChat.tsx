import { useCallback, useState } from 'react'
import { ApiError, request } from '../../api/http'
import { MessageSquareIcon } from '../../components/icons'
import { useDeferredEffect } from '../../hooks/useDeferredEffect'

export type ExerciseHelpLevel =
  | 'CLARIFICATION'
  | 'CONCEPT'
  | 'DIRECTION'
  | 'SPECIFIC_HINT'
  | 'PARTIAL_CODE'
  | 'STEP_SOLUTION'
  | 'FULL_SOLUTION'

type TutorMessage = {
  id: string
  stepIndex: number | null
  userMessage: string
  assistantAnswer: string
  helpLevel: ExerciseHelpLevel
  helpLevelLabel: string
  penaltyPercent: number
  createdAt: string
}

export type TutorSession = {
  messages: TutorMessage[]
  autonomyPercent: number
  hintsUsed: number
  solutionRevealed: boolean
  penaltyByStep: Record<string, number>
}

type Props = {
  exercisePath: string
  steps: string[]
  currentCode: string
  /** Muda a cada tentativa registrada, para recarregar a autonomia (que volta a 100%). */
  refreshKey: number
}

function penaltyText(message: TutorMessage, stepsCount: number) {
  if (message.helpLevel === 'FULL_SOLUTION') return 'nota limitada a 30%'
  if (message.helpLevel === 'STEP_SOLUTION') return `−${Math.round(100 / Math.max(1, stepsCount))}% (etapa)`
  return message.penaltyPercent === 0 ? 'sem desconto' : `−${message.penaltyPercent}%`
}

export function ExerciseTutorChat({ exercisePath, steps, currentCode, refreshKey }: Props) {
  const [session, setSession] = useState<TutorSession | null>(null)
  const [message, setMessage] = useState('')
  const [stepIndex, setStepIndex] = useState<number | null>(steps.length > 0 ? 0 : null)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setSession(await request<TutorSession>(`${exercisePath}/tutor`))
    } catch {
      setError('Não foi possível carregar a conversa deste exercício.')
    }
  }, [exercisePath])

  useDeferredEffect(() => load(), [load, refreshKey])

  const send = async () => {
    if (!message.trim()) return
    setSending(true)
    setError(null)
    try {
      const updated = await request<TutorSession>(`${exercisePath}/tutor/messages`, {
        method: 'POST',
        body: JSON.stringify({ message, currentCode, stepIndex }),
      })
      setSession(updated)
      setMessage('')
    } catch (e) {
      setError(
        e instanceof ApiError && e.status === 503
          ? 'Nenhum provedor de IA está disponível para a tutoria. Configure uma chave em Configurações.'
          : e instanceof Error
            ? e.message
            : 'Erro ao falar com a tutora.'
      )
    } finally {
      setSending(false)
    }
  }

  const revealSolution = async () => {
    const confirmed = window.confirm(
      'Ver a solução completa limita a nota da próxima tentativa a 30% e ela não conta como acerto no desempenho do bloco. Continuar?'
    )
    if (!confirmed) return
    setError(null)
    try {
      setSession(await request<TutorSession>(`${exercisePath}/tutor/reveal-solution`, { method: 'POST' }))
    } catch {
      setError('Não foi possível revelar a solução.')
    }
  }

  const autonomy = session?.autonomyPercent ?? 100

  return (
    <div className="exercise-tutor">
      <div className="exercise-tutor-header">
        <div className="flex-gap-center">
          <MessageSquareIcon className="w-4 h-4" />
          <strong>Tutora do exercício</strong>
        </div>
        <span
          className={`badge ${autonomy >= 85 ? 'badge-emerald' : 'badge-amber'}`}
          title="Autonomia da próxima tentativa, calculada pelas ajudas recebidas desde a última tentativa."
        >
          Autonomia: {autonomy}%
        </span>
      </div>
      {session && Object.keys(session.penaltyByStep).length > 0 && (
        <p className="text-xs exercise-tutor-difficulty">
          Onde você precisou de ajuda:{' '}
          {Object.entries(session.penaltyByStep)
            .map(([step, penalty]) => `${step === '-1' ? 'Geral' : `Etapa ${Number(step) + 1}`} (−${penalty}%)`)
            .join(' · ')}
        </p>
      )}
      <p className="text-xs text-muted">
        Converse sobre a etapa em que travou. Esclarecer o enunciado não desconta; dicas descontam conforme o quanto
        da solução foi entregue.
      </p>

      {session && session.messages.length > 0 && (
        <ol className="exercise-tutor-messages">
          {session.messages.map((m) => (
            <li key={m.id} className="exercise-tutor-exchange">
              <div className="exercise-tutor-user">
                {m.stepIndex !== null && steps[m.stepIndex] && (
                  <span className="text-xs text-muted">Etapa {m.stepIndex + 1} · </span>
                )}
                {m.userMessage}
              </div>
              <div className="exercise-tutor-answer">
                <p className="whitespace-pre-wrap">{m.assistantAnswer}</p>
                <span className={`exercise-help-tag help-${m.helpLevel.toLowerCase()}`}>
                  {m.helpLevelLabel} · {penaltyText(m, steps.length)}
                </span>
              </div>
            </li>
          ))}
        </ol>
      )}

      {error && <div className="alert-error-banner">{error}</div>}

      <div className="exercise-tutor-form">
        {steps.length > 0 && (
          <select
            aria-label="Etapa da dúvida"
            className="form-select form-select-sm"
            value={stepIndex ?? ''}
            onChange={(e) => setStepIndex(e.target.value === '' ? null : Number(e.target.value))}
          >
            <option value="">Dúvida geral</option>
            {steps.map((step, i) => (
              <option key={i} value={i}>
                Etapa {i + 1}: {step.length > 50 ? `${step.slice(0, 50)}…` : step}
              </option>
            ))}
          </select>
        )}
        <textarea
          aria-label="Mensagem para a tutora"
          className="form-textarea text-sm"
          rows={2}
          placeholder="Ex.: meu método da subclasse não está sendo chamado, o que pode ser?"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              void send()
            }
          }}
        />
        <div className="exercise-tutor-actions">
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void revealSolution()}>
            Ver solução
          </button>
          <button type="button" className="btn btn-primary btn-sm" onClick={() => void send()} disabled={sending || !message.trim()}>
            {sending ? 'Enviando...' : 'Perguntar'}
          </button>
        </div>
      </div>
    </div>
  )
}
