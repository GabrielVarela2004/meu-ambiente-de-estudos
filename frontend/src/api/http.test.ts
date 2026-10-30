import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request } from './http'
import { jsonResponse, mockFetch } from '../test/fetchMock'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('request', () => {
  it('devolve o JSON da resposta e envia Content-Type JSON', async () => {
    const { calls } = mockFetch(() => jsonResponse({ id: '1', title: 'Java' }))

    const data = await request<{ id: string; title: string }>('/api/study-blocks/1')

    expect(data).toEqual({ id: '1', title: 'Java' })
    expect(calls[0].url).toBe('/api/study-blocks/1')
    expect(calls[0].headers).toMatchObject({ 'Content-Type': 'application/json' })
  })

  it('devolve undefined em 204 sem tentar ler o corpo', async () => {
    mockFetch(() => new Response(null, { status: 204 }))

    await expect(request('/api/study-blocks/1', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('lança ApiError com o campo message do erro padronizado do backend', async () => {
    mockFetch(() =>
      jsonResponse(
        { status: 404, error: 'Não encontrado', message: 'Bloco de estudo não encontrado.', fields: null },
        404,
      ),
    )

    const error = await request('/api/study-blocks/x').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(404)
    expect((error as ApiError).message).toBe('Bloco de estudo não encontrado.')
  })

  it('usa uma mensagem padrão em português quando o erro vem sem corpo', async () => {
    mockFetch(() => new Response('', { status: 503 }))

    const error = (await request('/api/chat').catch((e: unknown) => e)) as ApiError

    expect(error.status).toBe(503)
    expect(error.message).toBe('O serviço de IA está temporariamente indisponível. Verifique as credenciais no backend.')
  })

  it('não define Content-Type ao enviar FormData (o navegador põe o boundary)', async () => {
    const { calls } = mockFetch(() => jsonResponse({}))

    await request('/api/upload', { method: 'POST', body: new FormData() })

    expect(calls[0].headers).not.toHaveProperty('Content-Type')
  })
})
