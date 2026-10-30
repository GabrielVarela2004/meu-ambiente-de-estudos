import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, mockFetch } from '../../test/fetchMock'
import { AiProvidersPanel } from './AiProvidersPanel'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('AiProvidersPanel — modelo por operação', () => {
  it('salva o modelo escolhido só para a operação', async () => {
    const gemini = {
      id: 'gemini',
      displayName: 'Google Gemini',
      keyConfigured: true,
      enabled: true,
      defaultModel: 'gemini-3.8-flash',
      modelOverride: null,
      effectiveModel: 'gemini-3.8-flash',
      suggestedModels: ['gemini-3.8-flash', 'gemini-3.5-flash-lite'],
      dailyRequestLimit: null,
      requestsToday: 0,
      unavailableReason: null,
    }
    const route = { operation: 'QUESTION_GENERATION', label: 'Geração de questões', providerId: 'gemini', fallbackEnabled: true, model: null }
    const { calls } = mockFetch((call) => {
      if (call.method === 'PUT') return jsonResponse({ ...route, model: 'gemini-3.5-flash-lite' })
      if (call.url.startsWith('/api/ai/usage')) return jsonResponse([])
      if (call.url === '/api/ai/cache') return jsonResponse({ enabled: true, ttlHours: 168, entries: 0, hits: 0, savedTokens: 0 })
      return jsonResponse({ providers: [gemini], routes: [route] })
    })
    render(<AiProvidersPanel />)

    const input = await screen.findByRole('combobox', { name: 'Modelo para Geração de questões' })
    expect(input).toHaveAttribute('placeholder', 'Do provedor: gemini-3.8-flash')
    await userEvent.type(input, 'gemini-3.5-flash-lite')
    await userEvent.tab()

    const put = calls.find((c) => c.method === 'PUT')
    expect(put?.url).toBe('/api/ai/routes/QUESTION_GENERATION')
    expect(JSON.parse(String(put?.body))).toEqual({
      providerId: 'gemini',
      fallbackEnabled: true,
      model: 'gemini-3.5-flash-lite',
    })
  })
})

describe('AiProvidersPanel — cache de respostas', () => {
  it('mostra o que o cache economizou e permite limpá-lo', async () => {
    let entries = 3
    const { calls } = mockFetch((call) => {
      if (call.url === '/api/ai/cache' && call.method === 'DELETE') {
        entries = 0
        return new Response(null, { status: 204 })
      }
      if (call.url === '/api/ai/cache') {
        return jsonResponse({ enabled: true, ttlHours: 168, entries, hits: entries * 2, savedTokens: entries * 1000 })
      }
      if (call.url.startsWith('/api/ai/usage')) return jsonResponse([])
      return jsonResponse({ providers: [], routes: [] })
    })
    render(<AiProvidersPanel />)

    expect(await screen.findByText(/3 respostas guardadas · 6 chamadas evitadas · 3\.000 tokens economizados/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Limpar cache' }))

    expect(await screen.findByText('Cache de respostas apagado.')).toBeInTheDocument()
    expect(screen.getByText(/0 respostas guardadas/)).toBeInTheDocument()
    expect(calls.some((c) => c.url === '/api/ai/cache' && c.method === 'DELETE')).toBe(true)
  })
})
