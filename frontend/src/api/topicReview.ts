import { request } from './http'

/** Decisão do usuário sobre um tópico sugerido. */
export type TopicReviewState = 'PENDING' | 'APPROVED' | 'REJECTED' | 'FEEDBACK'

/** Tópico sugerido pela extração, aguardando validação (aba Mapeamento de Fontes). */
export type TopicSuggestion = {
  id: string
  parentTopicId: string | null
  /** Título do tópico principal já oficial, quando a sugestão é um subtópico dele. */
  officialParentTitle: string | null
  title: string
  /** Título anterior à revisão com IA ou à renomeação ("antes → depois"). */
  previousTitle: string | null
  description: string | null
  supportingExcerpt: string | null
  sourceMaterialId: string | null
  sourceMaterialTitle: string | null
  sourceVideoId: string | null
  relevanceScore: number | null
  relevanceReason: string | null
  reviewRound: number
  reviewState: TopicReviewState
  userFeedback: string | null
}

export type TopicReviewSummary = {
  total: number
  approved: number
  rejected: number
  withFeedback: number
  undecided: number
  round: number
  /** Aprovações na primeira rodada sobre todas as decisões da primeira rodada (precisão da extração). */
  firstRoundApprovalPercent: number | null
}

export type TopicReviewBoard = { items: TopicSuggestion[]; summary: TopicReviewSummary }

export type PublishResult = { published: number; archived: number; waitingForParent: number }

const base = (blockId: string) => `/api/study-blocks/${blockId}/topics/review`

export function getTopicReview(blockId: string): Promise<TopicReviewBoard> {
  return request<TopicReviewBoard>(base(blockId))
}

export function reviewTopic(
  blockId: string,
  topicId: string,
  payload: { action: Exclude<TopicReviewState, 'PENDING'>; feedback?: string; title?: string },
): Promise<TopicSuggestion> {
  return request<TopicSuggestion>(`${base(blockId)}/${topicId}`, { method: 'POST', body: JSON.stringify(payload) })
}

export function approveRelevantTopics(blockId: string, minRelevance: number): Promise<{ count: number }> {
  return request<{ count: number }>(`${base(blockId)}/approve-relevant`, {
    method: 'POST',
    body: JSON.stringify({ minRelevance }),
  })
}

export function refineTopics(blockId: string): Promise<{ count: number }> {
  return request<{ count: number }>(`${base(blockId)}/refine`, { method: 'POST' })
}

export function publishTopics(blockId: string): Promise<PublishResult> {
  return request<PublishResult>(`${base(blockId)}/publish`, { method: 'POST' })
}
