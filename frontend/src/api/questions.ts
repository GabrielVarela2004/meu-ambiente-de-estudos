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
