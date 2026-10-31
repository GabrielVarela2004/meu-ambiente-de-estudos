import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import {
  deleteExerciseDraft,
  getExerciseDraft,
  runExercise,
  saveExerciseDraft,
  type CodeExecutionResult,
  type CompileDiagnostic,
  type ExerciseFile,
} from '../../../api/exercises'
import { useDeferredEffect } from '../../../hooks/useDeferredEffect'
import {
  fileBaseName,
  initialFiles,
  mainFilePath,
  pathFor,
  renameFile,
  skeleton,
  stackLocations,
  TYPE_KIND_LABELS,
  validateTypeName,
  type JavaTypeKind,
} from './javaProject'
import '../../../styles/pages/exercise-ide.css'

// O Monaco é grande: carrega só quando um exercício com editor aparece na tela.
const CodeEditor = lazy(() => import('./CodeEditor'))

/** Tempo sem digitar antes de salvar o rascunho automaticamente. */
export const AUTOSAVE_DELAY_MS = 1500

export type TestsReport = {
  status?: string
  message?: string
  compileErrors?: string[]
  compileDiagnostics?: CompileDiagnostic[]
  tests?: { name: string; passed: boolean; mandatory: boolean; message?: string }[]
} | null

type Props = {
  blockId: string
  exercise: { id: string; starterFiles?: ExerciseFile[] | null; starterCode?: string | null }
  submitting: boolean
  /** "Enviar para correção": roda os testes e conta como tentativa (fluxo da página). */
  onSubmit: (files: ExerciseFile[]) => void
  /** Projeto atual, para a tutora receber todos os arquivos. */
  onFilesChange?: (files: ExerciseFile[]) => void
  /** Relatório da última correção, mostrado na aba "Testes". */
  testsReport?: TestsReport
}

type ConsoleTab = 'run' | 'tests'
type Reveal = { path: string; line: number; column: number; nonce: number }

const runStatusLabels: Record<CodeExecutionResult['status'], string> = {
  OK: 'Processo finalizado',
  COMPILATION_ERROR: 'Erro de compilação',
  RUNTIME_ERROR: 'Erro de execução',
  TIMEOUT: 'Tempo limite excedido',
  NO_MAIN: 'Sem método main',
  UNAVAILABLE: 'Ambiente de execução indisponível',
}

function snapshot(files: ExerciseFile[]): Record<string, string> {
  return Object.fromEntries(files.map((f) => [f.path, f.content]))
}

