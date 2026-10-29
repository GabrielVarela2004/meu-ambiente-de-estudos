import { useState } from 'react'
import type { FormEvent } from 'react'
import { FolderIcon, PlusCircleIcon, SearchIcon } from '../components/icons'

export type StudyBlock = {
  id: string
  title: string
  description: string | null
  learningObjective: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  updatedAt: string
}

type Props = {
  blocks: StudyBlock[]
  selectedBlockId: string | null
  onSelectBlock: (id: string | null) => void
  onOpenBlockWorkspace: (block: StudyBlock) => void
  onSaveBlock: (form: { title: string; description: string; learningObjective: string }, editingId: string | null) => Promise<void>
  onArchiveBlock: (id: string) => Promise<void>
  onUnarchiveBlock: (id: string) => Promise<void>
  onNavigateToAddContent: (blockId?: string) => void
  materialsCountByBlock: Record<string, number>
  questionsCountByBlock: Record<string, number>
}

export function StudyBlocksPage({
  blocks,
  selectedBlockId,
  onSelectBlock,
  onOpenBlockWorkspace,
  onSaveBlock,
  onArchiveBlock,
  onUnarchiveBlock,
  onNavigateToAddContent,
  materialsCountByBlock,
  questionsCountByBlock,
}: Props) {
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'ARCHIVED'>('ACTIVE')
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingBlock, setEditingBlock] = useState<StudyBlock | null>(null)
  const [form, setForm] = useState({ title: '', description: '', learningObjective: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleOpenCreate = () => {
    setEditingBlock(null)
    setForm({ title: '', description: '', learningObjective: '' })
    setError(null)
    setIsModalOpen(true)
  }

  const handleOpenEdit = (block: StudyBlock) => {
    setEditingBlock(block)
    setForm({
      title: block.title,
      description: block.description || '',
      learningObjective: block.learningObjective || '',
    })
    setError(null)
    setIsModalOpen(true)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!form.title.trim()) {
      setError('O título do bloco é obrigatório.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await onSaveBlock(form, editingBlock ? editingBlock.id : null)
      setIsModalOpen(false)
    } catch {
      setError('Erro ao salvar bloco de estudos.')
    } finally {
      setSaving(false)
    }
  }

  const filteredBlocks = blocks.filter((b) => {
    const matchesSearch =
      b.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (b.description && b.description.toLowerCase().includes(searchTerm.toLowerCase()))
    const matchesStatus =
      statusFilter === 'ALL' ||
      (statusFilter === 'ACTIVE' && b.status === 'ACTIVE') ||
      (statusFilter === 'ARCHIVED' && b.status === 'ARCHIVED')
    return matchesSearch && matchesStatus
  })

  return (
    <div className="page-wrapper">
      {/* Cabeçalho da Página */}
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Blocos de Estudo</h1>
          <p className="page-main-subtitle">
            Organize seus materiais canônicos, cadernos temáticos e planos de aprendizado no estilo NotebookLM.
          </p>
        </div>
        <div className="page-actions-group">
          <button type="button" className="btn btn-primary" onClick={handleOpenCreate}>
            <PlusCircleIcon className="w-4 h-4" />
            <span>Novo Bloco de Estudo</span>
          </button>
        </div>
      </div>

      {/* Barra de Filtros e Pesquisa */}
      <div className="toolbar-card">
        <div className="search-input-wrapper">
          <SearchIcon className="search-icon" />
          <input
            type="text"
            className="search-input"
            placeholder="Pesquisar bloco por título ou objetivo..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div className="filter-chips-group">
          <button
            type="button"
            className={`chip-btn ${statusFilter === 'ACTIVE' ? 'active' : ''}`}
            onClick={() => setStatusFilter('ACTIVE')}
          >
            Ativos
          </button>
          <button
            type="button"
            className={`chip-btn ${statusFilter === 'ALL' ? 'active' : ''}`}
            onClick={() => setStatusFilter('ALL')}
          >
            Todos
          </button>
          <button
            type="button"
            className={`chip-btn ${statusFilter === 'ARCHIVED' ? 'active' : ''}`}
            onClick={() => setStatusFilter('ARCHIVED')}
          >
            Arquivados
          </button>
        </div>
      </div>

      {/* Grid de Blocos */}
      {filteredBlocks.length === 0 ? (
        <div className="empty-state-box">
          <FolderIcon className="empty-icon" />
          <h3>Nenhum bloco de estudo encontrado</h3>
          <p>Crie seu primeiro bloco temático para começar a importar fontes canônicas e exercitar o conhecimento.</p>
          <button type="button" className="btn btn-primary" onClick={handleOpenCreate}>
            Criar Bloco Agora
          </button>
        </div>
      ) : (
        <div className="study-blocks-grid">
          {filteredBlocks.map((block) => {
            const isSelected = selectedBlockId === block.id
            const matCount = materialsCountByBlock[block.id] ?? 0
            const qCount = questionsCountByBlock[block.id] ?? 0

            return (
              <div
                key={block.id}
                className={`study-block-card ${isSelected ? 'selected' : ''} ${
                  block.status === 'ARCHIVED' ? 'archived' : ''
                }`}
              >
                <div className="block-card-header">
                  <div className="block-card-title-group">
                    <span className="block-type-pill">Caderno de Estudo</span>
                    <h2 className="block-card-title">{block.title}</h2>
                  </div>
                  <span className={`block-status-tag ${block.status.toLowerCase()}`}>
                    {block.status === 'ACTIVE' ? 'Ativo' : 'Arquivado'}
                  </span>
                </div>

                <div className="block-card-body">
                  {block.description && (
                    <p className="block-card-desc">{block.description}</p>
                  )}
                  {block.learningObjective && (
                    <div className="block-card-objective">
                      <span className="objective-label">Objetivo de Domínio:</span>
                      <p>{block.learningObjective}</p>
                    </div>
                  )}
                </div>

                <div className="block-card-stats-row">
                  <div className="block-stat-item">
                    <span className="stat-number">{matCount}</span>
                    <span className="stat-label">Fontes</span>
                  </div>
                  <div className="block-stat-item">
                    <span className="stat-number">{qCount}</span>
                    <span className="stat-label">Questões</span>
                  </div>
                  <div className="block-stat-item">
                    <span className="stat-number">
                      {new Date(block.updatedAt).toLocaleDateString('pt-BR')}
                    </span>
                    <span className="stat-label">Atualizado</span>
                  </div>
                </div>

                <div className="block-card-footer">
                  <button
                    type="button"
                    className="btn btn-primary btn-sm"
                    onClick={() => {
                      onSelectBlock(block.id)
                      onOpenBlockWorkspace(block)
                    }}
                  >
                    <span>Abrir Bloco</span>
                  </button>

                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => onNavigateToAddContent(block.id)}
                    title="Adicionar novas fontes a este bloco"
                  >
                    <span>+ Fonte</span>
                  </button>

                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => handleOpenEdit(block)}
                  >
                    Editar
                  </button>

                  {block.status === 'ACTIVE' ? (
                    <button
                      type="button"
                      className="btn btn-ghost-danger btn-sm"
                      onClick={() => void onArchiveBlock(block.id)}
                    >
                      Arquivar
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      onClick={() => void onUnarchiveBlock(block.id)}
                    >
                      Restaurar
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Modal de Criação / Edição */}
      {isModalOpen && (
        <div className="modal-backdrop" onClick={() => setIsModalOpen(false)}>
          <div className="modal-dialog-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{editingBlock ? 'Editar Bloco de Estudo' : 'Novo Bloco de Estudo'}</h2>
              <button type="button" className="modal-close-btn" onClick={() => setIsModalOpen(false)}>
                &times;
              </button>
            </div>

            <form onSubmit={handleSubmit} className="modal-form-body">
              {error && <div className="alert-error-banner">{error}</div>}

              <div className="form-field-group">
                <label htmlFor="block-form-title">Título do Bloco *</label>
                <input
                  id="block-form-title"
                  type="text"
                  className="form-input"
                  placeholder="Ex: Arquitetura de Microsserviços, Algoritmos Avançados..."
                  value={form.title}
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                  required
                />
              </div>

              <div className="form-field-group">
                <label htmlFor="block-form-desc">Descrição</label>
                <textarea
                  id="block-form-desc"
                  className="form-textarea"
                  placeholder="Descreva o escopo e os temas centrais abordados neste bloco..."
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  rows={3}
                />
              </div>

              <div className="form-field-group">
                <label htmlFor="block-form-obj">Objetivo de Aprendizagem</label>
                <textarea
                  id="block-form-obj"
                  className="form-textarea"
                  placeholder="O que você deseja ser capaz de fazer ou dominar ao concluir este bloco..."
                  value={form.learningObjective}
                  onChange={(e) => setForm({ ...form, learningObjective: e.target.value })}
                  rows={3}
                />
              </div>

              <div className="modal-footer-actions">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setIsModalOpen(false)}
                  disabled={saving}
                >
                  Cancelar
                </button>
                <button type="submit" className="btn btn-primary" disabled={saving}>
                  {saving ? 'Salvando...' : editingBlock ? 'Salvar Alterações' : 'Criar Bloco'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
