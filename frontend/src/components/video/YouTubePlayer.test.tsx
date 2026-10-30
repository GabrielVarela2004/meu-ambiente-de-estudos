import { createRef } from 'react'
import { act, render } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { YouTubePlayer } from './YouTubePlayer'
import type { YouTubePlayerHandle } from './YouTubePlayer'

// IFrame Player API simulada: nenhum script do YouTube é carregado no teste.
type PlayerOptions = {
  host: string
  videoId: string
  playerVars: { start: number }
  events: { onReady: () => void }
}

const seekTo = vi.fn()
const playVideo = vi.fn()
const destroy = vi.fn()
let lastOptions: PlayerOptions | null = null
const iframe = document.createElement('iframe')

beforeEach(() => {
  lastOptions = null
  window.YT = {
    Player: vi.fn(function (this: unknown, _element: HTMLElement, options: Record<string, unknown>) {
      lastOptions = options as unknown as PlayerOptions
      return { seekTo, playVideo, destroy, getIframe: () => iframe }
    }) as unknown as NonNullable<Window['YT']>['Player'],
  }
})

afterEach(() => {
  delete window.YT
})

/** Espera a microtarefa que resolve o carregamento da API. */
async function flush() {
  await act(async () => {
    await Promise.resolve()
  })
}

describe('YouTubePlayer', () => {
  it('cria o player em youtube-nocookie com o vídeo e o início pedidos', async () => {
    render(<YouTubePlayer videoId="dQw4w9WgXcQ" title="Aula de Streams" startSeconds={42} />)
    await flush()

    expect(lastOptions?.host).toBe('https://www.youtube-nocookie.com')
    expect(lastOptions?.videoId).toBe('dQw4w9WgXcQ')
    expect(lastOptions?.playerVars.start).toBe(42)
  })

  it('pula para o minuto pedido: espera o player ficar pronto e depois toca dali', async () => {
    const ref = createRef<YouTubePlayerHandle>()
    render(<YouTubePlayer ref={ref} videoId="dQw4w9WgXcQ" title="Aula" />)
    await flush()

    // Antes de pronto: o pedido fica guardado.
    act(() => ref.current?.seekTo(195))
    expect(seekTo).not.toHaveBeenCalled()

    act(() => lastOptions?.events.onReady())
    expect(seekTo).toHaveBeenCalledWith(195, true)
    expect(playVideo).toHaveBeenCalled()
    expect(iframe.title).toBe('Vídeo: Aula')

    // Já pronto: pula na hora.
    act(() => ref.current?.seekTo(3725))
    expect(seekTo).toHaveBeenLastCalledWith(3725, true)
  })

  it('destrói o player ao sair da tela', async () => {
    const { unmount } = render(<YouTubePlayer videoId="dQw4w9WgXcQ" title="Aula" />)
    await flush()

    unmount()

    expect(destroy).toHaveBeenCalled()
  })
})
