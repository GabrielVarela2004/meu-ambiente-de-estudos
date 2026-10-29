type SpinnerProps = {
  size?: 'sm' | 'lg'
  /** Texto lido por leitores de tela. */
  label?: string
}

export function Spinner({ size = 'sm', label = 'Carregando' }: SpinnerProps) {
  return (
    <span
      className={size === 'lg' ? 'spinner spinner-lg' : 'spinner'}
      role="status"
      aria-label={label}
    />
  )
}
