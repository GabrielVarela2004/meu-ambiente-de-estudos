import { request } from './http'

// Rotas de StudyGoalController (/api/study-blocks/{blockId}/goals) e
// StudySessionController (/api/study-blocks/{blockId}/sessions).

export type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'
export type SessionStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

/** GoalResponse */
export type StudyGoal = {
  id: string
  title: string
  description: string | null
  targetUnits: number
  completedUnits: number
  status: GoalStatus
  createdAt: string
  updatedAt: string
}

/** StudySessionResponse */
export type StudySession = {
  id: string
  plannedMinutes: number
  notes: string | null
  status: SessionStatus
  startedAt: string | null
  endedAt: string | null
  createdAt: string
  updatedAt: string
}

/** GoalRequest: título obrigatório (até 160), descrição opcional (até 2000), meta de 1 a 100.000 unidades. */
export type GoalInput = { title: string; description: string | null; targetUnits: number }

/** StudySessionRequest: de 5 a 720 minutos planejados, notas opcionais (até 2000). */
export type SessionInput = { plannedMinutes: number; notes: string | null }

export type SessionAction = 'start' | 'complete' | 'cancel'

const goals = (blockId: string) => `/api/study-blocks/${blockId}/goals`
const sessions = (blockId: string) => `/api/study-blocks/${blockId}/sessions`

export function listGoals(blockId: string): Promise<StudyGoal[]> {
  return request<StudyGoal[]>(goals(blockId))
}

export function createGoal(blockId: string, input: GoalInput): Promise<StudyGoal> {
  return request<StudyGoal>(goals(blockId), { method: 'POST', body: JSON.stringify(input) })
}

/** Registra unidades concluídas da meta (ProgressRequest: 1 a 100.000). */
export function addGoalProgress(blockId: string, goalId: string, units: number): Promise<StudyGoal> {
  return request<StudyGoal>(`${goals(blockId)}/${goalId}/progress`, {
    method: 'POST',
    body: JSON.stringify({ units }),
  })
}

/** Arquiva a meta (DELETE responde 204, sem corpo). */
export function archiveGoal(blockId: string, goalId: string): Promise<void> {
  return request<void>(`${goals(blockId)}/${goalId}`, { method: 'DELETE' })
}

export function listSessions(blockId: string): Promise<StudySession[]> {
  return request<StudySession[]>(sessions(blockId))
}

export function planSession(blockId: string, input: SessionInput): Promise<StudySession> {
  return request<StudySession>(sessions(blockId), { method: 'POST', body: JSON.stringify(input) })
}

/** Inicia, conclui ou cancela uma sessão. */
export function changeSessionState(blockId: string, sessionId: string, action: SessionAction): Promise<StudySession> {
  return request<StudySession>(`${sessions(blockId)}/${sessionId}/${action}`, { method: 'POST' })
}
