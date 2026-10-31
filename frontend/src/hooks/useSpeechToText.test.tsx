import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSpeechToText } from './useSpeechToText'

/** Um resultado de fala: final (vira texto) ou provisório (interim). */
function speechResult(isFinal: boolean, transcript: string) {
  return { isFinal, length: 1, 0: { transcript } }
}

/** Simula a SpeechRecognition do navegador (nenhum áudio real é usado). */
class MockSpeechRecognition {
  static instances: MockSpeechRecognition[] = []

  lang = ''
  continuous = false
  interimResults = false
  onresult: ((event: { resultIndex: number; results: unknown }) => void) | null = null
  onerror: ((event: { error: string }) => void) | null = null
  onend: (() => void) | null = null
  start = vi.fn()
  stop = vi.fn()
  abort = vi.fn()

  constructor() {
    MockSpeechRecognition.instances.push(this)
  }

  emit(resultIndex: number, results: ReturnType<typeof speechResult>[]) {
    this.onresult?.({ resultIndex, results })
  }
}

beforeEach(() => {
  MockSpeechRecognition.instances = []
  vi.stubGlobal('SpeechRecognition', MockSpeechRecognition)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('useSpeechToText', () => {
  it('configura pt-BR, contínuo e com resultados parciais ao iniciar', () => {
    const { result } = renderHook(() => useSpeechToText())

    expect(result.current.isSupported).toBe(true)
    act(() => result.current.start())

    const recognition = MockSpeechRecognition.instances[0]
    expect(recognition!.lang).toBe('pt-BR')
    expect(recognition!.continuous).toBe(true)
    expect(recognition!.interimResults).toBe(true)
    expect(recognition!.start).toHaveBeenCalled()
    expect(result.current.isListening).toBe(true)
  })

  it('expõe o texto provisório e entrega o final pelo onFinalText (anexar é papel da tela)', () => {
    const onFinalText = vi.fn()
    const { result } = renderHook(() => useSpeechToText({ onFinalText }))
    act(() => result.current.start())

    const recognition = MockSpeechRecognition.instances[0]!
    act(() => recognition.emit(0, [speechResult(false, 'usa hash')]))
    expect(result.current.interimText).toBe('usa hash')

    act(() => recognition.emit(0, [speechResult(true, 'usa hashCode e equals.')]))
    expect(onFinalText).toHaveBeenCalledWith('usa hashCode e equals.')
    expect(result.current.interimText).toBe('')
  })

  it('stop encerra o reconhecimento e limpa o provisório', () => {
    const onEnd = vi.fn()
    const { result } = renderHook(() => useSpeechToText({ onEnd }))
    act(() => result.current.start())
    const recognition = MockSpeechRecognition.instances[0]!
    act(() => recognition.emit(0, [speechResult(false, 'oi')]))

    act(() => result.current.stop())

    expect(recognition.stop).toHaveBeenCalled()
    expect(result.current.isListening).toBe(false)
    expect(result.current.interimText).toBe('')
    expect(onEnd).toHaveBeenCalled()
  })

  it('para o reconhecimento ao desmontar', () => {
    const { result, unmount } = renderHook(() => useSpeechToText())
    act(() => result.current.start())
    const recognition = MockSpeechRecognition.instances[0]!

    unmount()

    expect(recognition.abort).toHaveBeenCalled()
  })

  it('sem suporte no navegador: isSupported false e start não faz nada', () => {
    vi.stubGlobal('SpeechRecognition', undefined)
    vi.stubGlobal('webkitSpeechRecognition', undefined)
    const { result } = renderHook(() => useSpeechToText())

    expect(result.current.isSupported).toBe(false)
    act(() => result.current.start())
    expect(result.current.isListening).toBe(false)
    expect(MockSpeechRecognition.instances).toHaveLength(0)
  })

  it('traduz os erros do reconhecimento para mensagens em português', () => {
    const onError = vi.fn()
    const { result } = renderHook(() => useSpeechToText({ onError }))
    act(() => result.current.start())

    const recognition = MockSpeechRecognition.instances[0]!
    act(() => recognition.onerror?.({ error: 'not-allowed' }))
    expect(result.current.error).toBe('Permissão do microfone negada. Libere o uso do microfone no navegador.')
    expect(onError).toHaveBeenCalled()

    act(() => recognition.onerror?.({ error: 'no-speech' }))
    expect(result.current.error).toBe('Não foi detectada nenhuma fala. Clique no microfone e fale novamente.')

    act(() => recognition.onerror?.({ error: 'audio-capture' }))
    expect(result.current.error).toBe('Nenhum microfone encontrado. Conecte um microfone e tente novamente.')

    act(() => recognition.onerror?.({ error: 'network' }))
    expect(result.current.error).toBe('Falha de rede no reconhecimento por voz. Verifique sua conexão e tente novamente.')
  })
})
