import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import {
  addGoalProgress,
  archiveGoal,
  changeSessionState,
  createGoal,
  listGoals,
  listSessions,
  planSession,
} from '../api/goals'
import type { GoalStatus, SessionStatus, StudyGoal, StudySession } from '../api/goals'
import { CheckCircleIcon, ClockIcon, TargetIcon } from '../components/icons'
import type { StudyBlock } from './StudyBlocksPage'
import '../styles/pages/goals-sessions.css'
import { useDeferredEffect } from '../hooks/useDeferredEffect'

type Goal = StudyGoal
type Session = StudySession

type Props = {
  blocks: StudyBlock[]
  selectedBlockId: string | null
  onSelectBlock: (id: string | null) => void
}

const goalStatusLabel: Record<GoalStatus, { label: string; className: string }> = {
  ACTIVE: { label: 'Em andamento', className: 'badge-info' },
  COMPLETED: { label: 'Concluída', className: 'badge-success' },
  ARCHIVED: { label: 'Arquivada', className: 'badge-subtle' },
}

const sessionStatusLabel: Record<SessionStatus, { label: string; className: string }> = {
  PLANNED: { label: 'Planejada', className: 'badge-subtle' },
  IN_PROGRESS: { label: 'Em andamento', className: 'badge-warning' },
  COMPLETED: { label: 'Concluída', className: 'badge-success' },
  CANCELLED: { label: 'Cancelada', className: 'badge-danger' },
}

/** Minutos efetivamente estudados: do início ao fim (ou até agora, se em andamento). */
function studiedMinutes(session: Session, now: number): number {
  if (!session.startedAt) return 0
  const end = session.endedAt ? new Date(session.endedAt).getTime() : now
  return Math.max(0, Math.round((end - new Date(session.startedAt).getTime()) / 60000))
}

