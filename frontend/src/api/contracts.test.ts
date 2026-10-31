import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, mockFetch } from '../test/fetchMock'
import { runCareerAgent, toggleCareerSource } from './career'
import { deleteExerciseDraft, getExerciseDraft, runExercise, saveExerciseDraft, submitExerciseAttempt } from './exercises'
import { addGoalProgress, archiveGoal, changeSessionState, createGoal, listGoals, listSessions, planSession } from './goals'
import { importMaterialFile, importYoutubeVideo, previewYoutube, reprocessMaterial, toggleMaterialChat } from './materials'
import {
  answerQuestion,
  getEvaluationMonitoring,
  getEvaluationSettings,
  listPendingAttempts,
  reevaluateAttempt,
  rescheduleQuestionReview,
  runConsistencyCheck,
  updateEvaluationSettings,
} from './questions'

// Testes de contrato: cada função tipada chama a rota, o método e o corpo que os controllers do backend expõem.

afterEach(() => {
  vi.unstubAllGlobals()
})

function json(body: BodyInit | null | undefined) {
  return JSON.parse(String(body))
}

describe('api/materials (StudyMaterialController)', () => {
  it('toggleMaterialChat → PATCH …/materials/{id}/toggle-chat', async () => {
    const { calls } = mockFetch()
    await toggleMaterialChat('b1', 'm1')
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/materials/m1/toggle-chat', method: 'PATCH' })
  })

  it('importMaterialFile → POST …/materials/import com o arquivo no campo "file"', async () => {
    const { calls } = mockFetch()
    const file = new File(['conteúdo'], 'aula.pdf', { type: 'application/pdf' })
    await importMaterialFile('b1', file)
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/materials/import', method: 'POST' })
    expect((calls[0].body as FormData).get('file')).toBeInstanceOf(File)
    expect(((calls[0].body as FormData).get('file') as File).name).toBe('aula.pdf')
  })

  it('previewYoutube → GET …/materials/youtube/preview com o link codificado', async () => {
    const { calls } = mockFetch()
    await previewYoutube('b1', 'https://youtu.be/dQw4w9WgXcQ?si=abc')
    expect(calls[0]).toMatchObject({
      url: '/api/study-blocks/b1/materials/youtube/preview?url=https%3A%2F%2Fyoutu.be%2FdQw4w9WgXcQ%3Fsi%3Dabc',
      method: 'GET',
    })
  })

  it('importYoutubeVideo → POST …/materials/youtube com url, título e chat', async () => {
    const { calls } = mockFetch()
    await importYoutubeVideo('b1', { url: 'https://youtu.be/x', title: 'Aula 1', enabledForChat: false })
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/materials/youtube', method: 'POST' })
    expect(json(calls[0].body)).toEqual({ url: 'https://youtu.be/x', title: 'Aula 1', enabledForChat: false })
  })

  it('reprocessMaterial → POST …/materials/{id}/reprocess', async () => {
    const { calls } = mockFetch()
    await reprocessMaterial('b1', 'm1')
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/materials/m1/reprocess', method: 'POST' })
  })
})

describe('api/questions (StudyQuestionController, GlobalReviewController, EvaluationPreferenceController)', () => {
  it('answerQuestion → POST …/questions/{id}/answers com discursiveAnswer', async () => {
    const { calls } = mockFetch()
    await answerQuestion('b1', 'q1', { discursiveAnswer: 'Minha resposta' })
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/questions/q1/answers', method: 'POST' })
    expect(json(calls[0].body)).toEqual({ discursiveAnswer: 'Minha resposta' })
  })

  it('rescheduleQuestionReview → POST /api/reviews/questions/{id}/reschedule?days=N', async () => {
    const { calls } = mockFetch()
    await rescheduleQuestionReview('q1', 3)
    expect(calls[0]).toMatchObject({ url: '/api/reviews/questions/q1/reschedule?days=3', method: 'POST' })
  })

  it('reevaluateAttempt → POST …/questions/{id}/attempts/{attemptId}/evaluate', async () => {
    const { calls } = mockFetch()
    await reevaluateAttempt('b1', 'q1', 'a1')
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/questions/q1/attempts/a1/evaluate', method: 'POST' })
  })

  it('listPendingAttempts → GET …/questions/{id}/attempts/pending', async () => {
    const { calls } = mockFetch(() => jsonResponse([]))
    await listPendingAttempts('b1', 'q1')
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/questions/q1/attempts/pending', method: 'GET' })
  })

  it('getEvaluationSettings e updateEvaluationSettings → /api/settings/evaluation', async () => {
    const { calls } = mockFetch(() => jsonResponse({ rigorLevel: 'MODERADO' }))
    await getEvaluationSettings()
    await updateEvaluationSettings('MODERADO')
    expect(calls[0]).toMatchObject({ url: '/api/settings/evaluation', method: 'GET' })
    expect(calls[1]).toMatchObject({ url: '/api/settings/evaluation', method: 'PUT' })
    expect(json(calls[1].body)).toEqual({ rigorLevel: 'MODERADO' })
  })

  it('getEvaluationMonitoring e runConsistencyCheck → /api/settings/evaluation/monitoring', async () => {
    const { calls } = mockFetch(() =>
      jsonResponse({ graded: 0, pending: 0, correct: 0, partial: 0, incorrect: 0, averageScore: 0, averageCriteria: {}, blocks: [] }),
    )
    await getEvaluationMonitoring()
    await runConsistencyCheck(10)
    expect(calls[0]).toMatchObject({ url: '/api/settings/evaluation/monitoring', method: 'GET' })
    expect(calls[1]).toMatchObject({
      url: '/api/settings/evaluation/monitoring/consistency-check?limit=10',
      method: 'POST',
    })
  })
})

