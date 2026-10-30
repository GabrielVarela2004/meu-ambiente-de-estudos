import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { request } from '../../api/http'
import { importMaterialFile } from '../../api/materials'

export type SourceType =
  | 'TEXT'
  | 'FILE_TXT'
  | 'FILE_MD'
  | 'FILE_PDF'
  | 'FILE_DOCX'
  | 'FILE_PPTX'
  | 'FILE_CODE'
  | 'WEB'
  | 'YOUTUBE'
export type ProcessingStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export type StudyMaterialItem = {
  id: string
  studyBlockId: string
  title: string
  content: string
  sourceType: SourceType
  sourceUrl: string | null
  externalVideoId: string | null
  language: string | null
  processingStatus: ProcessingStatus
  errorMessage: string | null
  metadataJson?: string | null
  enabledForChat?: boolean
  wordCount?: number
  charCount?: number
  status: 'ACTIVE' | 'ARCHIVED'
  summary?: string | null
  summarySource?: string | null
  summaryGeneratedAt?: string | null
  createdAt: string
  updatedAt: string
}

type AddSourceModalProps = {
  blockId: string
  blockTitle: string
  isOpen: boolean
  onClose: () => void
  onSourceAdded: (material: StudyMaterialItem) => Promise<void> | void
}

type SourceTab = 'FILE' | 'TEXT' | 'YOUTUBE'

