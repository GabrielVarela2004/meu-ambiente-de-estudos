import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AnswerResult } from '../api/questions'
import { jsonResponse, mockFetch } from '../test/fetchMock'
import { TheoreticalQuestionsPage } from './TheoreticalQuestionsPage'
import type { StudyQuestion } from './TheoreticalQuestionsPage'
import type { StudyBlock } from './StudyBlocksPage'

const MODEL_ANSWER = 'O HashSet usa hashCode para achar o balde e equals para comparar os objetos.'
const EXCERPT = 'usa hashCode e equals para comparar os objetos antes de inserir'

const block: StudyBlock = {
  id: 'b1',
  title: 'Coleções',
  description: null,
  learningObjective: null,
  status: 'ACTIVE',
  updatedAt: '2026-10-30T10:00:00Z',
}

// Como o backend lista uma questão aberta: sem resposta esperada nem explicação antes da resposta.
const question: StudyQuestion = {
  id: 'q1',
  studyBlockId: 'b1',
  materialTitle: 'Apostila de coleções',
  sourceExcerpt: EXCERPT,
  prompt: 'Explique por que o HashSet não aceita elementos duplicados.',
  explanation: null,
  difficulty: 'INTERMEDIATE',
  format: 'DISCURSIVE',
  expectedAnswer: null,
  options: [],
  status: 'ACTIVE',
  nextReviewAt: '2026-10-30T10:00:00Z',
  reviewIntervalDays: 1,
  correctStreak: 0,
}

