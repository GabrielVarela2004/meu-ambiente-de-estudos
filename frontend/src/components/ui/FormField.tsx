import { useId } from 'react'
import type { ReactNode } from 'react'

type FormFieldProps = {
  label: string
  /** Texto auxiliar exibido abaixo do campo. */
  hint?: ReactNode
  /** Mensagem de erro; quando presente, substitui a dica. */
  error?: string | null
  required?: boolean
  /**
   * Recebe os atributos de acessibilidade que devem ser repassados ao campo
   * (`id`, `aria-describedby`, `aria-invalid`), garantindo o vínculo
   * entre rótulo, dica e erro.
   */
  children: (fieldProps: {
    id: string
    'aria-describedby'?: string
    'aria-invalid'?: boolean
  }) => ReactNode
}

export function FormField({ label, hint, error, required = false, children }: FormFieldProps) {
  const id = useId()
  const messageId = `${id}-message`
  const hasMessage = Boolean(error || hint)

  return (
    <div className="form-field">
      <label htmlFor={id}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      {children({
        id,
        'aria-describedby': hasMessage ? messageId : undefined,
        'aria-invalid': error ? true : undefined,
      })}
      {error ? (
        <p id={messageId} className="form-error" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p id={messageId} className="form-hint">
          {hint}
        </p>
      ) : null}
    </div>
  )
}
