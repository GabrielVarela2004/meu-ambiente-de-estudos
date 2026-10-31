import { MicIcon, StopIcon } from '../icons'

type MicButtonProps = {
  /** true enquanto o reconhecimento está ativo para esta questão. */
  listening: boolean
  /** Último ditado terminou em erro (borda vermelha até a próxima tentativa). */
  hasError?: boolean
  disabled?: boolean
  onClick: () => void
}

/**
 * Botão de ditado por voz das questões discursivas, com os tokens do design system.
 * Estados: parado, gravando (indicador pulsando + "Ouvindo…") e erro.
 */
export function MicButton({ listening, hasError = false, disabled = false, onClick }: MicButtonProps) {
  const label = listening ? 'Parar ditado' : 'Ditar resposta por voz'
  const classes = ['btn', 'btn-sm', 'mic-button', listening ? 'is-listening' : '', hasError && !listening ? 'has-error' : '']
    .filter(Boolean)
    .join(' ')

  return (
    <button
      type="button"
      className={classes}
      aria-label={label}
      aria-pressed={listening}
      title={label}
      onClick={onClick}
      disabled={disabled}
    >
      {listening ? (
        <>
          <span className="mic-pulse" aria-hidden="true" />
          <StopIcon className="w-4 h-4" aria-hidden="true" />
          <span>Ouvindo…</span>
        </>
      ) : (
        <MicIcon className="w-4 h-4" aria-hidden="true" />
      )}
    </button>
  )
}
