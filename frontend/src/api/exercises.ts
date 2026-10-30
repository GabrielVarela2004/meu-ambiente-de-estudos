import { request } from './http'

/** Corpo de AttemptRequest em PracticalExerciseController. */
export type ExerciseAttemptPayload = { answer: string; selfRating: number; feedback?: string }

export function submitExerciseAttempt<T>(blockId: string, exerciseId: string, payload: ExerciseAttemptPayload): Promise<T> {
  return request<T>(`/api/study-blocks/${blockId}/exercises/${exerciseId}/attempts`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
