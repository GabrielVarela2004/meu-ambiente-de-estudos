import { request } from './http'

/** Arquivo do projeto do exercício; o caminho é relativo à pasta src (ex.: "loja/Pedido.java"). */
export type ExerciseFile = { path: string; content: string }

/**
 * Corpo de AttemptRequest em PracticalExerciseController; selfRating é dispensado quando há correção automática.
 * Com `files`, o projeto inteiro é corrigido; só `answer` mantém o formato antigo (texto único).
 */
export type ExerciseAttemptPayload = {
  answer?: string
  files?: ExerciseFile[]
  selfRating: number | null
  feedback?: string
}

export function submitExerciseAttempt<T>(blockId: string, exerciseId: string, payload: ExerciseAttemptPayload): Promise<T> {
  return request<T>(`/api/study-blocks/${blockId}/exercises/${exerciseId}/attempts`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export type CompileDiagnostic = { file: string; line: number; column: number; message: string }

/** CodeExecutionResult do backend: resultado do "Executar" (não é tentativa e não usa IA). */
export type CodeExecutionResult = {
  status: 'OK' | 'COMPILATION_ERROR' | 'RUNTIME_ERROR' | 'TIMEOUT' | 'NO_MAIN' | 'UNAVAILABLE'
  message: string | null
  mainClass: string | null
  exitCode: number | null
  timeMillis: number
  stdout: string
  stderr: string
  compileDiagnostics: CompileDiagnostic[]
}

const exercisePath = (blockId: string, exerciseId: string) => `/api/study-blocks/${blockId}/exercises/${exerciseId}`

/** POST …/run: compila e executa o main com a entrada (stdin) opcional. */
export function runExercise(blockId: string, exerciseId: string, files: ExerciseFile[], stdin: string): Promise<CodeExecutionResult> {
  return request<CodeExecutionResult>(`${exercisePath(blockId, exerciseId)}/run`, {
    method: 'POST',
    body: JSON.stringify({ files, stdin: stdin === '' ? null : stdin }),
  })
}

export type ExerciseDraft = { files: ExerciseFile[]; updatedAt: string }

/** GET …/draft: o rascunho salvo, ou null quando ainda não existe (204). */
export async function getExerciseDraft(blockId: string, exerciseId: string): Promise<ExerciseDraft | null> {
  const draft = await request<ExerciseDraft | undefined>(`${exercisePath(blockId, exerciseId)}/draft`)
  return draft ?? null
}

export function saveExerciseDraft(blockId: string, exerciseId: string, files: ExerciseFile[]): Promise<ExerciseDraft> {
  return request<ExerciseDraft>(`${exercisePath(blockId, exerciseId)}/draft`, {
    method: 'PUT',
    body: JSON.stringify({ files }),
  })
}

/** DELETE …/draft: "Restaurar código inicial". */
export function deleteExerciseDraft(blockId: string, exerciseId: string): Promise<void> {
  return request<void>(`${exercisePath(blockId, exerciseId)}/draft`, { method: 'DELETE' })
}
