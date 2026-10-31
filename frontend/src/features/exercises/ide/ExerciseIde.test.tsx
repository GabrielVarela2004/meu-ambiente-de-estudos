import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CodeExecutionResult } from '../../../api/exercises'
import { jsonResponse, mockFetch, type RecordedCall } from '../../../test/fetchMock'
import { ExerciseIde } from './ExerciseIde'

// O Monaco não roda no jsdom: um textarea faz o papel do editor e mostra o arquivo e a linha revelada.
vi.mock('./CodeEditor', () => ({
  default: (props: { path: string; value: string; onChange: (v: string) => void; reveal: { line: number } | null }) => (
    <div>
      <span data-testid="editor-path">{props.path}</span>
      <span data-testid="editor-reveal">{props.reveal ? `linha ${props.reveal.line}` : ''}</span>
      <textarea aria-label="editor" value={props.value} onChange={(e) => props.onChange(e.target.value)} />
    </div>
  ),
}))

const exercise = {
  id: 'e1',
  starterFiles: [
    { path: 'Main.java', content: 'public class Main {\n  public static void main(String[] args) {}\n}' },
    { path: 'loja/Pedido.java', content: 'package loja;\npublic class Pedido {}' },
  ],
}

const runOk: CodeExecutionResult = {
  status: 'COMPILATION_ERROR',
  message: null,
  mainClass: null,
  exitCode: null,
  timeMillis: 0,
  stdout: '',
  stderr: '',
  compileDiagnostics: [{ file: 'loja/Pedido.java', line: 3, column: 9, message: "';' expected" }],
}

function respond(call: RecordedCall) {
  if (call.url.endsWith('/draft') && call.method === 'GET') return new Response(null, { status: 204 })
  if (call.url.endsWith('/run')) return jsonResponse(runOk)
  return jsonResponse({ files: [], updatedAt: '2026-10-31T10:00:00Z' })
}

const fileTabs = () => within(screen.getByRole('tablist', { name: 'Arquivos abertos' })).getAllByRole('tab')

function renderIde(onSubmit = vi.fn()) {
  render(<ExerciseIde blockId="b1" exercise={exercise} submitting={false} onSubmit={onSubmit} testsReport={null} />)
  return onSubmit
}

describe('ExerciseIde', () => {
  beforeEach(() => {
    mockFetch(respond)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('cria Enum pelo menu "Novo" com o esqueleto certo e abre em aba', async () => {
    const user = userEvent.setup()
    renderIde()
    await user.click(screen.getByRole('button', { name: '+ Novo' }))
    await user.click(screen.getByRole('menuitem', { name: 'Enum' }))
    await user.type(screen.getByLabelText('Nome do tipo'), 'Cor')
    await user.type(screen.getByLabelText('Pacote'), 'loja')
    await user.click(screen.getByRole('button', { name: 'Criar' }))

    expect(screen.getByTestId('editor-path')).toHaveTextContent('loja/Cor.java')
    expect(screen.getByLabelText('editor')).toHaveValue('package loja;\n\npublic enum Cor {\n\n}\n')
    expect(screen.getByRole('tab', { name: /Cor\.java/ })).toBeInTheDocument()
  })

  it('recusa nome inválido e duplicado', async () => {
    const user = userEvent.setup()
    renderIde()
    await user.click(screen.getByRole('button', { name: '+ Novo' }))
    await user.click(screen.getByRole('menuitem', { name: 'Classe' }))
    await user.type(screen.getByLabelText('Nome do tipo'), '1Pedido')
    await user.click(screen.getByRole('button', { name: 'Criar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('não é um nome válido')

    await user.clear(screen.getByLabelText('Nome do tipo'))
    await user.type(screen.getByLabelText('Nome do tipo'), 'Main')
    await user.click(screen.getByRole('button', { name: 'Criar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Já existe um arquivo Main.java')
  })

  it('abre arquivos em abas e marca alteração não salva', async () => {
    const user = userEvent.setup()
    renderIde()
    expect(fileTabs()).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: /^loja\/Pedido\.java/ }))
    expect(fileTabs()).toHaveLength(2)
    expect(screen.getByTestId('editor-path')).toHaveTextContent('loja/Pedido.java')

    await user.type(screen.getByLabelText('editor'), ' // x')
    expect(within(screen.getByRole('tab', { name: /Pedido\.java/ })).getByLabelText('alteração não salva')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Fechar loja/Pedido.java' }))
    expect(fileTabs()).toHaveLength(1)
  })

  it('exclui só com confirmação e nunca o arquivo do main', async () => {
    const user = userEvent.setup()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    renderIde()

    expect(screen.getByRole('button', { name: 'Excluir Main.java' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Excluir loja/Pedido.java' }))
    expect(screen.getByRole('button', { name: /^loja\/Pedido\.java/ })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Excluir loja/Pedido.java' }))
    expect(screen.queryByRole('button', { name: /^loja\/Pedido\.java/ })).not.toBeInTheDocument()
    expect(confirm).toHaveBeenCalledTimes(2)
  })

  it('"Executar" chama só a rota run (não conta tentativa) e o clique no erro leva à linha', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch(respond)
    const onSubmit = renderIde()

    await user.type(screen.getByLabelText(/Entrada \(stdin/), 'Ana')
    await user.click(screen.getByRole('button', { name: '▶ Executar' }))

    await screen.findByTestId('run-output')
    const runCall = calls.find((c) => c.url.endsWith('/run'))!
    expect(runCall).toMatchObject({ url: '/api/study-blocks/b1/exercises/e1/run', method: 'POST' })
    expect(JSON.parse(String(runCall.body))).toMatchObject({ stdin: 'Ana', files: exercise.starterFiles })
    expect(calls.some((c) => c.url.includes('/attempts'))).toBe(false)
    expect(onSubmit).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'loja/Pedido.java:3:9' }))
    expect(screen.getByTestId('editor-path')).toHaveTextContent('loja/Pedido.java')
    expect(screen.getByTestId('editor-reveal')).toHaveTextContent('linha 3')
  })

  it('"Enviar para correção" entrega todos os arquivos à página', async () => {
    const user = userEvent.setup()
    const onSubmit = renderIde()
    await user.click(screen.getByRole('button', { name: 'Enviar para correção' }))
    expect(onSubmit).toHaveBeenCalledWith(exercise.starterFiles)
  })

  it('restaura o rascunho salvo ao voltar ao exercício', async () => {
    mockFetch((call) =>
      call.url.endsWith('/draft') && call.method === 'GET'
        ? jsonResponse({ files: [{ path: 'Main.java', content: '// meu rascunho' }], updatedAt: '2026-10-31T10:00:00Z' })
        : jsonResponse({}),
    )
    renderIde()
    await waitFor(() => expect(screen.getByLabelText('editor')).toHaveValue('// meu rascunho'))
    expect(screen.queryByRole('button', { name: /^loja\/Pedido\.java/ })).not.toBeInTheDocument()
  })
})
