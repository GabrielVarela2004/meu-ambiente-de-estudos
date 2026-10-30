import { vi } from 'vitest'

export type RecordedCall = {
  url: string
  method: string
  body: BodyInit | null | undefined
  headers: HeadersInit | undefined
}

/** Substitui o fetch global: grava cada chamada e responde com o que `respond` devolver. */
export function mockFetch(respond: (call: RecordedCall) => Response = () => jsonResponse({})) {
  const calls: RecordedCall[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const call: RecordedCall = {
      url: String(input),
      method: init?.method ?? 'GET',
      body: init?.body,
      headers: init?.headers,
    }
    calls.push(call)
    return respond(call)
  })
  vi.stubGlobal('fetch', fetchMock)
  return { calls, fetchMock }
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
