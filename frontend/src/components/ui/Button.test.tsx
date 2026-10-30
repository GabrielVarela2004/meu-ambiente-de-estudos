import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Button, IconButton } from './Button'

describe('Button', () => {
  it('gera as classes do design system e é type="button" por padrão', () => {
    render(
      <Button variant="secondary" size="sm" block>
        Salvar
      </Button>,
    )

    const button = screen.getByRole('button', { name: 'Salvar' })
    expect(button).toHaveClass('btn', 'btn-secondary', 'btn-sm', 'btn-block')
    expect(button).toHaveAttribute('type', 'button')
  })

  it('chama onClick quando clicado', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Enviar</Button>)

    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }))

    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('em carregamento fica desabilitado, marcado como ocupado e não dispara cliques', async () => {
    const onClick = vi.fn()
    render(
      <Button loading onClick={onClick}>
        Gerando
      </Button>,
    )

    const button = screen.getByRole('button', { name: /Gerando/ })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
    await userEvent.click(button)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('IconButton exige rótulo acessível', () => {
    render(<IconButton label="Fechar">✕</IconButton>)

    expect(screen.getByRole('button', { name: 'Fechar' })).toHaveClass('btn-icon')
  })
})