const partialAnswer: AnswerResult = {
  correct: false,
  explanation: 'PARCIAL — nota 68/100.',
  classification: 'PARTIAL',
  scorePercent: 68,
  feedback: {
    attemptId: 'a1',
    classification: 'PARTIAL',
    scorePercent: 68,
    criteriaScores: { conceptualAccuracy: 80, essentialCoverage: 55 },
    correctPoints: ['Citou o hashCode'],
    missingEssentialConcepts: ['equals compara os objetos'],
    missingTerms: ['balde'],
    errors: [],
    improvementTip: 'Explique o papel do equals.',
    modelAnswer: MODEL_ANSWER,
    sourceExcerpt: EXCERPT,
    sourceTimestamp: null,
    sourceMaterialId: 'm1',
    sourceMaterialTitle: 'Apostila de coleções',
    sourceVideoId: null,
    rigorLevel: 'RIGOROSO',
    pending: false,
    pendingMessage: null,
  },
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('TheoreticalQuestionsPage — questão aberta', () => {
  it('só mostra o que faltou, a resposta-modelo e o trecho depois de responder', async () => {
    const { calls } = mockFetch((call) =>
      call.method === 'POST' ? jsonResponse(partialAnswer) : jsonResponse([question]),
    )
    render(<TheoreticalQuestionsPage mode="block" blocks={[block]} selectedBlockId="b1" onSelectBlock={() => {}} />)

    expect(await screen.findByText(question.prompt)).toBeInTheDocument()
    // Antes da resposta: nada de gabarito, rubrica ou trecho da fonte.
    expect(screen.queryByText(MODEL_ANSWER)).not.toBeInTheDocument()
    expect(screen.queryByText('Conceitos essenciais que faltaram')).not.toBeInTheDocument()
    expect(screen.queryByText(/Ver Trecho da Fonte/)).not.toBeInTheDocument()

    await userEvent.type(screen.getByRole('textbox'), 'Ele usa o hashCode.')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Resposta' }))

    expect(await screen.findByText('PARCIAL')).toBeInTheDocument()
    expect(screen.getByText('68/100')).toBeInTheDocument()
    expect(screen.getByText('Conceitos essenciais que faltaram')).toBeInTheDocument()
    expect(screen.getByText('equals compara os objetos')).toBeInTheDocument()
    expect(screen.getByText('balde')).toBeInTheDocument()
    expect(screen.getByText(MODEL_ANSWER)).toBeInTheDocument()
    expect(screen.getByText(EXCERPT)).toBeInTheDocument()
    expect(screen.getByText(/Explique o papel do equals/)).toBeInTheDocument()

    const answerCall = calls.find((c) => c.method === 'POST')
    expect(answerCall?.url).toBe('/api/study-blocks/b1/questions/q1/answers')
    expect(JSON.parse(String(answerCall?.body))).toEqual({ discursiveAnswer: 'Ele usa o hashCode.' })
  })

  it('feedback de erro mostra correção, resposta correta, explicação e erros comuns', async () => {
    const wrong: AnswerResult = {
      ...partialAnswer,
      classification: 'INCORRECT',
      feedback: {
        ...partialAnswer.feedback!,
        classification: 'INCORRECT',
        errors: ['Disse que a inserção lança exceção'],
        explanation: 'Colisões vão para o mesmo bucket.',
        commonMistakes: ['Se você achou que a inserção falha, desconhece o tratamento de colisões.'],
      },
    }
    mockFetch((call) => (call.method === 'POST' ? jsonResponse(wrong) : jsonResponse([question])))
    render(<TheoreticalQuestionsPage mode="block" blocks={[block]} selectedBlockId="b1" onSelectBlock={() => {}} />)

    await userEvent.type(await screen.findByRole('textbox'), 'Lança exceção.')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Resposta' }))

    expect(await screen.findByText(/^Correção: veja onde/)).toBeInTheDocument()
    expect(screen.getByText('Disse que a inserção lança exceção')).toBeInTheDocument()
    expect(screen.getByText('Resposta correta')).toBeInTheDocument()
    expect(screen.getByText('Colisões vão para o mesmo bucket.')).toBeInTheDocument()
    expect(screen.getByText(/desconhece o tratamento de colisões/)).toBeInTheDocument()
  })

  it('acerto mostra parabéns', async () => {
    const right: AnswerResult = {
      ...partialAnswer,
      correct: true,
      classification: 'CORRECT',
      feedback: { ...partialAnswer.feedback!, classification: 'CORRECT', scorePercent: 92 },
    }
    mockFetch((call) => (call.method === 'POST' ? jsonResponse(right) : jsonResponse([question])))
    render(<TheoreticalQuestionsPage mode="block" blocks={[block]} selectedBlockId="b1" onSelectBlock={() => {}} />)

    await userEvent.type(await screen.findByRole('textbox'), 'Usa hashCode e equals.')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Resposta' }))

    expect(await screen.findByText(/Parabéns, você acertou/)).toBeInTheDocument()
  })

  it('modo estudo mostra a resposta sem enviar tentativa', async () => {
    const { calls } = mockFetch((call) =>
      call.url.endsWith('/study-view')
        ? jsonResponse({
            questionId: 'q1',
            prompt: question.prompt,
            answer: MODEL_ANSWER,
            explanation: 'Usa hashCode e equals.',
            essentialConcepts: ['equals compara os objetos'],
            commonMistakes: ['Se você achou que o HashSet ordena, confundiu com o TreeSet.'],
            sourceExcerpt: EXCERPT,
            sourceTimestamp: null,
            sourceMaterialId: 'm1',
            sourceMaterialTitle: 'Apostila de coleções',
            sourceVideoId: null,
          })
        : jsonResponse([question]),
    )
    render(<TheoreticalQuestionsPage mode="block" blocks={[block]} selectedBlockId="b1" onSelectBlock={() => {}} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Ver resposta (modo estudo)' }))

    expect(await screen.findByText(MODEL_ANSWER)).toBeInTheDocument()
    expect(screen.getByText(/não conta na pontuação/)).toBeInTheDocument()
    expect(screen.getByText(/confundiu com o TreeSet/)).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'POST')).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: 'Ocultar resposta' }))
    await waitFor(() => expect(screen.queryByText(MODEL_ANSWER)).not.toBeInTheDocument())
  })

  it('"Responder de novo" limpa o feedback e reabre o campo para outra tentativa', async () => {
    mockFetch((call) => (call.method === 'POST' ? jsonResponse(partialAnswer) : jsonResponse([question])))
    render(<TheoreticalQuestionsPage mode="block" blocks={[block]} selectedBlockId="b1" onSelectBlock={() => {}} />)

    await userEvent.type(await screen.findByRole('textbox'), 'Primeira resposta.')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Resposta' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Responder de novo' }))

    await waitFor(() => expect(screen.queryByText(MODEL_ANSWER)).not.toBeInTheDocument())
    expect(screen.getByRole('textbox')).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Confirmar Resposta' })).toBeEnabled()
  })

  it('resposta pendente (sem IA) avisa e oferece reavaliar, sem mostrar a resposta-modelo', async () => {
    const pending: AnswerResult = {
      correct: false,
      explanation: 'pendente',
      classification: 'PENDING_EVALUATION',
      scorePercent: null,
      feedback: {
        ...partialAnswer.feedback!,
        classification: 'PENDING_EVALUATION',
        scorePercent: null,
        modelAnswer: null,
        pending: true,
        pendingMessage: 'Resposta registrada, mas ainda não corrigida.',
      },
    }
    mockFetch((call) => (call.method === 'POST' ? jsonResponse(pending) : jsonResponse([question])))
    render(<TheoreticalQuestionsPage mode="block" blocks={[block]} selectedBlockId="b1" onSelectBlock={() => {}} />)

    await userEvent.type(await screen.findByRole('textbox'), 'Usa hashCode e equals.')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Resposta' }))

    expect(await screen.findByText('PENDENTE DE AVALIAÇÃO')).toBeInTheDocument()
    expect(screen.getByText('Resposta registrada, mas ainda não corrigida.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reavaliar agora' })).toBeInTheDocument()
    expect(screen.queryByText(MODEL_ANSWER)).not.toBeInTheDocument()
  })
})