export function AddSourceModal({
  blockId,
  blockTitle,
  isOpen,
  onClose,
  onSourceAdded,
}: AddSourceModalProps) {
  const [activeTab, setActiveTab] = useState<SourceTab>('FILE')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [textTitle, setTextTitle] = useState('')
  const [textContent, setTextContent] = useState('')
  const [youtubeUrl, setYoutubeUrl] = useState('')
  const [status, setStatus] = useState<'IDLE' | 'PROCESSING' | 'COMPLETED' | 'FAILED'>('IDLE')
  const [statusMessage, setStatusMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (!isOpen) return null

  const resetFeedback = () => {
    setStatus('IDLE')
    setStatusMessage(null)
    setErrorMessage(null)
  }

  const switchTab = (tab: SourceTab) => {
    setActiveTab(tab)
    resetFeedback()
  }

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    resetFeedback()
    const file = event.target.files?.[0] ?? null
    setSelectedFile(file)
  }

  const submitFile = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedFile) {
      setErrorMessage('Selecione um arquivo para importar.')
      return
    }

    const ext = selectedFile.name.substring(selectedFile.name.lastIndexOf('.')).toLowerCase()
    const allowed = ['.pdf', '.txt', '.md', '.docx']
    if (!allowed.includes(ext)) {
      setErrorMessage('Formato não suportado. Utilize PDF, TXT, MD ou DOCX.')
      return
    }

    const maxBytes = 10 * 1024 * 1024 // 10 MB (mesmo limite do backend)
    if (selectedFile.size > maxBytes) {
      setErrorMessage('O arquivo excede o tamanho máximo de 10 MB.')
      return
    }

    setStatus('PROCESSING')
    setStatusMessage('Enviando e extraindo texto do arquivo...')
    setErrorMessage(null)

    try {
      const saved = await importMaterialFile<StudyMaterialItem>(blockId, selectedFile)

      setStatus('COMPLETED')
      setStatusMessage('Arquivo importado com sucesso!')
      await onSourceAdded(saved)
      setTimeout(() => {
        onClose()
        setSelectedFile(null)
        resetFeedback()
      }, 700)
    } catch (err) {
      setStatus('FAILED')
      setErrorMessage(
        err instanceof Error ? err.message : 'Falha ao processar o arquivo selecionado.',
      )
    }
  }

  const submitText = async (event: FormEvent) => {
    event.preventDefault()
    if (!textTitle.trim()) {
      setErrorMessage('Informe o título da fonte.')
      return
    }
    if (!textContent.trim()) {
      setErrorMessage('Cole ou digite o conteúdo do material.')
      return
    }

    setStatus('PROCESSING')
    setStatusMessage('Salvando fonte de estudo...')
    setErrorMessage(null)

    try {
      const saved = await request<StudyMaterialItem>(`/api/study-blocks/${blockId}/materials`, {
        method: 'POST',
        body: JSON.stringify({
          title: textTitle.trim(),
          content: textContent.trim(),
        }),
      })

      setStatus('COMPLETED')
      setStatusMessage('Fonte criada com sucesso!')
      await onSourceAdded(saved)
      setTimeout(() => {
        onClose()
        setTextTitle('')
        setTextContent('')
        resetFeedback()
      }, 700)
    } catch (err) {
      setStatus('FAILED')
      setErrorMessage(err instanceof Error ? err.message : 'Falha ao salvar a fonte de texto.')
    }
  }

  const submitYoutube = async (event: FormEvent) => {
    event.preventDefault()
    if (!youtubeUrl.trim()) {
      setErrorMessage('Informe a URL do vídeo do YouTube.')
      return
    }

    setStatus('PROCESSING')
    setStatusMessage('Extraindo transcrição do vídeo no YouTube...')
    setErrorMessage(null)

    try {
      const saved = await request<StudyMaterialItem>(
        `/api/study-blocks/${blockId}/materials/youtube`,
        {
          method: 'POST',
          body: JSON.stringify({ url: youtubeUrl.trim() }),
        },
      )

      setStatus('COMPLETED')
      setStatusMessage('Vídeo importado e transcrição processada!')
      await onSourceAdded(saved)
      setTimeout(() => {
        onClose()
        setYoutubeUrl('')
        resetFeedback()
      }, 700)
    } catch (err) {
      setStatus('FAILED')
      setErrorMessage(
        err instanceof Error ? err.message : 'Falha ao importar o vídeo do YouTube.',
      )
    }
  }

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal-dialog">
        <div className="modal-header">
          <div>
            <h2>Adicionar fonte de estudo</h2>
            <p className="subtitle">Bloco: {blockTitle}</p>
          </div>
          <button
            type="button"
            className="modal-close-btn"
            aria-label="Fechar modal"
            onClick={() => {
              if (status !== 'PROCESSING') {
                onClose()
                resetFeedback()
              }
            }}
          >
            ×
          </button>
        </div>

        <nav className="source-tabs" aria-label="Tipo de fonte">
          <button
            type="button"
            className={`source-tab-btn ${activeTab === 'FILE' ? 'active' : ''}`}
            disabled={status === 'PROCESSING'}
            onClick={() => switchTab('FILE')}
          >
            Upload de Arquivo (PDF, DOCX, TXT, MD)
          </button>
          <button
            type="button"
            className={`source-tab-btn ${activeTab === 'TEXT' ? 'active' : ''}`}
            disabled={status === 'PROCESSING'}
            onClick={() => switchTab('TEXT')}
          >
            Texto / Notas manuais
          </button>
          <button
            type="button"
            className={`source-tab-btn ${activeTab === 'YOUTUBE' ? 'active' : ''}`}
            disabled={status === 'PROCESSING'}
            onClick={() => switchTab('YOUTUBE')}
          >
            Vídeo do YouTube
          </button>
        </nav>

        <div className="modal-body">
          {activeTab === 'FILE' && (
            <form onSubmit={(e) => void submitFile(e)} className="source-form">
              <div className="file-drop-zone">
                <label className="file-drop-label">
                  <span className="file-drop-icon">📁</span>
                  <strong>Escolha ou arraste um arquivo</strong>
                  <span className="file-drop-hint">
                    Suporta PDF, DOCX, TXT e Markdown (máx. 10 MB)
                  </span>
                  <input
                    type="file"
                    className="file-input-hidden"
                    accept=".pdf,.docx,.txt,.md,text/plain,text/markdown,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    disabled={status === 'PROCESSING'}
                    onChange={handleFileChange}
                  />
                </label>
                {selectedFile && (
                  <div className="selected-file-badge">
                    <span>📄 {selectedFile.name}</span>
                    <small>({(selectedFile.size / 1024).toFixed(1)} KB)</small>
                  </div>
                )}
              </div>

              <div className="form-actions">
                <button
                  type="submit"
                  disabled={!selectedFile || status === 'PROCESSING'}
                  className="btn-primary"
                >
                  {status === 'PROCESSING' ? 'Processando arquivo...' : 'Importar arquivo'}
                </button>
                <button
                  type="button"
                  className="secondary"
                  disabled={status === 'PROCESSING'}
                  onClick={onClose}
                >
                  Cancelar
                </button>
              </div>
            </form>
          )}

          {activeTab === 'TEXT' && (
            <form onSubmit={(e) => void submitText(e)} className="source-form">
              <label>
                Título do material
                <input
                  type="text"
                  required
                  maxLength={160}
                  placeholder="Ex: Resumo de Princípios SOLID"
                  value={textTitle}
                  disabled={status === 'PROCESSING'}
                  onChange={(e) => setTextTitle(e.target.value)}
                />
              </label>

              <label>
                Conteúdo textual
                <textarea
                  required
                  rows={8}
                  maxLength={100000}
                  placeholder="Cole ou digite aqui notas, transcrições, artigos ou trechos de estudo..."
                  value={textContent}
                  disabled={status === 'PROCESSING'}
                  onChange={(e) => setTextContent(e.target.value)}
                />
              </label>

              <div className="form-actions">
                <button
                  type="submit"
                  disabled={status === 'PROCESSING'}
                  className="btn-primary"
                >
                  {status === 'PROCESSING' ? 'Salvando...' : 'Criar fonte de texto'}
                </button>
                <button
                  type="button"
                  className="secondary"
                  disabled={status === 'PROCESSING'}
                  onClick={onClose}
                >
                  Cancelar
                </button>
              </div>
            </form>
          )}

          {activeTab === 'YOUTUBE' && (
            <form onSubmit={(e) => void submitYoutube(e)} className="source-form">
              <label>
                URL do vídeo do YouTube
                <input
                  type="url"
                  required
                  maxLength={1000}
                  placeholder="Ex: https://www.youtube.com/watch?v=dQw4w9WgXcQ"
                  value={youtubeUrl}
                  disabled={status === 'PROCESSING'}
                  onChange={(e) => setYoutubeUrl(e.target.value)}
                />
              </label>
              <p className="input-hint">
                A transcrição/legenda pública disponível no vídeo será extraída automaticamente e
                vinculada como fonte deste bloco.
              </p>

              <div className="form-actions">
                <button
                  type="submit"
                  disabled={status === 'PROCESSING'}
                  className="btn-primary"
                >
                  {status === 'PROCESSING' ? 'Processando transcrição...' : 'Importar vídeo'}
                </button>
                <button
                  type="button"
                  className="secondary"
                  disabled={status === 'PROCESSING'}
                  onClick={onClose}
                >
                  Cancelar
                </button>
              </div>
            </form>
          )}

          {statusMessage && (
            <div
              className={`source-feedback-banner ${
                status === 'COMPLETED'
                  ? 'success'
                  : status === 'PROCESSING'
                  ? 'info'
                  : ''
              }`}
            >
              {status === 'PROCESSING' && <span className="spinner" />}
              <span>{statusMessage}</span>
            </div>
          )}

          {errorMessage && (
            <div className="source-feedback-banner error">
              <span>⚠️ {errorMessage}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
