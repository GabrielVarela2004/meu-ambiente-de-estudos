import type { ReactNode } from 'react'

type PageHeaderProps = {
  title: ReactNode
  description?: ReactNode
  /** Rótulo curto acima do título (ex.: módulo ou contexto). */
  eyebrow?: ReactNode
  /** Botões e controles alinhados à direita do cabeçalho. */
  actions?: ReactNode
}

/** Cabeçalho padrão de página — usa as mesmas classes já presentes nas páginas. */
export function PageHeader({ title, description, eyebrow, actions }: PageHeaderProps) {
  return (
    <header className="page-header-flex">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1 className="page-main-title">{title}</h1>
        {description && <p className="page-main-subtitle">{description}</p>}
      </div>
      {actions && <div className="page-actions-group">{actions}</div>}
    </header>
  )
}