export function ExerciseIde({ blockId, exercise, submitting, onSubmit, onFilesChange, testsReport }: Props) {
  const starter = useMemo(() => initialFiles(exercise), [exercise])
  const [files, setFiles] = useState<ExerciseFile[]>(starter)
  const [saved, setSaved] = useState<Record<string, string>>(() => snapshot(starter))
  const [loaded, setLoaded] = useState(false)
  const [openTabs, setOpenTabs] = useState<string[]>(() => [mainFilePath(starter) ?? starter[0].path])
  const [active, setActive] = useState<string>(() => mainFilePath(starter) ?? starter[0].path)
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')

  const [creating, setCreating] = useState<JavaTypeKind | null>(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const [newName, setNewName] = useState('')
  const [newPkg, setNewPkg] = useState('')
  const [renaming, setRenaming] = useState<string | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  const [consoleTab, setConsoleTab] = useState<ConsoleTab>('run')
  const [stdin, setStdin] = useState('')
  const [running, setRunning] = useState(false)
  const [runResult, setRunResult] = useState<CodeExecutionResult | null>(null)
  const [runError, setRunError] = useState<string | null>(null)
  const [reveal, setReveal] = useState<Reveal | null>(null)

  const mainPath = mainFilePath(files)
  const activeFile = files.find((f) => f.path === active) ?? files[0]
  const isDirty = (path: string) => files.find((f) => f.path === path)?.content !== saved[path]
  const anyDirty = files.some((f) => isDirty(f.path)) || Object.keys(saved).some((p) => !files.some((f) => f.path === p))

  // Rascunho salvo: os arquivos reaparecem ao voltar ao exercício.
  useDeferredEffect(async () => {
    try {
      const draft = await getExerciseDraft(blockId, exercise.id)
      if (draft && draft.files.length > 0) {
        setFiles(draft.files)
        setSaved(snapshot(draft.files))
        const first = mainFilePath(draft.files) ?? draft.files[0].path
        setOpenTabs([first])
        setActive(first)
      }
    } catch {
      // Sem rascunho acessível: segue com o código inicial.
    } finally {
      setLoaded(true)
    }
  }, [blockId, exercise.id])

  // Avisa a página só quando os arquivos mudam (a função recebida pode ser recriada a cada renderização).
  const onFilesChangeRef = useRef(onFilesChange)
  useEffect(() => {
    onFilesChangeRef.current = onFilesChange
  }, [onFilesChange])
  useEffect(() => {
    onFilesChangeRef.current?.(files)
  }, [files])

  const saveDraft = useCallback(async () => {
    setSaveState('saving')
    try {
      await saveExerciseDraft(blockId, exercise.id, files)
      setSaved(snapshot(files))
      setSaveState('saved')
    } catch {
      setSaveState('error')
    }
  }, [blockId, exercise.id, files])

  // Salvamento automático com espera (debounce) depois da última alteração.
  const saveRef = useRef(saveDraft)
  useEffect(() => {
    saveRef.current = saveDraft
  }, [saveDraft])
  useEffect(() => {
    if (!loaded || !anyDirty) return
    const timer = window.setTimeout(() => void saveRef.current(), AUTOSAVE_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [files, loaded, anyDirty])

  const openFile = (path: string) => {
    setOpenTabs((tabs) => (tabs.includes(path) ? tabs : [...tabs, path]))
    setActive(path)
  }

  const closeTab = (path: string) => {
    const remaining = openTabs.filter((t) => t !== path)
    setOpenTabs(remaining)
    if (active === path && remaining.length > 0) setActive(remaining[remaining.length - 1])
  }

  const updateContent = (content: string) => {
    setFiles((prev) => prev.map((f) => (f.path === activeFile.path ? { ...f, content } : f)))
  }

  const startCreate = (kind: JavaTypeKind) => {
    setMenuOpen(false)
    setRenaming(null)
    setCreating(kind)
    setNewName('')
    setNewPkg('')
    setFormError(null)
  }

  const confirmCreate = () => {
    if (!creating) return
    const error = validateTypeName(newName, newPkg, files)
    if (error) {
      setFormError(error)
      return
    }
    const path = pathFor(newName.trim(), newPkg.trim())
    setFiles((prev) => [...prev, { path, content: skeleton(creating, newName.trim(), newPkg.trim()) }])
    setCreating(null)
    openFile(path)
  }

  const startRename = (path: string) => {
    setCreating(null)
    setRenaming(path)
    setRenameValue(fileBaseName(path))
    setFormError(null)
  }

  const confirmRename = () => {
    if (!renaming) return
    const file = files.find((f) => f.path === renaming)
    if (!file) return
    const pkg = renaming.includes('/') ? renaming.slice(0, renaming.lastIndexOf('/')).replace(/\//g, '.') : ''
    const error = validateTypeName(renameValue, pkg, files, renaming)
    if (error) {
      setFormError(error)
      return
    }
    const renamed = renameFile(file, renameValue.trim())
    setFiles((prev) => prev.map((f) => (f.path === renaming ? renamed : f)))
    setOpenTabs((tabs) => tabs.map((t) => (t === renaming ? renamed.path : t)))
    if (active === renaming) setActive(renamed.path)
    setRenaming(null)
  }

  const deleteFile = (path: string) => {
    if (path === mainPath) return
    if (!window.confirm(`Excluir o arquivo ${path}? Esta ação não pode ser desfeita.`)) return
    setFiles((prev) => prev.filter((f) => f.path !== path))
    closeTab(path)
  }

  const restoreStarter = async () => {
    if (!window.confirm('Restaurar o código inicial? Seus arquivos e o rascunho salvo serão descartados.')) return
    try {
      await deleteExerciseDraft(blockId, exercise.id)
    } catch {
      // O rascunho é sobrescrito no próximo salvamento de qualquer forma.
    }
    setFiles(starter)
    setSaved(snapshot(starter))
    const first = mainFilePath(starter) ?? starter[0].path
    setOpenTabs([first])
    setActive(first)
    setRunResult(null)
  }

  const run = useCallback(async () => {
    setConsoleTab('run')
    setRunning(true)
    setRunError(null)
    try {
      setRunResult(await runExercise(blockId, exercise.id, files, stdin))
    } catch (e) {
      setRunError(e instanceof Error ? e.message : 'Não foi possível executar o código.')
      setRunResult(null)
    } finally {
      setRunning(false)
    }
  }, [blockId, exercise.id, files, stdin])

  const goTo = (path: string, line: number, column = 1) => {
    if (!files.some((f) => f.path === path)) return
    openFile(path)
    setReveal({ path, line, column, nonce: Date.now() })
  }

  const diagnostics = consoleTab === 'run' ? runResult?.compileDiagnostics ?? [] : testsReport?.compileDiagnostics ?? []
  const markers = diagnostics
    .filter((d) => d.file === activeFile.path)
    .map((d) => ({ line: d.line, column: d.column, message: d.message }))

  const sortedFiles = [...files].sort((a, b) => a.path.localeCompare(b.path))

  return (
    <div className="ide-shell">
      <div className="ide-toolbar">
        <span className="ide-toolbar-title">Projeto Java</span>
        <span className="ide-save-state" aria-live="polite">
          {saveState === 'saving' ? 'Salvando rascunho…' : saveState === 'error' ? 'Rascunho não salvo' : anyDirty ? 'Alterações não salvas' : 'Rascunho salvo'}
        </span>
        <div className="ide-toolbar-actions">
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void restoreStarter()}>
            Restaurar código inicial
          </button>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => void run()} disabled={running} title="Shift+F10">
            {running ? 'Executando…' : '▶ Executar'}
          </button>
          <button type="button" className="btn btn-primary btn-sm" onClick={() => onSubmit(files)} disabled={submitting}>
            {submitting ? 'Corrigindo…' : 'Enviar para correção'}
          </button>
        </div>
      </div>

      <div className="ide-body">
        <aside className="ide-project" aria-label="Projeto">
          <div className="ide-project-header">
            <span>src</span>
            <div className="ide-new-menu">
              <button type="button" className="btn btn-ghost btn-xs" onClick={() => setMenuOpen((o) => !o)} aria-expanded={menuOpen}>
                + Novo
              </button>
              {menuOpen && (
                <div className="ide-new-menu-list" role="menu">
                  {(Object.keys(TYPE_KIND_LABELS) as JavaTypeKind[]).map((kind) => (
                    <button key={kind} type="button" role="menuitem" className="btn btn-ghost btn-xs" onClick={() => startCreate(kind)}>
                      {TYPE_KIND_LABELS[kind]}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          {creating && (
            <form
              className="ide-inline-form"
              onSubmit={(e) => {
                e.preventDefault()
                confirmCreate()
              }}
            >
              <label className="ide-inline-label">
                Nova {TYPE_KIND_LABELS[creating].toLowerCase()}
                <input className="form-input" autoFocus value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="Nome" aria-label="Nome do tipo" />
              </label>
              <input className="form-input" value={newPkg} onChange={(e) => setNewPkg(e.target.value)} placeholder="Pacote (opcional)" aria-label="Pacote" />
              <div className="ide-inline-actions">
                <button type="submit" className="btn btn-primary btn-xs">Criar</button>
                <button type="button" className="btn btn-ghost btn-xs" onClick={() => setCreating(null)}>Cancelar</button>
              </div>
            </form>
          )}
          {formError && <p className="ide-form-error" role="alert">{formError}</p>}

          <ul className="ide-file-list">
            {sortedFiles.map((f) => (
              <li key={f.path} className={`ide-file ${f.path === activeFile.path ? 'active' : ''}`}>
                {renaming === f.path ? (
                  <form
                    className="ide-rename-form"
                    onSubmit={(e) => {
                      e.preventDefault()
                      confirmRename()
                    }}
                  >
                    <input className="form-input" autoFocus aria-label={`Novo nome de ${f.path}`} value={renameValue} onChange={(e) => setRenameValue(e.target.value)} />
                    <button type="submit" className="btn btn-primary btn-xs">OK</button>
                    <button type="button" className="btn btn-ghost btn-xs" onClick={() => setRenaming(null)}>✕</button>
                  </form>
                ) : (
                  <>
                    <button type="button" className="btn btn-ghost btn-xs ide-file-name" onClick={() => openFile(f.path)} title={f.path}>
                      {f.path}
                      {f.path === mainPath && <span className="ide-main-badge">main</span>}
                    </button>
                    <span className="ide-file-actions">
                      <button type="button" className="btn btn-ghost btn-xs" onClick={() => startRename(f.path)} aria-label={`Renomear ${f.path}`}>✎</button>
                      <button
                        type="button"
                        className="btn btn-ghost-danger btn-xs"
                        onClick={() => deleteFile(f.path)}
                        disabled={f.path === mainPath}
                        aria-label={`Excluir ${f.path}`}
                        title={f.path === mainPath ? 'O arquivo com o main não pode ser excluído.' : undefined}
                      >
                        🗑
                      </button>
                    </span>
                  </>
                )}
              </li>
            ))}
          </ul>
        </aside>

        <section className="ide-editor-area">
          <div className="ide-tabs" role="tablist" aria-label="Arquivos abertos">
            {openTabs.map((path) => (
              <div key={path} className={`ide-tab ${path === activeFile.path ? 'active' : ''}`}>
                <button type="button" role="tab" aria-selected={path === activeFile.path} className="btn btn-ghost btn-xs ide-tab-name" onClick={() => setActive(path)}>
                  {fileBaseName(path)}.java
                  {isDirty(path) && <span className="ide-dirty-dot" aria-label="alteração não salva">●</span>}
                </button>
                <button type="button" className="btn btn-ghost btn-xs" onClick={() => closeTab(path)} aria-label={`Fechar ${path}`}>✕</button>
              </div>
            ))}
          </div>
          <div className="ide-editor">
            <Suspense fallback={<div className="ide-editor-loading">Carregando editor…</div>}>
              <CodeEditor
                path={activeFile.path}
                value={activeFile.content}
                onChange={updateContent}
                onSave={() => void saveDraft()}
                onRun={() => void run()}
                markers={markers}
                reveal={reveal && reveal.path === activeFile.path ? reveal : null}
              />
            </Suspense>
          </div>
        </section>
      </div>

      <div className="ide-console">
        <div className="ide-console-tabs" role="tablist" aria-label="Console">
          <button type="button" role="tab" aria-selected={consoleTab === 'run'} className={`btn btn-ghost btn-xs ${consoleTab === 'run' ? 'active' : ''}`} onClick={() => setConsoleTab('run')}>
            Executar
          </button>
          <button type="button" role="tab" aria-selected={consoleTab === 'tests'} className={`btn btn-ghost btn-xs ${consoleTab === 'tests' ? 'active' : ''}`} onClick={() => setConsoleTab('tests')}>
            Testes
          </button>
        </div>

        {consoleTab === 'run' ? (
          <div className="ide-console-body">
            <label className="ide-stdin">
              Entrada (stdin, opcional)
              <textarea className="form-textarea" rows={2} value={stdin} onChange={(e) => setStdin(e.target.value)} placeholder="Linhas lidas pelo Scanner" />
            </label>
            {runError && <p className="ide-console-error">{runError}</p>}
            {running && <p className="ide-console-muted">Compilando e executando…</p>}
            {runResult && (
              <div className="ide-console-output" data-testid="run-output">
                <p className={`ide-console-status ${runResult.status === 'OK' ? 'ok' : 'error'}`}>
                  {runStatusLabels[runResult.status]}
                  {runResult.status === 'OK' || runResult.status === 'RUNTIME_ERROR'
                    ? ` · código de saída ${runResult.exitCode ?? '—'} · ${runResult.timeMillis} ms`
                    : ''}
                </p>
                {runResult.message && <p className="ide-console-muted">{runResult.message}</p>}
                <DiagnosticList diagnostics={runResult.compileDiagnostics} onOpen={goTo} />
                {runResult.stdout && <pre className="ide-console-pre">{runResult.stdout}</pre>}
                {runResult.stderr && <StackTrace text={runResult.stderr} files={files} onOpen={goTo} />}
              </div>
            )}
            {!runResult && !running && !runError && (
              <p className="ide-console-muted">"Executar" compila e roda o main; não conta como tentativa e não usa IA.</p>
            )}
          </div>
        ) : (
          <div className="ide-console-body">
            {!testsReport ? (
              <p className="ide-console-muted">Envie para correção para ver o resultado dos testes aqui.</p>
            ) : (
              <div className="ide-console-output">
                {testsReport.message && <p className="ide-console-muted">{testsReport.message}</p>}
                <DiagnosticList diagnostics={testsReport.compileDiagnostics ?? []} onOpen={goTo} />
                {(testsReport.tests ?? []).map((t) => (
                  <p key={t.name} className={`ide-test-line ${t.passed ? 'ok' : 'error'}`}>
                    {t.passed ? '✔' : '✘'} {t.name}
                    {t.mandatory ? ' (obrigatório)' : ''}
                    {t.message ? ` — ${t.message}` : ''}
                  </p>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function DiagnosticList({ diagnostics, onOpen }: { diagnostics: CompileDiagnostic[]; onOpen: (path: string, line: number, column: number) => void }) {
  if (diagnostics.length === 0) return null
  return (
    <ul className="ide-diagnostics">
      {diagnostics.map((d, i) => (
        <li key={`${d.file}:${d.line}:${i}`}>
          <button type="button" className="btn btn-ghost btn-xs ide-link" onClick={() => onOpen(d.file, d.line, d.column)}>
            {d.file}:{d.line}
            {d.column ? `:${d.column}` : ''}
          </button>{' '}
          <span className="ide-console-error">{d.message}</span>
        </li>
      ))}
    </ul>
  )
}

/** Saída de erro com as linhas "Arquivo.java:12" da exceção clicáveis. */
function StackTrace({ text, files, onOpen }: { text: string; files: ExerciseFile[]; onOpen: (path: string, line: number) => void }) {
  const locations = stackLocations(text, files)
  const parts: ReactNode[] = []
  let cursor = 0
  locations.forEach((loc, i) => {
    parts.push(text.slice(cursor, loc.index))
    parts.push(
      <button key={i} type="button" className="btn btn-ghost btn-xs ide-link" onClick={() => onOpen(loc.path, loc.line)}>
        {text.slice(loc.index, loc.index + loc.length)}
      </button>,
    )
    cursor = loc.index + loc.length
  })
  parts.push(text.slice(cursor))
  return <pre className="ide-console-pre ide-console-error">{parts}</pre>
}