describe('api/exercises (PracticalExerciseController)', () => {
  it('submitExerciseAttempt → POST …/exercises/{id}/attempts com answer (não codeAnswer)', async () => {
    const { calls } = mockFetch()
    await submitExerciseAttempt('b1', 'e1', { answer: 'int x = 1;', selfRating: 4 })
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/exercises/e1/attempts', method: 'POST' })
    expect(json(calls[0].body)).toEqual({ answer: 'int x = 1;', selfRating: 4 })
  })

  const project = [
    { path: 'Main.java', content: 'public class Main {}' },
    { path: 'loja/Status.java', content: 'package loja; public enum Status { A }' },
  ]

  it('submitExerciseAttempt com files → corpo com o projeto inteiro', async () => {
    const { calls } = mockFetch()
    await submitExerciseAttempt('b1', 'e1', { files: project, selfRating: null })
    expect(json(calls[0].body)).toEqual({ files: project, selfRating: null })
  })

  it('runExercise → POST …/exercises/{id}/run com files e stdin (vazio vira null)', async () => {
    const { calls } = mockFetch()
    await runExercise('b1', 'e1', project, 'Ana\n')
    await runExercise('b1', 'e1', project, '')
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/exercises/e1/run', method: 'POST' })
    expect(json(calls[0].body)).toEqual({ files: project, stdin: 'Ana\n' })
    expect(json(calls[1].body)).toEqual({ files: project, stdin: null })
  })

  it('rascunho → GET (204 vira null), PUT com files e DELETE …/draft', async () => {
    const { calls } = mockFetch((call) => (call.method === 'GET' ? new Response(null, { status: 204 }) : jsonResponse({ files: project })))
    await expect(getExerciseDraft('b1', 'e1')).resolves.toBeNull()
    await saveExerciseDraft('b1', 'e1', project)
    await deleteExerciseDraft('b1', 'e1')
    expect(calls.map((c) => [c.method, c.url])).toEqual([
      ['GET', '/api/study-blocks/b1/exercises/e1/draft'],
      ['PUT', '/api/study-blocks/b1/exercises/e1/draft'],
      ['DELETE', '/api/study-blocks/b1/exercises/e1/draft'],
    ])
    expect(json(calls[1].body)).toEqual({ files: project })
  })
})

describe('api/career (CareerAgentController, CareerSourceController)', () => {
  it('runCareerAgent → POST /api/career/agent/run', async () => {
    const { calls } = mockFetch()
    await runCareerAgent()
    expect(calls[0]).toMatchObject({ url: '/api/career/agent/run', method: 'POST' })
  })

  it('toggleCareerSource → PATCH /api/career/sources/{id}/toggle com enabled', async () => {
    const { calls } = mockFetch()
    await toggleCareerSource('s1', false)
    expect(calls[0]).toMatchObject({ url: '/api/career/sources/s1/toggle', method: 'PATCH' })
    expect(json(calls[0].body)).toEqual({ enabled: false })
  })
})

describe('api/goals (StudyGoalController)', () => {
  it('listGoals → GET …/goals', async () => {
    const { calls } = mockFetch(() => jsonResponse([]))
    await listGoals('b1')
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/goals', method: 'GET' })
  })

  it('createGoal → POST …/goals com título, descrição e unidades', async () => {
    const { calls } = mockFetch()
    await createGoal('b1', { title: 'Ler 5 capítulos', description: null, targetUnits: 5 })
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/goals', method: 'POST' })
    expect(json(calls[0].body)).toEqual({ title: 'Ler 5 capítulos', description: null, targetUnits: 5 })
  })

  it('addGoalProgress → POST …/goals/{id}/progress com { units }', async () => {
    const { calls } = mockFetch()
    await addGoalProgress('b1', 'g1', 2)
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/goals/g1/progress', method: 'POST' })
    expect(json(calls[0].body)).toEqual({ units: 2 })
  })

  it('archiveGoal → DELETE …/goals/{id} e aceita 204 sem corpo', async () => {
    const { calls } = mockFetch(() => new Response(null, { status: 204 }))
    await expect(archiveGoal('b1', 'g1')).resolves.toBeUndefined()
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/goals/g1', method: 'DELETE' })
  })
})

describe('api/goals (StudySessionController)', () => {
  it('listSessions → GET …/sessions', async () => {
    const { calls } = mockFetch(() => jsonResponse([]))
    await listSessions('b1')
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/sessions', method: 'GET' })
  })

  it('planSession → POST …/sessions com minutos planejados e notas', async () => {
    const { calls } = mockFetch()
    await planSession('b1', { plannedMinutes: 25, notes: 'Revisar coleções' })
    expect(calls[0]).toMatchObject({ url: '/api/study-blocks/b1/sessions', method: 'POST' })
    expect(json(calls[0].body)).toEqual({ plannedMinutes: 25, notes: 'Revisar coleções' })
  })

  it.each(['start', 'complete', 'cancel'] as const)('changeSessionState(%s) → POST …/sessions/{id}/%s', async (action) => {
    const { calls } = mockFetch()
    await changeSessionState('b1', 's1', action)
    expect(calls[0]).toMatchObject({ url: `/api/study-blocks/b1/sessions/s1/${action}`, method: 'POST' })
    expect(calls[0].body).toBeUndefined()
  })
})
