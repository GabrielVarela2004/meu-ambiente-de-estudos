import { request } from './http'

/** Dispara uma varredura manual do agente de vagas (CareerAgentController). */
export function runCareerAgent<T = unknown>(): Promise<T> {
  return request<T>('/api/career/agent/run', { method: 'POST' })
}

/** Liga ou desliga uma fonte de vagas. */
export function toggleCareerSource<T = unknown>(sourceId: string, enabled: boolean): Promise<T> {
  return request<T>(`/api/career/sources/${sourceId}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ enabled }),
  })
}