/** Tempo decorrido da sessão em andamento como H:MM:SS (cronômetro). */
export function formatElapsed(session: Session, now: number): string {
  if (!session.startedAt) return '0:00:00'
  const end = session.endedAt ? new Date(session.endedAt).getTime() : now
  const total = Math.max(0, Math.floor((end - new Date(session.startedAt).getTime()) / 1000))
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function formatDateTime(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

export function GoalsSessionsPage({ blocks, selectedBlockId, onSelectBlock }: Props) {
  const activeBlocks = blocks.filter((b) => b.status === 'ACTIVE')
  const blockId = selectedBlockId && activeBlocks.some((b) => b.id === selectedBlockId) ? selectedBlockId : null

  const [goals, setGoals] = useState<Goal[]>([])
  const [sessions, setSessions] = useState<Session[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [now, setNow] = useState(() => Date.now())

  const [goalForm, setGoalForm] = useState({ title: '', description: '', targetUnits: 10 })
  const [progressInput, setProgressInput] = useState<Record<string, number>>({})
  const [showArchivedGoals, setShowArchivedGoals] = useState(false)
  const [sessionForm, setSessionForm] = useState({ plannedMinutes: 25, notes: '' })

  const load = useCallback(async () => {
    if (!blockId) return
    setLoading(true)
    setError(null)
    try {
      const [g, s] = await Promise.all([listGoals(blockId), listSessions(blockId)])
      setGoals(g)
      setSessions(s)
    } catch {
      setError('Não foi possível carregar metas e sessões deste bloco.')
    } finally {
      setLoading(false)
    }
  }, [blockId])

  useDeferredEffect(() => load(), [load])

  // Cronômetro: atualiza a cada segundo enquanto houver sessão em andamento.
  const hasRunningSession = sessions.some((s) => s.status === 'IN_PROGRESS')
  useEffect(() => {
    if (!hasRunningSession) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [hasRunningSession])

  const run = async (id: string, action: () => Promise<void>, failMessage: string) => {
    setBusyId(id)
    setError(null)
    try {
      await action()
    } catch (err) {
      setError(err instanceof Error && err.message ? `${failMessage} (${err.message})` : failMessage)
    } finally {
      setBusyId(null)
    }
  }

  const handleCreateGoal = (e: FormEvent) => {
    e.preventDefault()
    if (!blockId || !goalForm.title.trim()) return
    void run('new-goal', async () => {
      const created = await createGoal(blockId, {
        title: goalForm.title.trim(),
        description: goalForm.description.trim() || null,
        targetUnits: goalForm.targetUnits,
      })
      setGoals((prev) => [created, ...prev])
      setGoalForm({ title: '', description: '', targetUnits: 10 })
    }, 'Erro ao criar a meta.')
  }

  const handleProgress = (goal: Goal) => {
    const units = Math.min(progressInput[goal.id] ?? 1, goal.targetUnits - goal.completedUnits)
    if (!blockId || units < 1) return
    void run(goal.id, async () => {
      const updated = await addGoalProgress(blockId, goal.id, units)
      setGoals((prev) => prev.map((g) => (g.id === goal.id ? updated : g)))
      setProgressInput((prev) => ({ ...prev, [goal.id]: 1 }))
    }, 'Erro ao registrar progresso.')
  }

  const handleArchiveGoal = (goal: Goal) => {
    if (!blockId) return
    void run(goal.id, async () => {
      await archiveGoal(blockId, goal.id)
      setGoals((prev) => prev.map((g) => (g.id === goal.id ? { ...g, status: 'ARCHIVED' } : g)))
    }, 'Erro ao arquivar a meta.')
  }

  const handleCreateSession = (e: FormEvent) => {
    e.preventDefault()
    if (!blockId) return
    void run('new-session', async () => {
      const created = await planSession(blockId, {
        plannedMinutes: sessionForm.plannedMinutes,
        notes: sessionForm.notes.trim() || null,
      })
      setSessions((prev) => [created, ...prev])
      setSessionForm((prev) => ({ ...prev, notes: '' }))
    }, 'Erro ao planejar a sessão.')
  }

  const handleSessionAction = (session: Session, action: 'start' | 'complete' | 'cancel') => {
    if (!blockId) return
    void run(session.id, async () => {
      const updated = await changeSessionState(blockId, session.id, action)
      setSessions((prev) => prev.map((s) => (s.id === session.id ? updated : s)))
      setNow(Date.now())
    }, 'Erro ao atualizar a sessão.')
  }

  const visibleGoals = goals.filter((g) => showArchivedGoals || g.status !== 'ARCHIVED')
  const activeGoals = goals.filter((g) => g.status === 'ACTIVE').length
  const completedGoals = goals.filter((g) => g.status === 'COMPLETED').length
  const weekAgo = now - 7 * 24 * 60 * 60 * 1000
  const minutesThisWeek = sessions
    .filter((s) => s.status === 'COMPLETED' && s.endedAt && new Date(s.endedAt).getTime() >= weekAgo)
    .reduce((sum, s) => sum + studiedMinutes(s, now), 0)
  const completedSessions = sessions.filter((s) => s.status === 'COMPLETED').length

  return (
    <div className="page-wrapper">
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Metas e Sessões</h1>
          <p className="page-main-subtitle">
            Defina metas mensuráveis para cada bloco e registre o tempo real das suas sessões de estudo.
          </p>
        </div>
        <div className="page-actions-group">
          <select
            className="form-select"
            aria-label="Bloco de estudo"
            value={blockId ?? ''}
            onChange={(e) => onSelectBlock(e.target.value || null)}
          >
            <option value="">Selecione um bloco…</option>
            {activeBlocks.map((b) => (
              <option key={b.id} value={b.id}>
                {b.title}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error && <div className="alert-error-banner mb-4">{error}</div>}

      {!blockId ? (
        <div className="card empty-state-box">
          <TargetIcon className="empty-icon" />
          <h3>Selecione um bloco de estudo</h3>
          <p>Metas e sessões pertencem a um bloco. Escolha um bloco ativo acima ou na barra lateral.</p>
        </div>
      ) : (
        <>
          <div className="retention-kpi-grid mb-6">
            <div className="stat-card">
              <span className="kpi-label">Metas em andamento</span>
              <span className="kpi-value">{activeGoals}</span>
            </div>
            <div className="stat-card">
              <span className="kpi-label">Metas concluídas</span>
              <span className="kpi-value">{completedGoals}</span>
            </div>
            <div className="stat-card">
              <span className="kpi-label">Sessões concluídas</span>
              <span className="kpi-value">{completedSessions}</span>
            </div>
            <div className="stat-card">
              <span className="kpi-label">Minutos nos últimos 7 dias</span>
              <span className="kpi-value">{minutesThisWeek}</span>
            </div>
          </div>

          <div className="goals-sessions-grid">
            {/* ---- Metas ---- */}
            <section className="card" aria-labelledby="goals-heading">
              <div className="section-heading">
                <div>
                  <h2 id="goals-heading">Metas</h2>
                  <p className="subtitle">Ex.: 20 questões, 5 exercícios práticos, 10 capítulos.</p>
                </div>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={showArchivedGoals}
                    onChange={(e) => setShowArchivedGoals(e.target.checked)}
                  />
                  <span className="checkbox-text">Mostrar arquivadas</span>
                </label>
              </div>

              <form className="gs-form" onSubmit={handleCreateGoal}>
                <div className="form-field">
                  <label htmlFor="goal-title">Nova meta</label>
                  <input
                    id="goal-title"
                    className="form-input"
                    placeholder="O que você quer cumprir?"
                    maxLength={160}
                    value={goalForm.title}
                    onChange={(e) => setGoalForm({ ...goalForm, title: e.target.value })}
                  />
                </div>
                <div className="gs-form-row">
                  <div className="form-field flex-1">
                    <label htmlFor="goal-desc">Descrição (opcional)</label>
                    <input
                      id="goal-desc"
                      className="form-input"
                      maxLength={2000}
                      value={goalForm.description}
                      onChange={(e) => setGoalForm({ ...goalForm, description: e.target.value })}
                    />
                  </div>
                  <div className="form-field gs-number-field">
                    <label htmlFor="goal-target">Unidades</label>
                    <input
                      id="goal-target"
                      type="number"
                      className="form-input"
                      min={1}
                      max={100000}
                      value={goalForm.targetUnits}
                      onChange={(e) => setGoalForm({ ...goalForm, targetUnits: Math.max(1, Number(e.target.value) || 1) })}
                    />
                  </div>
                </div>
                <div className="form-actions">
                  <button
                    type="submit"
                    className="btn btn-primary btn-sm"
                    disabled={!goalForm.title.trim() || busyId === 'new-goal'}
                  >
                    {busyId === 'new-goal' ? 'Criando…' : 'Criar meta'}
                  </button>
                </div>
              </form>

              {loading && goals.length === 0 ? (
                <p className="text-muted text-sm">Carregando metas…</p>
              ) : visibleGoals.length === 0 ? (
                <p className="empty-subnotice">Nenhuma meta neste bloco ainda.</p>
              ) : (
                <ul className="gs-list">
                  {visibleGoals.map((goal) => {
                    const percent = Math.round((goal.completedUnits / goal.targetUnits) * 100)
                    const remaining = goal.targetUnits - goal.completedUnits
                    const status = goalStatusLabel[goal.status]
                    return (
                      <li key={goal.id} className={`gs-item ${goal.status === 'ARCHIVED' ? 'is-muted' : ''}`}>
                        <div className="gs-item-header">
                          <strong className="gs-item-title">{goal.title}</strong>
                          <span className={`badge ${status.className}`}>{status.label}</span>
                        </div>
                        {goal.description && <p className="gs-item-desc">{goal.description}</p>}
                        <div
                          className="gs-progress"
                          role="progressbar"
                          aria-valuemin={0}
                          aria-valuemax={goal.targetUnits}
                          aria-valuenow={goal.completedUnits}
                          aria-label={`Progresso de ${goal.title}`}
                        >
                          <div className="gs-progress-fill" style={{ width: `${percent}%` }} />
                        </div>
                        <div className="gs-item-footer">
                          <span className="text-xs text-secondary">
                            {goal.completedUnits} de {goal.targetUnits} ({percent}%)
                          </span>
                          {goal.status === 'ACTIVE' && (
                            <div className="gs-actions">
                              <input
                                type="number"
                                className="form-input gs-progress-input"
                                aria-label="Unidades a registrar"
                                min={1}
                                max={remaining}
                                value={progressInput[goal.id] ?? 1}
                                onChange={(e) =>
                                  setProgressInput((prev) => ({
                                    ...prev,
                                    [goal.id]: Math.min(remaining, Math.max(1, Number(e.target.value) || 1)),
                                  }))
                                }
                              />
                              <button
                                type="button"
                                className="btn btn-secondary btn-sm"
                                disabled={busyId === goal.id}
                                onClick={() => handleProgress(goal)}
                              >
                                Registrar
                              </button>
                            </div>
                          )}
                          {goal.status !== 'ARCHIVED' && (
                            <button
                              type="button"
                              className="btn btn-ghost-danger btn-sm"
                              disabled={busyId === goal.id}
                              onClick={() => handleArchiveGoal(goal)}
                            >
                              Arquivar
                            </button>
                          )}
                        </div>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>

            {/* ---- Sessões ---- */}
            <section className="card" aria-labelledby="sessions-heading">
              <div className="section-heading">
                <div>
                  <h2 id="sessions-heading">Sessões de estudo</h2>
                  <p className="subtitle">Planeje, inicie e conclua. O tempo real é medido entre início e fim.</p>
                </div>
              </div>

              <form className="gs-form" onSubmit={handleCreateSession}>
                <div className="gs-form-row">
                  <div className="form-field gs-number-field">
                    <label htmlFor="session-minutes">Minutos</label>
                    <input
                      id="session-minutes"
                      type="number"
                      className="form-input"
                      min={5}
                      max={720}
                      value={sessionForm.plannedMinutes}
                      onChange={(e) =>
                        setSessionForm({
                          ...sessionForm,
                          plannedMinutes: Math.min(720, Math.max(5, Number(e.target.value) || 5)),
                        })
                      }
                    />
                  </div>
                  <div className="form-field flex-1">
                    <label htmlFor="session-notes">Foco da sessão (opcional)</label>
                    <input
                      id="session-notes"
                      className="form-input"
                      maxLength={2000}
                      placeholder="Ex.: revisar herança e polimorfismo"
                      value={sessionForm.notes}
                      onChange={(e) => setSessionForm({ ...sessionForm, notes: e.target.value })}
                    />
                  </div>
                </div>
                <div className="form-actions">
                  <button type="submit" className="btn btn-primary btn-sm" disabled={busyId === 'new-session'}>
                    {busyId === 'new-session' ? 'Planejando…' : 'Planejar sessão'}
                  </button>
                </div>
              </form>

              {loading && sessions.length === 0 ? (
                <p className="text-muted text-sm">Carregando sessões…</p>
              ) : sessions.length === 0 ? (
                <p className="empty-subnotice">Nenhuma sessão registrada neste bloco ainda.</p>
              ) : (
                <ul className="gs-list">
                  {sessions.map((session) => {
                    const status = sessionStatusLabel[session.status]
                    const minutes = studiedMinutes(session, now)
                    return (
                      <li key={session.id} className={`gs-item ${session.status === 'CANCELLED' ? 'is-muted' : ''}`}>
                        <div className="gs-item-header">
                          <strong className="gs-item-title">
                            <ClockIcon className="w-4 h-4" aria-hidden="true" /> {session.plannedMinutes} min planejados
                          </strong>
                          <span className={`badge ${status.className}`}>{status.label}</span>
                        </div>
                        {session.notes && <p className="gs-item-desc">{session.notes}</p>}
                        {session.status === 'IN_PROGRESS' && (
                          <p className="gs-timer" role="timer" aria-label="Tempo de estudo da sessão em andamento">
                            <span className="gs-timer-value">{formatElapsed(session, now)}</span>
                            <span className="gs-timer-target">de {session.plannedMinutes} min planejados</span>
                          </p>
                        )}
                        <div className="gs-item-footer">
                          <span className="text-xs text-secondary">
                            {session.status === 'PLANNED' && `Criada em ${formatDateTime(session.createdAt)}`}
                            {session.status === 'IN_PROGRESS' &&
                              `Iniciada em ${formatDateTime(session.startedAt)} · ${minutes} min até agora`}
                            {session.status === 'COMPLETED' && `${minutes} min estudados · ${formatDateTime(session.endedAt)}`}
                            {session.status === 'CANCELLED' && `Cancelada em ${formatDateTime(session.endedAt)}`}
                          </span>
                          <div className="gs-actions">
                            {session.status === 'PLANNED' && (
                              <button
                                type="button"
                                className="btn btn-primary btn-sm"
                                disabled={busyId === session.id}
                                onClick={() => handleSessionAction(session, 'start')}
                              >
                                Iniciar
                              </button>
                            )}
                            {session.status === 'IN_PROGRESS' && (
                              <button
                                type="button"
                                className="btn btn-primary btn-sm"
                                disabled={busyId === session.id}
                                onClick={() => handleSessionAction(session, 'complete')}
                              >
                                <CheckCircleIcon className="w-4 h-4" aria-hidden="true" /> Concluir
                              </button>
                            )}
                            {(session.status === 'PLANNED' || session.status === 'IN_PROGRESS') && (
                              <button
                                type="button"
                                className="btn btn-ghost-danger btn-sm"
                                disabled={busyId === session.id}
                                onClick={() => handleSessionAction(session, 'cancel')}
                              >
                                Cancelar
                              </button>
                            )}
                          </div>
                        </div>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  )
}
