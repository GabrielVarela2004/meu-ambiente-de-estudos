import { request } from './http'

/** Corpo de POST /questions/{id}/answers: alternativa (objetivas) ou texto (discursivas). */
export type QuestionAnswerPayload = { optionId?: string; discursiveAnswer?: string }

export function answerQuestion<T>(blockId: string, questionId: string, payload: QuestionAnswerPayload): Promise<T> {
  return request<T>(`/api/study-blocks/${blockId}/questions/${questionId}/answers`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/** Adia a próxima revisão da questão em N dias (GlobalReviewController). */
export function rescheduleQuestionReview<T>(questionId: string, days: number): Promise<T> {
  return request<T>(`/api/reviews/questions/${questionId}/reschedule?days=${days}`, { method: 'POST' })
}

/** Classificação da resposta (questões abertas exibem em português: CORRETA, PARCIAL, INCORRETA, PENDENTE). */
export type AnswerClassification = 'CORRECT' | 'PARTIAL' | 'INCORRECT' | 'PENDING_EVALUATION' | 'UNGRADABLE'

export type EvaluationRigor = 'RIGOROSO' | 'MODERADO'

/** Feedback de uma questão aberta: só existe depois da resposta (a resposta-modelo nunca vem antes). */
export type DiscursiveFeedback = {
  attemptId: string
  classification: AnswerClassification
  scorePercent: number | null
  criteriaScores: Record<string, number>
  correctPoints: string[]
  missingEssentialConcepts: string[]
  missingTerms: string[]
  errors: string[]
  improvementTip: string | null
  modelAnswer: string | null
  sourceExcerpt: string | null
  sourceTimestamp: string | null
  sourceMaterialId: string | null
  sourceMaterialTitle: string | null
  sourceVideoId: string | null
  rigorLevel: EvaluationRigor | null
  pending: boolean
  pendingMessage: string | null
}

/** Resposta de POST …/answers e …/evaluate. `correct` só é verdadeiro para CORRETA (PARCIAL não é acerto). */
export type AnswerResult = {
  correct: boolean
  explanation: string | null
  classification: AnswerClassification
  scorePercent: number | null
  feedback: DiscursiveFeedback | null
}

/** Reavalia uma tentativa que ficou pendente por falta de IA. */
export function reevaluateAttempt(blockId: string, questionId: string, attemptId: string): Promise<AnswerResult> {
  return request<AnswerResult>(
    `/api/study-blocks/${blockId}/questions/${questionId}/attempts/${attemptId}/evaluate`,
    { method: 'POST' },
  )
}

export type PendingAttempt = { attemptId: string; answer: string; answeredAt: string }

export function listPendingAttempts(blockId: string, questionId: string): Promise<PendingAttempt[]> {
  return request<PendingAttempt[]>(`/api/study-blocks/${blockId}/questions/${questionId}/attempts/pending`)
}

/** Configurações › Correção das questões abertas. */
export function getEvaluationSettings(): Promise<{ rigorLevel: EvaluationRigor }> {
  return request<{ rigorLevel: EvaluationRigor }>('/api/settings/evaluation')
}

export function updateEvaluationSettings(rigorLevel: EvaluationRigor): Promise<{ rigorLevel: EvaluationRigor }> {
  return request<{ rigorLevel: EvaluationRigor }>('/api/settings/evaluation', {
    method: 'PUT',
    body: JSON.stringify({ rigorLevel }),
  })
}
