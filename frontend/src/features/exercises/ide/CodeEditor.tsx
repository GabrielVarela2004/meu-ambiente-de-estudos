import { useEffect, useRef } from 'react'
import Editor, { loader, type OnMount } from '@monaco-editor/react'
// Só o núcleo, os recursos de edição usados e o Java (o pacote completo traz dezenas de linguagens).
import * as monaco from 'monaco-editor/editor'
import 'monaco-editor/features/codicon/register'
import 'monaco-editor/features/codeEditor/register'
import 'monaco-editor/features/bracketMatching/register'
import 'monaco-editor/features/clipboard/register'
import 'monaco-editor/features/comment/register'
import 'monaco-editor/features/contextmenu/register'
import 'monaco-editor/features/cursorUndo/register'
import 'monaco-editor/features/find/register'
import 'monaco-editor/features/folding/register'
import 'monaco-editor/features/hover/register'
import 'monaco-editor/features/indentation/register'
import 'monaco-editor/features/linesOperations/register'
import 'monaco-editor/features/multicursor/register'
import 'monaco-editor/features/snippet/register'
import 'monaco-editor/features/suggest/register'
import 'monaco-editor/features/wordHighlighter/register'
import 'monaco-editor/features/wordOperations/register'
import 'monaco-editor/languages/definitions/java/register'
import EditorWorker from 'monaco-editor/editor/editor.worker?worker'
import { JAVA_SNIPPETS } from './javaProject'

declare global {
  interface Window {
    MonacoEnvironment?: { getWorker: () => Worker }
  }
}

window.MonacoEnvironment = { getWorker: () => new EditorWorker() }
// Monaco empacotado com o app: nada é baixado de CDN.
loader.config({ monaco })

let javaSetupDone = false

/** Cor de um token CSS (tokens.css) sem o "#", como o tema do Monaco espera. */
function cssColor(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim().replace('#', '')
}

/** Tema escuro estilo Darcula e snippets do IntelliJ, registrados uma vez. */
function setupJava() {
  if (javaSetupDone) return
  javaSetupDone = true
  const c = (name: string) => cssColor(name)
  monaco.editor.defineTheme('estudos-darcula', {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: '', foreground: c('--ide-fg') },
      { token: 'keyword', foreground: c('--ide-keyword') },
      { token: 'string', foreground: c('--ide-string') },
      { token: 'number', foreground: c('--ide-number') },
      { token: 'comment', foreground: c('--ide-comment'), fontStyle: 'italic' },
      { token: 'annotation', foreground: c('--ide-annotation') },
      { token: 'type', foreground: c('--ide-type') },
    ],
    colors: {
      'editor.background': `#${c('--ide-bg')}`,
      'editor.foreground': `#${c('--ide-fg')}`,
      'editorLineNumber.foreground': `#${c('--ide-line-number')}`,
      'editor.lineHighlightBackground': `#${c('--ide-current-line')}`,
      'editor.selectionBackground': `#${c('--ide-selection')}`,
      'editorGutter.background': `#${c('--ide-panel-alt')}`,
    },
  })
  monaco.languages.registerCompletionItemProvider('java', {
    provideCompletionItems(model, position) {
      const word = model.getWordUntilPosition(position)
      const range = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn)
      return {
        suggestions: JAVA_SNIPPETS.map((s) => ({
          label: s.label,
          detail: s.detail,
          kind: monaco.languages.CompletionItemKind.Snippet,
          insertText: s.body,
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          range,
        })),
      }
    },
  })
}

export type EditorMarker = { line: number; column: number; message: string }

type Props = {
  /** Caminho do arquivo no projeto: cada arquivo tem o próprio modelo (desfazer e cursor separados). */
  path: string
  value: string
  onChange: (value: string) => void
  onSave: () => void
  onRun: () => void
  /** Erros de compilação deste arquivo, sublinhados no editor. */
  markers: EditorMarker[]
  /** Posição para mostrar (clique num erro do console); o nonce força repetir a mesma linha. */
  reveal: { line: number; column: number; nonce: number } | null
}

export default function CodeEditor({ path, value, onChange, onSave, onRun, markers, reveal }: Props) {
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null)
  // Os atalhos são registrados uma vez; usam sempre as funções atuais.
  const actions = useRef({ onSave, onRun })
  useEffect(() => {
    actions.current = { onSave, onRun }
  }, [onSave, onRun])

  const handleMount: OnMount = (editor) => {
    editorRef.current = editor
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => actions.current.onSave())
    editor.addCommand(monaco.KeyMod.Shift | monaco.KeyCode.F10, () => actions.current.onRun())
    // Ctrl+/ (comentar linha) já vem do recurso "comment" do Monaco.
  }

  useEffect(() => {
    const model = editorRef.current?.getModel()
    if (!model) return
    monaco.editor.setModelMarkers(
      model,
      'javac',
      markers.map((m) => ({
        startLineNumber: m.line,
        startColumn: Math.max(1, m.column),
        endLineNumber: m.line,
        endColumn: model.getLineMaxColumn(Math.min(Math.max(1, m.line), model.getLineCount())),
        message: m.message,
        severity: monaco.MarkerSeverity.Error,
      })),
    )
  }, [markers, path])

  useEffect(() => {
    const editor = editorRef.current
    if (!editor || !reveal) return
    const line = Math.max(1, reveal.line)
    editor.revealLineInCenter(line)
    editor.setPosition({ lineNumber: line, column: Math.max(1, reveal.column) })
    editor.focus()
  }, [reveal, path])

  return (
    <Editor
      path={`file:///src/${path}`}
      language="java"
      theme="estudos-darcula"
      value={value}
      beforeMount={setupJava}
      onMount={handleMount}
      onChange={(v) => onChange(v ?? '')}
      loading={<div className="ide-editor-loading">Carregando editor…</div>}
      options={{
        fontFamily: getComputedStyle(document.documentElement).getPropertyValue('--font-mono').trim() || 'monospace',
        fontSize: 14,
        lineNumbers: 'on',
        minimap: { enabled: false },
        autoClosingBrackets: 'always',
        autoClosingQuotes: 'always',
        autoIndent: 'full',
        formatOnType: true,
        tabSize: 4,
        insertSpaces: true,
        scrollBeyondLastLine: false,
        automaticLayout: true,
        renderLineHighlight: 'all',
        snippetSuggestions: 'top',
        fixedOverflowWidgets: true,
      }}
    />
  )
}
