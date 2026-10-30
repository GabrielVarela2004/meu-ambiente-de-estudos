import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { request } from '../api/http'
import { importMaterialFile, toggleMaterialChat } from '../api/materials'
import { CheckCircleIcon, FileTextIcon, FolderIcon, PlusCircleIcon, SearchIcon, SparklesIcon } from '../components/icons'
import type { StudyBlock } from './StudyBlocksPage'
import '../styles/pages/add-content.css'

type Props = {
  blocks: StudyBlock[]
  selectedBlockId: string | null
  onSelectBlock: (id: string | null) => void
  onSourceAdded: (blockId: string) => void
  onNavigateToBlock: (blockId: string) => void
}

type TabMode = 'TEXT' | 'FILE' | 'WEB' | 'YOUTUBE'

type CreatedMaterial = {
  id: string
  title: string
  content: string
  enabledForChat: boolean
}

// Limites do backend para PUT /materials/{id} (StudyMaterialRequest).
const MAX_TITLE_LENGTH = 160
const MAX_EDITABLE_CONTENT_LENGTH = 100000

/**
 * As rotas de criação (texto, arquivo e YouTube) não recebem título personalizado
 * nem a opção de chat. Aplica essas escolhas depois, usando as rotas de edição.
 */
async function applySourceOptions(
  blockId: string,
  material: CreatedMaterial,
  options: { title?: string; enabledForChat: boolean },
) {
  const title = options.title?.trim().slice(0, MAX_TITLE_LENGTH)
  if (title && title !== material.title && material.content.length <= MAX_EDITABLE_CONTENT_LENGTH) {
    await request(`/api/study-blocks/${blockId}/materials/${material.id}`, {
      method: 'PUT',
      body: JSON.stringify({ title, content: material.content }),
    })
  }
  if (options.enabledForChat !== material.enabledForChat) {
    await toggleMaterialChat(blockId, material.id)
  }
}

