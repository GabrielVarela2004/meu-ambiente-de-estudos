import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Ditado por voz com a Web Speech API do navegador (SpeechRecognition / webkitSpeechRecognition).
 * 100% no frontend: nenhum áudio sai da máquina. Sem suporte (Firefox, Opera…), `isSupported`
 * é false e a tela esconde o microfone.
 */

type SpeechRecognitionAlternative = { transcript: string }

type SpeechRecognitionResult = {
  isFinal: boolean
  length: number
  [index: number]: SpeechRecognitionAlternative
}

type SpeechRecognitionResultList = {
  length: number
  [index: number]: SpeechRecognitionResult
}

type SpeechRecognitionEvent = {
  resultIndex: number
  results: SpeechRecognitionResultList
}

type SpeechRecognitionErrorEvent = {
  error: string
}

type SpeechRecognitionLike = {
  lang: string
  continuous: boolean
  interimResults: boolean
  onresult: ((event: SpeechRecognitionEvent) => void) | null
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
  abort: () => void
}

type SpeechRecognitionCtor = new () => SpeechRecognitionLike

type UseSpeechToTextOptions = {
  /** Recebe cada trecho final reconhecido (a tela anexa ao texto existente). */
  onFinalText?: (text: string) => void
  /** Disparado quando o reconhecimento termina (stop, erro ou silêncio). */
  onEnd?: () => void
  /** Recebe a mensagem de erro já traduzida para o português. */
  onError?: (message: string) => void
}

const MICROPHONE_PERMISSION_DENIED = 'Permissão do microfone negada. Libere o uso do microfone no navegador.'
const MICROPHONE_NOT_FOUND = 'Nenhum microfone encontrado. Conecte um microfone e tente novamente.'
const NO_SPEECH_DETECTED = 'Não foi detectada nenhuma fala. Clique no microfone e fale novamente.'
const NETWORK_ERROR = 'Falha de rede no reconhecimento por voz. Verifique sua conexão e tente novamente.'
const GENERIC_ERROR = 'Não foi possível usar o ditado por voz agora. Tente novamente em instantes.'

const ERROR_MESSAGES: Record<string, string> = {
  'not-allowed': MICROPHONE_PERMISSION_DENIED,
  'service-not-allowed': MICROPHONE_PERMISSION_DENIED,
  'audio-capture': MICROPHONE_NOT_FOUND,
  'no-speech': NO_SPEECH_DETECTED,
  network: NETWORK_ERROR,
  'audio-busy': GENERIC_ERROR,
  'language-not-supported': GENERIC_ERROR,
}

/** Instância disponível no navegador (Chrome, Edge); null nos demais. */
export function getSpeechRecognitionCtor(): SpeechRecognitionCtor | null {
  if (typeof window === 'undefined') return null
  const w = window as Window & {
    SpeechRecognition?: SpeechRecognitionCtor
    webkitSpeechRecognition?: SpeechRecognitionCtor
  }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null
}

export function useSpeechToText(options: UseSpeechToTextOptions = {}) {
  const [isSupported] = useState(() => getSpeechRecognitionCtor() !== null)
  const [isListening, setIsListening] = useState(false)
  const [interimText, setInterimText] = useState('')
  const [error, setError] = useState<string | null>(null)

  const recognitionRef = useRef<SpeechRecognitionLike | null>(null)
  const optionsRef = useRef(options)
  optionsRef.current = options

  const handleResult = useCallback((event: SpeechRecognitionEvent) => {
    let finalText = ''
    let interim = ''
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const result = event.results[i]
      const transcript = result[0]?.transcript ?? ''
      if (result.isFinal) {
        finalText += transcript
      } else {
        interim += transcript
      }
    }
    if (finalText.trim()) {
      // O texto final é ANEXADO ao que já está no campo; a tela decide como.
      optionsRef.current.onFinalText?.(finalText.trim())
      setInterimText('')
    }
    if (interim !== '') {
      setInterimText(interim)
    }
  }, [])

  const stop = useCallback(() => {
    const recognition = recognitionRef.current
    recognitionRef.current = null
    setIsListening(false)
    setInterimText('')
    if (!recognition) return
    recognition.onresult = null
    recognition.onerror = null
    recognition.onend = null
    try {
      recognition.stop()
    } catch {
      // Já parado: nada a fazer.
    }
    optionsRef.current.onEnd?.()
  }, [])

  const start = useCallback(() => {
    const Ctor = getSpeechRecognitionCtor()
    if (!Ctor) return
    if (recognitionRef.current) return
    setError(null)
    const recognition = new Ctor()
    recognition.lang = 'pt-BR'
    recognition.continuous = true
    recognition.interimResults = true
    recognition.onresult = (event) => handleResult(event)
    recognition.onerror = (event) => {
      // "aborted" acontece no stop voluntário e não é erro para o usuário.
      if (event.error === 'aborted') return
      const message = ERROR_MESSAGES[event.error] ?? GENERIC_ERROR
      setError(message)
      optionsRef.current.onError?.(message)
    }
    recognition.onend = () => {
      if (recognitionRef.current === recognition) {
        recognitionRef.current = null
        setIsListening(false)
        setInterimText('')
        optionsRef.current.onEnd?.()
      }
    }
    recognitionRef.current = recognition
    try {
      recognition.start()
      setIsListening(true)
    } catch {
      recognitionRef.current = null
      const message = GENERIC_ERROR
      setError(message)
      optionsRef.current.onError?.(message)
    }
  }, [handleResult])

  // Para o reconhecimento ao desmontar (troca de tela, saída da questão).
  useEffect(() => {
    return () => {
      const recognition = recognitionRef.current
      recognitionRef.current = null
      if (recognition) {
        recognition.onresult = null
        recognition.onerror = null
        recognition.onend = null
        try {
          recognition.abort()
        } catch {
          // Já parado: nada a fazer.
        }
      }
    }
  }, [])

  return { isSupported, isListening, interimText, error, start, stop }
}
