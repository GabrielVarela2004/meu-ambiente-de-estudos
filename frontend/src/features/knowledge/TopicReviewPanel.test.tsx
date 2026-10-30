import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { TopicReviewBoard, TopicSuggestion } from '../../api/topicReview'
import { jsonResponse, mockFetch } from '../../test/fetchMock'
import { TopicReviewPanel } from './TopicReviewPanel'

const suggestion = (overrides: Partial<TopicSuggestion>): TopicSuggestion => ({
  id: 't1',
  parentTopicId: null,
  officialParentTitle: null,
  title: 'Tipos primitivos',
  previousTitle: null,
  description: 'Valores simples da linguagem',
  supportingExcerpt: 'Tipos primitivos guardam valores simples',
  sourceMaterialId: 'm1',
  sourceMaterialTitle: 'Aula 1',
  sourceVideoId: null,
  relevanceScore: 92,
  relevanceReason: 'Base da linguagem',
  reviewRound: 1,
  reviewState: 'PENDING',
  userFeedback: null,
  ...overrides,
})

const board: TopicReviewBoard = {
  items: [
    suggestion({}),
    suggestion({ id: 't2', parentTopicId: 't1', title: 'int e long', relevanceScore: 70, relevanceReason: null }),
    suggestion({
      id: 't3',
      title: 'Classes abstratas em Java',
      previousTitle: 'Classes abstratas',
      reviewRound: 2,
      reviewState: 'FEEDBACK',
      userFeedback: 'Inclua métodos abstratos',
      relevanceScore: 55,
      relevanceReason: 'Conceito de POO citado na aula',
    }),
  ],
  summary: { total: 3, approved: 0, rejected: 0, withFeedback: 1, undecided: 2, round: 2, firstRoundApprovalPercent: 75 },
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('TopicReviewPanel', () => {
  it('mostra relevância, trecho, antes/depois e comentário de cada sugestão', async () => {
    mockFetch(() => jsonResponse(board))
    render(<TopicReviewPanel blockId="b1" reloadKey={0} onPublished={() => {}} />)

    expect(await screen.findByText('Sugestões para validar · Rodada 2')).toBeInTheDocument()
    expect(screen.getByText('Relevância 92')).toBeInTheDocument()
    expect(screen.getByText('Por que essa relevância: Base da linguagem')).toBeInTheDocument()
    // Cada sugestão mostra o trecho da fonte que a sustenta.
    expect(screen.getAllByText(/Tipos primitivos guardam valores simples/)).toHaveLength(3)
    expect(screen.getAllByText('Trecho de "Aula 1":')).toHaveLength(3)
    expect(screen.getByText('↳ int e long')).toBeInTheDocument()
    expect(screen.getByText('Antes: Classes abstratas')).toBeInTheDocument()
    expect(screen.getByText('Seu comentário: Inclua métodos abstratos')).toBeInTheDocument()
    expect(screen.getByText('Precisão da extração (aprovados na 1ª rodada): 75%')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Revisar com IA (1 com comentário)' })).toBeEnabled()
  })

  it('aprova pelo botão e pelo atalho A, e envia comentário', async () => {
    const { calls } = mockFetch((call) => (call.method === 'POST' ? jsonResponse(suggestion({})) : jsonResponse(board)))
    render(<TopicReviewPanel blockId="b1" reloadKey={0} onPublished={() => {}} />)

    const approveButtons = await screen.findAllByRole('button', { name: '✓ Aprovar' })
    await userEvent.click(approveButtons[0])
    await waitFor(() => expect(calls.some((c) => c.url === '/api/study-blocks/b1/topics/review/t1')).toBe(true))
    expect(JSON.parse(String(calls.find((c) => c.method === 'POST')?.body))).toEqual({ action: 'APPROVED' })

    // Atalho no card focado.
    screen.getByRole('article', { name: 'Sugestão: int e long' }).focus()
    await userEvent.keyboard('a')
    await waitFor(() => expect(calls.some((c) => c.url.endsWith('/review/t2'))).toBe(true))

    const commentButtons = screen.getAllByRole('button', { name: 'Comentar' })
    await userEvent.click(commentButtons[0])
    await userEvent.type(screen.getByLabelText('O que pode ser melhorado ou acrescentado?'), 'Separe int e long')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar comentário' }))
    await waitFor(() =>
      expect(calls.some((c) => c.method === 'POST' && String(c.body).includes('"action":"FEEDBACK"'))).toBe(true),
    )
  })

  it('oficializa e avisa a lista de tópicos oficiais', async () => {
    const onPublished = vi.fn()
    const decided = { ...board, summary: { ...board.summary, approved: 2, undecided: 0 } }
    mockFetch((call) =>
      call.url.endsWith('/publish')
        ? jsonResponse({ published: 2, archived: 1, waitingForParent: 0 })
        : jsonResponse(decided),
    )
    render(<TopicReviewPanel blockId="b1" reloadKey={0} onPublished={onPublished} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Oficializar 2 aprovado(s)' }))

    expect(await screen.findByText('2 tópico(s) oficializado(s), 1 excluído(s).')).toBeInTheDocument()
    expect(onPublished).toHaveBeenCalled()
  })

  it('não mostra nada quando não há sugestões', async () => {
    const { calls } = mockFetch(() => jsonResponse({ items: [], summary: { ...board.summary, total: 0 } }))
    const { container } = render(<TopicReviewPanel blockId="b1" reloadKey={0} onPublished={() => {}} />)
    await waitFor(() => expect(calls.length).toBe(1))
    expect(container).toBeEmptyDOMElement()
  })
})
