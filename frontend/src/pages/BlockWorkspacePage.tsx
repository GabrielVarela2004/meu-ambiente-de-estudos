import { useCallback, useEffect, useState } from 'react'
import { request } from '../api/http'
import {
  BookOpenIcon,
  ChevronLeftIcon,
  CodeIcon,
  FileTextIcon,
  NetworkIcon,
  PlusCircleIcon,
} from '../components/icons'
import type { StudyMaterialItem } from '../features/sources/AddSourceModal'
import type { StudyBlock } from './StudyBlocksPage'

type Props = {
  block: StudyBlock
  onBack: () => void
  onNavigateToAddContent: (blockId: string) => void
  onNavigateToPractical: () => void
  onNavigateToTheoretical: () => void
  onNavigateToTopics: () => void
}

const sourceTypeLabels: Record<string, string> = {
  TEXT: 'Texto',
  FILE_TXT: 'Arquivo TXT',
  FILE_MD: 'Markdown',
  FILE_PDF: 'PDF',
  FILE_DOCX: 'Word (DOCX)',
  YOUTUBE: 'Vídeo YouTube',
}

export function BlockWorkspacePage({
  block,
  onBack,
  onNavigateToAddContent,
  onNavigateToPractical,
  onNavigateToTheoretical,
  onNavigateToTopics,
}: Props) {
  const [materials, setMaterials] = useState<StudyMaterialItem[]>([])
  const [loading, setLoading] = useState(false)
  const [selectedMaterialId, setSelectedMaterialId] = useState<string | null>(null)
  const [togglingChatId, setTogglingChatId] = useState<string | null>(null)

  const loadMaterials = useCallback(async () => {
    setLoading(true)
    try {
      const data = await request<StudyMaterialItem[]>(
        `/api/study-blocks/${block.id}/materials?includeArchived=false`
      )
      setMaterials(data)
      if (data.length > 0 && !selectedMaterialId) {
        setSelectedMaterialId(data[0].id)
      }
    } catch {
      // Ignora erro de carregamento
    } finally {
      setLoading(false)
    }
  }, [block.id, selectedMaterialId])

  useEffect(() => {
    void loadMaterials()
  }, [loadMaterials])

  const handleToggleChat = async (mat: StudyMaterialItem, e: React.MouseEvent) => {
    e.stopPropagation()
    setTogglingChatId(mat.id)
    try {
      const newEnabled = !(mat.enabledForChat ?? true)
      await request(`/api/study-blocks/${block.id}/materials/${mat.id}/chat-toggle`, {
        method: 'PATCH',
        body: JSON.stringify({ enabledForChat: newEnabled }),
      })
      setMaterials((prev) =>
        prev.map((m) => (m.id === mat.id ? { ...m, enabledForChat: newEnabled } : m))
      )
    } catch {
      alert('Erro ao alterar inclusão da fonte no chat.')
    } finally {
      setTogglingChatId(null)
    }
  }

  const selectedMaterial = materials.find((m) => m.id === selectedMaterialId) || materials[0]

  return (
    <div className="page-wrapper">
      {/* Barra de Navegação Superior do Bloco */}
      <div className="block-nav-header">
        <button type="button" className="btn btn-ghost btn-sm" onClick={onBack}>
          <ChevronLeftIcon className="w-4 h-4" />
          <span>Voltar para Blocos</span>
        </button>

        <div className="block-quick-shortcuts">
          <button type="button" className="btn btn-secondary btn-sm" onClick={onNavigateToPractical}>
            <CodeIcon className="w-4 h-4 text-emerald-400" />
            <span>Exercícios Práticos</span>
          </button>
          <button type="button" className="btn btn-secondary btn-sm" onClick={onNavigateToTheoretical}>
            <FileTextIcon className="w-4 h-4 text-emerald-400" />
            <span>Questões Teóricas</span>
          </button>
          <button type="button" className="btn btn-secondary btn-sm" onClick={onNavigateToTopics}>
            <NetworkIcon className="w-4 h-4 text-emerald-400" />
            <span>Mapa de Tópicos</span>
          </button>
          <button
            type="button"
            className="btn btn-primary btn-sm"
            onClick={() => onNavigateToAddContent(block.id)}
          >
            <PlusCircleIcon className="w-4 h-4" />
            <span>Adicionar Fonte</span>
          </button>
        </div>
      </div>

      {/* Info do Bloco */}
      <div className="card block-hero-card mb-6">
        <div className="flex-between">
          <div>
            <span className="badge badge-emerald mb-2">Caderno Aberto</span>
            <h1 className="text-2xl font-bold">{block.title}</h1>
            {block.description && <p className="text-secondary mt-1">{block.description}</p>}
          </div>
          <div className="block-counts-pill">
            <span>{materials.length} Fontes Canônicas</span>
          </div>
        </div>

        {block.learningObjective && (
          <div className="block-objective-box mt-3">
            <strong>Objetivo de Aprendizagem:</strong> {block.learningObjective}
          </div>
        )}
      </div>

      {/* Layout Interno NotebookLM: Fontes à Esquerda, Leitor no Centro */}
      <div className="notebook-workspace-grid">
        {/* Painel de Fontes */}
        <div className="card sources-drawer-card">
          <div className="sources-drawer-header">
            <h3>Fontes do Bloco ({materials.length})</h3>
            <button
              type="button"
              className="btn btn-primary btn-xs"
              onClick={() => onNavigateToAddContent(block.id)}
            >
              + Adicionar
            </button>
          </div>

          {loading ? (
            <p className="text-xs text-muted p-4">Carregando fontes...</p>
          ) : materials.length === 0 ? (
            <div className="empty-sources-box">
              <p>Nenhuma fonte cadastrada neste bloco ainda.</p>
              <button
                type="button"
                className="btn btn-primary btn-sm mt-2"
                onClick={() => onNavigateToAddContent(block.id)}
              >
                Cadastrar Primeira Fonte
              </button>
            </div>
          ) : (
            <div className="sources-scroll-list">
              {materials.map((mat) => {
                const isSelected = selectedMaterial?.id === mat.id
                const isChatActive = mat.enabledForChat ?? true
                const isToggling = togglingChatId === mat.id

                return (
                  <div
                    key={mat.id}
                    className={`source-card-item ${isSelected ? 'selected' : ''}`}
                    onClick={() => setSelectedMaterialId(mat.id)}
                  >
                    <div className="source-item-header">
                      <span className="source-type-chip">
                        {sourceTypeLabels[mat.sourceType || 'TEXT'] || mat.sourceType}
                      </span>
                      <button
                        type="button"
                        className={`source-chat-toggle ${isChatActive ? 'enabled' : 'disabled'}`}
                        onClick={(e) => void handleToggleChat(mat, e)}
                        disabled={isToggling}
                        title={
                          isChatActive
                            ? 'Fonte incluída no Chat (clique para pausar)'
                            : 'Fonte pausada do Chat (clique para incluir)'
                        }
                      >
                        {isToggling ? '...' : isChatActive ? '💬 No Chat' : '🔇 Pausada'}
                      </button>
                    </div>

                    <h4 className="source-item-title">{mat.title}</h4>

                    <div className="source-item-meta">
                      <span>{mat.wordCount ?? 0} palavras</span>
                      <span>•</span>
                      <span>{mat.charCount ?? 0} chars</span>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Leitor de Material */}
        <div className="card material-viewer-card">
          {selectedMaterial ? (
            <div className="material-reader">
              <div className="reader-header">
                <div>
                  <span className="badge badge-subtle mb-1">
                    {sourceTypeLabels[selectedMaterial.sourceType || 'TEXT'] || selectedMaterial.sourceType}
                  </span>
                  <h2 className="text-xl font-bold">{selectedMaterial.title}</h2>
                </div>
                <div className="reader-stats">
                  <span>{selectedMaterial.wordCount ?? 0} palavras</span>
                  <span>{selectedMaterial.charCount ?? 0} caracteres</span>
                </div>
              </div>

              <div className="reader-content-body">
                <pre className="content-pre-wrap">{selectedMaterial.content}</pre>
              </div>
            </div>
          ) : (
            <div className="empty-state-box">
              <BookOpenIcon className="empty-icon" />
              <h3>Nenhuma fonte selecionada</h3>
              <p>Selecione um dos materiais da lista à esquerda para ler seu conteúdo canônico.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
