import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Spinner } from './Spinner'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'ghost-danger'
export type ButtonSize = 'md' | 'sm' | 'xs'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: ButtonSize
  /** Exibe um indicador de carregamento e desabilita o botão. */
  loading?: boolean
  /** Ícone exibido antes do texto. */
  icon?: ReactNode
  block?: boolean
}

/**
 * Botão padrão do design system. Gera as mesmas classes usadas no markup
 * existente (`btn btn-primary btn-sm`), então pode ser adotado aos poucos.
 */
export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  icon,
  block = false,
  type = 'button',
  className,
  disabled,
  children,
  ...rest
}: ButtonProps) {
  const classes = [
    'btn',
    `btn-${variant}`,
    size !== 'md' ? `btn-${size}` : null,
    block ? 'btn-block' : null,
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <Spinner /> : icon}
      {children}
    </button>
  )
}

type IconButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  /** Rótulo acessível obrigatório, já que o botão não tem texto visível. */
  label: string
}

export function IconButton({ label, className, type = 'button', children, ...rest }: IconButtonProps) {
  return (
    <button
      type={type}
      className={['btn-icon', className].filter(Boolean).join(' ')}
      aria-label={label}
      title={label}
      {...rest}
    >
      {children}
    </button>
  )
}