export function AddContentPage({
  blocks,
  selectedBlockId,
  onSelectBlock,
  onSourceAdded,
  onNavigateToBlock,
}: Props) {
  const [activeTab, setActiveTab] = useState<TabMode>('TEXT')
  const [targetBlockId, setTargetBlockId] = useState<string>(
    selectedBlockId || (blocks.length > 0 ? blocks[0].id : '')
  )

  // Modo Texto
  const [textTitle, setTextTitle] = useState('')
  const [textContent, setTextContent] = useState('')
  const [textEnabledForChat, setTextEnabledForChat] = useState(true)

  // Modo Arquivo
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [fileTitle, setFileTitle] = useState('')
  const [fileEnabledForChat, setFileEnabledForChat] = useState(true)

  // Modo YouTube
  const [ytUrl, setYtUrl] = useState('')
  const [ytTitle, setYtTitle] = useState('')
  const [ytEnabledForChat, setYtEnabledForChat] = useState(true)

  // Modo Página Web
  const [webUrl, setWebUrl] = useState('')
  const [webTitle, setWebTitle] = useState('')
  const [webEnabledForChat, setWebEnabledForChat] = useState(true)

  const [submitting, setSubmitting] = useState(false)
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  const activeBlock = blocks.find((b) => b.id === targetBlockId)

  // Contadores de palavras e caracteres para texto
  const textWords = textContent.trim() ? textContent.trim().split(/\s+/).length : 0
  const textChars = textContent.length

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] || null
    setSelectedFile(file)
    if (file && !fileTitle) {
      // Remove extensão do arquivo para sugerir título
      const cleanName = file.name.replace(/\.[^/.]+$/, '').replace(/[-_]/g, ' ')
      setFileTitle(cleanName)
    }
  }

  const handleSubmitText = async (e: FormEvent) => {
    e.preventDefault()
    if (!targetBlockId) {
      setFeedback({ type: 'error', message: 'Selecione um bloco de estudo de destino.' })
      return
    }
    if (!textTitle.trim() || !textContent.trim()) {
      setFeedback({ type: 'error', message: 'Título e conteúdo são obrigatórios.' })
      return
    }

    setSubmitting(true)
    setFeedback(null)
    try {
      const created = await request<CreatedMaterial>(`/api/study-blocks/${targetBlockId}/materials`, {
        method: 'POST',
        body: JSON.stringify({
          title: textTitle.trim(),
          content: textContent.trim(),
        }),
      })
      await applySourceOptions(targetBlockId, created, { enabledForChat: textEnabledForChat })
      setFeedback({
        type: 'success',
        message: `Fonte canônica "${textTitle}" adicionada com sucesso ao bloco!`,
      })
      setTextTitle('')
      setTextContent('')
      onSourceAdded(targetBlockId)
    } catch {
      setFeedback({ type: 'error', message: 'Erro ao cadastrar texto como fonte.' })
    } finally {
      setSubmitting(false)
    }
  }

  const handleSubmitFile = async (e: FormEvent) => {
    e.preventDefault()
    if (!targetBlockId) {
      setFeedback({ type: 'error', message: 'Selecione um bloco de estudo de destino.' })
      return
    }
    if (!selectedFile) {
      setFeedback({ type: 'error', message: 'Selecione um arquivo PDF, DOCX, PPTX, Markdown, TXT ou de código.' })
      return
    }

    setSubmitting(true)
    setFeedback(null)
    try {
      const created = await importMaterialFile<CreatedMaterial>(targetBlockId, selectedFile)
      await applySourceOptions(targetBlockId, created, { title: fileTitle, enabledForChat: fileEnabledForChat })

      setFeedback({
        type: 'success',
        message: `Arquivo "${selectedFile.name}" enviado e processado com sucesso!`,
      })
      setSelectedFile(null)
      setFileTitle('')
      onSourceAdded(targetBlockId)
    } catch {
      setFeedback({
        type: 'error',
        message: 'Falha ao processar o arquivo. Formatos aceitos: .pdf, .docx, .pptx, .md, .txt e arquivos de código.',
      })
    } finally {
      setSubmitting(false)
    }
  }

  const handleSubmitWeb = async (e: FormEvent) => {
    e.preventDefault()
    if (!targetBlockId) {
      setFeedback({ type: 'error', message: 'Selecione um bloco de estudo de destino.' })
      return
    }
    if (!webUrl.trim()) {
      setFeedback({ type: 'error', message: 'Informe o endereço da página.' })
      return
    }

    setSubmitting(true)
    setFeedback(null)
    try {
      const created = await request<CreatedMaterial>(`/api/study-blocks/${targetBlockId}/materials/web`, {
        method: 'POST',
        body: JSON.stringify({ url: webUrl.trim() }),
      })
      await applySourceOptions(targetBlockId, created, { title: webTitle, enabledForChat: webEnabledForChat })
      setFeedback({ type: 'success', message: `Página "${webTitle.trim() || created.title}" importada como fonte!` })
      setWebUrl('')
      setWebTitle('')
      onSourceAdded(targetBlockId)
    } catch (err) {
      setFeedback({
        type: 'error',
        message: err instanceof Error && err.message ? err.message : 'Não foi possível importar a página.',
      })
    } finally {
      setSubmitting(false)
    }
  }

  const handleSubmitYoutube = async (e: FormEvent) => {
    e.preventDefault()
    if (!targetBlockId) {
      setFeedback({ type: 'error', message: 'Selecione um bloco de estudo de destino.' })
      return
    }
    if (!ytUrl.trim()) {
      setFeedback({ type: 'error', message: 'Informe a URL do vídeo do YouTube.' })
      return
    }

    setSubmitting(true)
    setFeedback(null)
    try {
      const created = await request<CreatedMaterial>(`/api/study-blocks/${targetBlockId}/materials/youtube`, {
        method: 'POST',
        body: JSON.stringify({ url: ytUrl.trim() }),
      })
      await applySourceOptions(targetBlockId, created, { title: ytTitle, enabledForChat: ytEnabledForChat })
      setFeedback({
        type: 'success',
        message: 'Vídeo do YouTube importado! As legendas/transcrição estão disponíveis como fonte.',
      })
      setYtUrl('')
      setYtTitle('')
      onSourceAdded(targetBlockId)
    } catch {
      setFeedback({
        type: 'error',
        message: 'Não foi possível extrair a transcrição do vídeo. Certifique-se de que o vídeo possui legendas ativas.',
      })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page-wrapper max-w-4xl">
      {/* Header */}
      <div className="page-header-flex">
        <div>
          <div className="flex-inline-gap">
            <h1 className="page-main-title">Adicionar Conteúdo</h1>
            <span className="badge-novo">NOVO</span>
          </div>
          <p className="page-main-subtitle">
            Cadastre novos materiais e fontes canônicas com suporte a extração automática de texto para alimentar o chat, as questões e os exercícios.
          </p>
        </div>
      </div>

      {/* Seletor de Bloco de Destino */}
      <div className="card mb-6">
        <label htmlFor="target-block-select" className="form-label-header">
          <FolderIcon className="w-4 h-4 text-emerald-400" />
          <span>Bloco de Estudo de Destino *</span>
        </label>
        {blocks.length === 0 ? (
          <div className="alert-warning-banner">
            Você ainda não possui blocos de estudo cadastrados. Crie um bloco primeiro na aba "Blocos de Estudo".
          </div>
        ) : (
          <div className="flex-gap-center">
            <select
              id="target-block-select"
              className="form-select flex-1"
              value={targetBlockId}
              onChange={(e) => {
                setTargetBlockId(e.target.value)
                onSelectBlock(e.target.value)
              }}
            >
              {blocks.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.title} {b.status === 'ARCHIVED' ? '(Arquivado)' : ''}
                </option>
              ))}
            </select>
            {activeBlock && (
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={() => onNavigateToBlock(activeBlock.id)}
              >
                Ver Bloco
              </button>
            )}
          </div>
        )}
      </div>

      {/* Tabs de Formato */}
      <div className="content-type-tabs">
        <button
          type="button"
          className={`content-type-tab-btn ${activeTab === 'TEXT' ? 'active' : ''}`}
          onClick={() => setActiveTab('TEXT')}
        >
          <FileTextIcon className="w-4 h-4" />
          <span>Texto Canônico / Notas</span>
        </button>

        <button
          type="button"
          className={`content-type-tab-btn ${activeTab === 'FILE' ? 'active' : ''}`}
          onClick={() => setActiveTab('FILE')}
        >
          <PlusCircleIcon className="w-4 h-4" />
          <span>Upload de Arquivo (PDF, DOCX, PPTX, código…)</span>
        </button>

        <button
          type="button"
          className={`content-type-tab-btn ${activeTab === 'WEB' ? 'active' : ''}`}
          onClick={() => setActiveTab('WEB')}
        >
          <SearchIcon className="w-4 h-4" />
          <span>Página Web (link)</span>
        </button>

        <button
          type="button"
          className={`content-type-tab-btn ${activeTab === 'YOUTUBE' ? 'active' : ''}`}
          onClick={() => setActiveTab('YOUTUBE')}
        >
          <SparklesIcon className="w-4 h-4" />
          <span>Vídeo do YouTube (Transcrições)</span>
        </button>
      </div>

      {/* Feedback Alert */}
      {feedback && (
        <div className={`alert-banner ${feedback.type === 'success' ? 'success' : 'error'}`}>
          {feedback.type === 'success' && <CheckCircleIcon className="w-5 h-5 flex-shrink-0" />}
          <span>{feedback.message}</span>
        </div>
      )}

      {/* Form 1: Texto Canônico */}
      {activeTab === 'TEXT' && (
        <form onSubmit={handleSubmitText} className="card source-form-card">
          <div className="form-field-group">
            <label htmlFor="text-title">Título da Fonte / Material *</label>
            <input
              id="text-title"
              type="text"
              className="form-input"
              placeholder="Ex: Introdução ao Circuit Breaker e Estados de Transição..."
              value={textTitle}
              onChange={(e) => setTextTitle(e.target.value)}
              required
            />
          </div>

          <div className="form-field-group">
            <div className="flex-between">
              <label htmlFor="text-content">Conteúdo Textual *</label>
              <div className="char-word-pills">
                <span>{textWords} palavras</span>
                <span>{textChars} caracteres</span>
              </div>
            </div>
            <textarea
              id="text-content"
              className="form-textarea font-mono text-sm"
              placeholder="Cole aqui o texto, documentação, resumo de artigo ou notas de estudo..."
              value={textContent}
              onChange={(e) => setTextContent(e.target.value)}
              rows={12}
              required
            />
          </div>

          <div className="form-checkbox-row">
            <label className="checkbox-label-styled">
              <input
                type="checkbox"
                checked={textEnabledForChat}
                onChange={(e) => setTextEnabledForChat(e.target.checked)}
              />
              <span className="checkbox-text">
                Habilitar esta fonte para inclusão no Chat Contextual do Bloco
              </span>
            </label>
          </div>

          <div className="form-footer-actions">
            <button
              type="submit"
              className="btn btn-primary"
              disabled={submitting || !blocks.length}
            >
              {submitting ? 'Salvando Fonte...' : 'Adicionar Fonte de Texto'}
            </button>
          </div>
        </form>
      )}

      {/* Form 2: Upload de Arquivo */}
      {activeTab === 'FILE' && (
        <form onSubmit={handleSubmitFile} className="card source-form-card">
          <div className="form-field-group">
            <label htmlFor="file-upload-input">Selecione o Arquivo (.pdf, .docx, .md, .txt) *</label>
            <div className="file-dropzone">
              <input
                id="file-upload-input"
                type="file"
                accept=".pdf,.docx,.pptx,.md,.txt,.java,.kt,.py,.js,.jsx,.ts,.tsx,.c,.h,.cpp,.hpp,.cs,.go,.rb,.php,.rs,.swift,.scala,.sql,.sh,.ps1,.html,.css,.scss,.json,.yml,.yaml,.xml,.gradle,.properties,.ipynb"
                onChange={handleFileChange}
                className="file-hidden-input"
              />
              <div className="dropzone-label">
                <PlusCircleIcon className="w-8 h-8 text-emerald-400 mb-2" />
                {selectedFile ? (
                  <div>
                    <strong className="text-emerald-400">{selectedFile.name}</strong>
                    <p className="text-xs text-muted">
                      {(selectedFile.size / 1024).toFixed(1)} KB — clique para trocar o arquivo
                    </p>
                  </div>
                ) : (
                  <div>
                    <strong>Clique para escolher ou arraste o arquivo aqui</strong>
                    <p className="text-xs text-muted">PDF, DOCX e PPTX (até 10 MB), Markdown, TXT e código-fonte (.java, .py, .ts, .sql…, até 2 MB)</p>
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="form-field-group">
            <label htmlFor="file-title">Título Customizado (opcional)</label>
            <input
              id="file-title"
              type="text"
              className="form-input"
              placeholder="Deixe em branco para usar o nome do arquivo..."
              value={fileTitle}
              onChange={(e) => setFileTitle(e.target.value)}
            />
          </div>

          <div className="form-checkbox-row">
            <label className="checkbox-label-styled">
              <input
                type="checkbox"
                checked={fileEnabledForChat}
                onChange={(e) => setFileEnabledForChat(e.target.checked)}
              />
              <span className="checkbox-text">
                Habilitar o conteúdo deste arquivo para o Chat do Bloco
              </span>
            </label>
          </div>

          <div className="form-footer-actions">
            <button
              type="submit"
              className="btn btn-primary"
              disabled={submitting || !selectedFile || !blocks.length}
            >
              {submitting ? 'Processando e Extraindo Texto...' : 'Fazer Upload e Processar'}
            </button>
          </div>
        </form>
      )}

      {/* Form 3: Página Web */}
      {activeTab === 'WEB' && (
        <form onSubmit={handleSubmitWeb} className="card source-form-card">
          <div className="form-field-group">
            <label htmlFor="web-url">Endereço da página *</label>
            <input
              id="web-url"
              type="url"
              className="form-input"
              placeholder="https://docs.oracle.com/javase/tutorial/java/IandI/polymorphism.html"
              value={webUrl}
              onChange={(e) => setWebUrl(e.target.value)}
              required
            />
            <p className="form-hint">
              O texto principal da página (artigo ou conteúdo) é extraído; menus, rodapés e scripts são descartados.
              Páginas que exigem login ou dependem de JavaScript podem não ter texto legível.
            </p>
          </div>

          <div className="form-field-group">
            <label htmlFor="web-title">Título Customizado (opcional)</label>
            <input
              id="web-title"
              type="text"
              className="form-input"
              placeholder="Por padrão, usa o título da página"
              value={webTitle}
              onChange={(e) => setWebTitle(e.target.value)}
            />
          </div>

          <div className="form-checkbox-row">
            <label className="checkbox-label-styled">
              <input
                type="checkbox"
                checked={webEnabledForChat}
                onChange={(e) => setWebEnabledForChat(e.target.checked)}
              />
              <span className="checkbox-text">Habilitar esta página para o Chat do Bloco</span>
            </label>
          </div>

          <div className="form-footer-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting || !webUrl.trim() || !blocks.length}>
              {submitting ? 'Baixando e extraindo o texto...' : 'Importar Página'}
            </button>
          </div>
        </form>
      )}

      {/* Form 4: Vídeo do YouTube */}
      {activeTab === 'YOUTUBE' && (
        <form onSubmit={handleSubmitYoutube} className="card source-form-card">
          <div className="form-field-group">
            <label htmlFor="yt-url">URL do Vídeo do YouTube *</label>
            <input
              id="yt-url"
              type="url"
              className="form-input"
              placeholder="https://www.youtube.com/watch?v=... ou https://youtu.be/..."
              value={ytUrl}
              onChange={(e) => setYtUrl(e.target.value)}
              required
            />
          </div>

          <div className="form-grid-2col">
            <div className="form-field-group">
              <label htmlFor="yt-title">Título Customizado (opcional)</label>
              <input
                id="yt-title"
                type="text"
                className="form-input"
                placeholder="Ex: Aula 03 - Padrões de Resiliência..."
                value={ytTitle}
                onChange={(e) => setYtTitle(e.target.value)}
              />
            </div>

            <div className="form-field-group">
              <span className="form-label-text">Idioma da Transcrição</span>
              <p className="form-hint">
                Escolhido automaticamente entre as legendas do vídeo: português, depois inglês e espanhol.
              </p>
            </div>
          </div>

          <div className="form-checkbox-row">
            <label className="checkbox-label-styled">
              <input
                type="checkbox"
                checked={ytEnabledForChat}
                onChange={(e) => setYtEnabledForChat(e.target.checked)}
              />
              <span className="checkbox-text">
                Habilitar a transcrição deste vídeo para o Chat do Bloco
              </span>
            </label>
          </div>

          <div className="form-footer-actions">
            <button
              type="submit"
              className="btn btn-primary"
              disabled={submitting || !ytUrl.trim() || !blocks.length}
            >
              {submitting ? 'Buscando Legendas e Transcrevendo...' : 'Importar Transcrição do YouTube'}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
