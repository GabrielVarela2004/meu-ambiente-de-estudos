import { useCallback, useState } from 'react'
import { request } from '../../api/http'
import { runCareerAgent } from '../../api/career'
import {
  BriefcaseIcon,
  ChevronDownIcon,
  FilterIcon,
  SearchIcon,
  SparklesIcon,
} from '../../components/icons'
import '../../styles/pages/career.css'
import { useDeferredEffect } from '../../hooks/useDeferredEffect'

type JobItem = {
  id: string
  title: string
  company: string
  location: string
  modality: 'REMOTE' | 'HYBRID' | 'ON_SITE' | 'ANY'
  experienceLevel: string
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string
  url: string
  sourceName: string
  description: string
  tags: string | null
  status: 'DISCOVERED' | 'APPLIED' | 'INTERVIEWING' | 'OFFER' | 'REJECTED' | 'DISCARDED'
  compatibilityPercent: number
  compatibilityNote: string | null
  matchedRequirements?: string[]
  missingRequirements?: string[]
  publishedAt: string | null
  discoveredAt: string
  applicationNotes: string | null
  statusUpdatedAt: string | null
  studyBlockId: string | null
}

type JobAnalysis = {
  text: string
  source: string
  compatibilityPercent: number
  generatedAt: string
}

type JobStudyBlock = {
  studyBlockId: string
  title: string
  topics: string[]
  created: boolean
}

type Props = {
  onNavigateToPreferences?: () => void
}

export function CareerJobFeedPage({ onNavigateToPreferences }: Props) {
  const [jobs, setJobs] = useState<JobItem[]>([])
  const [loading, setLoading] = useState(true)
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [filterModality, setFilterModality] = useState<string>('ALL')
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState<'ALL' | JobItem['status']>('ALL')
  const [selectedJob, setSelectedJob] = useState<JobItem | null>(null)

  const loadJobs = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // O endpoint responde com uma página do Spring ({ content: [...] }); aceita também lista simples
      const data = await request<JobItem[] | { content?: JobItem[] }>('/api/career/jobs?size=50')
      setJobs(Array.isArray(data) ? data : data.content ?? [])
    } catch {
      setError('Não foi possível carregar as vagas.')
    } finally {
      setLoading(false)
    }
  }, [])

  useDeferredEffect(() => loadJobs(), [loadJobs])

  const handleRunAgent = async () => {
    setSearching(true)
    setError(null)
    try {
      await runCareerAgent()
      await loadJobs()
    } catch {
      setError('Erro ao acionar varredura do agente de vagas.')
    } finally {
      setSearching(false)
    }
  }

  const [notesDraft, setNotesDraft] = useState('')
  const [savingNotes, setSavingNotes] = useState(false)
  const [analysis, setAnalysis] = useState<JobAnalysis | null>(null)
  const [analyzing, setAnalyzing] = useState(false)
  const [creatingBlock, setCreatingBlock] = useState(false)
  const [blockMessage, setBlockMessage] = useState<string | null>(null)

  const selectJob = (job: JobItem | null) => {
    setSelectedJob(job)
    setNotesDraft(job?.applicationNotes ?? '')
    setAnalysis(null)
    setBlockMessage(null)
  }

  // Substitui a vaga na lista e no painel pela versão devolvida pelo backend
  const replaceJob = (updated: JobItem) => {
    setJobs((prev) => prev.map((j) => (j.id === updated.id ? updated : j)))
    setSelectedJob((prev) => (prev?.id === updated.id ? updated : prev))
  }

  // Sem "notes" no corpo, o backend mantém as anotações atuais
  const handleUpdateStatus = async (jobId: string, newStatus: JobItem['status'], notes?: string) => {
    try {
      replaceJob(await request<JobItem>(`/api/career/jobs/${jobId}/status`, {
        method: 'PATCH',
        body: JSON.stringify(notes === undefined ? { status: newStatus } : { status: newStatus, notes }),
      }))
    } catch {
      setError('Erro ao atualizar status da vaga.')
    }
  }

  const handleSaveNotes = async () => {
    if (!selectedJob) return
    setSavingNotes(true)
    await handleUpdateStatus(selectedJob.id, selectedJob.status, notesDraft)
    setSavingNotes(false)
  }

  const handleAnalyze = async () => {
    if (!selectedJob) return
    setAnalyzing(true)
    try {
      setAnalysis(await request<JobAnalysis>(`/api/career/jobs/${selectedJob.id}/analysis`, { method: 'POST' }))
    } catch {
      setError('Não foi possível analisar a vaga.')
    } finally {
      setAnalyzing(false)
    }
  }

  const handleCreateBlock = async () => {
    if (!selectedJob) return
    setCreatingBlock(true)
    try {
      const block = await request<JobStudyBlock>(`/api/career/jobs/${selectedJob.id}/study-block`, { method: 'POST' })
      replaceJob({ ...selectedJob, studyBlockId: block.studyBlockId })
      setBlockMessage(
        block.created
          ? `Bloco "${block.title}" criado com ${block.topics.length} tópico(s).`
          : `Esta vaga já tem o bloco "${block.title}".`,
      )
    } catch {
      setError('Não foi possível criar o bloco de estudo da vaga.')
    } finally {
      setCreatingBlock(false)
    }
  }

  const filteredJobs = jobs.filter((j) => {
    const matchesSearch =
      j.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      j.company.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (j.tags ?? '').toLowerCase().includes(searchTerm.toLowerCase())
    const matchesModality =
      filterModality === 'ALL' || j.modality === filterModality
    const matchesStatus =
      statusFilter === 'ALL' || j.status === statusFilter
    return matchesSearch && matchesModality && matchesStatus
  })

  const modalityLabel: Record<string, string> = {
    REMOTE: 'Remoto',
    HYBRID: 'Híbrido',
    ON_SITE: 'Presencial',
    ANY: 'Qualquer',
  }

  const statusBadge: Record<JobItem['status'], { label: string; className: string }> = {
    DISCOVERED: { label: 'Nova', className: 'badge-subtle' },
    APPLIED: { label: 'Candidatado', className: 'badge-success' },
    INTERVIEWING: { label: 'Em entrevista', className: 'badge-info' },
    OFFER: { label: 'Oferta', className: 'badge-success' },
    REJECTED: { label: 'Recusada', className: 'badge-warning' },
    DISCARDED: { label: 'Descartada', className: 'badge-danger' },
  }

  const compatibilityClass = (percent: number) =>
    percent >= 80 ? 'badge-success' : percent >= 50 ? 'badge-warning' : 'badge-subtle'

  const splitTags = (tags: string | null) =>
    (tags ?? '').split(',').map((t) => t.trim()).filter(Boolean)

  return (
    <div className="page-wrapper">
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Radar de Vagas</h1>
          <p className="page-main-subtitle">
            Vagas reais (Remotive e Arbeitnow) comparadas com os tópicos dos seus blocos de estudo.
          </p>
        </div>
        <div className="page-actions-group">
          {onNavigateToPreferences && (
            <button type="button" className="btn btn-secondary" onClick={onNavigateToPreferences}>
              Configurar Preferências
            </button>
          )}
          <button type="button" className="btn btn-primary" disabled={searching} onClick={() => void handleRunAgent()}>
            <SparklesIcon className="w-4 h-4" />
            {searching ? 'Buscando vagas...' : 'Buscar Novas Vagas Agora'}
          </button>
        </div>
      </div>

      {error && (
        <div className="error-banner">
          <span>{error}</span>
          <button type="button" className="banner-dismiss" aria-label="Fechar aviso" onClick={() => setError(null)}>✕</button>
        </div>
      )}

      {/* Filtros e Barra de Pesquisa */}
      <div className="card filter-bar-card">
        <div className="search-input-wrapper flex-1">
          <SearchIcon className="search-icon" />
          <input
            type="text"
            className="form-input search-input"
            aria-label="Buscar vagas"
            placeholder="Buscar por cargo, empresa, tecnologia (ex: Java, Spring, React)..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div className="job-filters-group">
          <div className="filter-select-wrapper">
            <FilterIcon className="filter-select-icon" />
            <select
              className="form-select filter-select"
              aria-label="Modalidade"
              value={filterModality}
              onChange={(e) => setFilterModality(e.target.value)}
            >
              <option value="ALL">Todas as modalidades</option>
              <option value="REMOTE">Apenas Remoto</option>
              <option value="HYBRID">Híbrido</option>
              <option value="ON_SITE">Presencial</option>
            </select>
            <ChevronDownIcon className="filter-select-arrow" />
          </div>

          <div className="filter-select-wrapper">
            <select
              className="form-select filter-select"
              aria-label="Status da candidatura"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)}
            >
              <option value="ALL">Todos os status</option>
              <option value="DISCOVERED">Novas</option>
              <option value="APPLIED">Candidatado</option>
              <option value="INTERVIEWING">Em entrevista</option>
              <option value="OFFER">Oferta</option>
              <option value="REJECTED">Recusadas</option>
              <option value="DISCARDED">Descartadas</option>
            </select>
            <ChevronDownIcon className="filter-select-arrow" />
          </div>
        </div>
      </div>

      {/* Conteúdo Principal: Lista de Vagas + Painel Lateral de Detalhes */}
      <div className={`job-feed-layout ${selectedJob ? 'has-detail' : ''}`}>
        <div className="job-list-column">
          {loading ? (
            <div className="card empty-state-box">
              <p>Carregando radar de vagas...</p>
            </div>
          ) : filteredJobs.length === 0 ? (
            <div className="card empty-state-box">
              <BriefcaseIcon className="empty-icon" />
              <h3>Nenhuma vaga encontrada</h3>
              <p>Não há vagas com os filtros selecionados ou o agente ainda não executou a varredura.</p>
              <button type="button" className="btn btn-primary" onClick={() => void handleRunAgent()}>
                Buscar Vagas Agora
              </button>
            </div>
          ) : (
            <div className="job-list-stack">
              {filteredJobs.map((job) => {
                const isSelected = selectedJob?.id === job.id
                const status = statusBadge[job.status] ?? statusBadge.DISCOVERED
                return (
                  <article
                    key={job.id}
                    className={`card job-card-item ${isSelected ? 'selected' : ''} ${job.status === 'DISCARDED' ? 'is-discarded' : ''}`}
                  >
                    <button
                      type="button"
                      className="job-card-open"
                      aria-pressed={isSelected}
                      onClick={() => selectJob(job)}
                    >
                      <div className="job-card-top">
                        <div className="job-card-heading">
                          <h3 className="job-card-title">{job.title}</h3>
                          <p className="job-card-company">
                            <strong>{job.company}</strong> · {job.location} ({modalityLabel[job.modality] ?? job.modality})
                          </p>
                        </div>
                        <span className={`badge ${compatibilityClass(job.compatibilityPercent)}`}>
                          {job.compatibilityPercent}% compatível
                        </span>
                      </div>

                      {splitTags(job.tags).length > 0 && (
                        <div className="tags-row">
                          {splitTags(job.tags).slice(0, 8).map((tag) => (
                            <span key={tag} className="tag">{tag}</span>
                          ))}
                        </div>
                      )}
                    </button>

                    <div className="job-card-footer">
                      <span className="text-xs text-muted">
                        {job.sourceName} · {new Date(job.publishedAt ?? job.discoveredAt).toLocaleDateString('pt-BR')}
                      </span>
                      <div className="job-card-actions">
                        <span className={`badge ${status.className}`}>{status.label}</span>
                        {job.status === 'DISCOVERED' && (
                          <button
                            type="button"
                            className="btn btn-secondary btn-xs"
                            onClick={() => void handleUpdateStatus(job.id, 'APPLIED')}
                          >
                            Marcar candidatado
                          </button>
                        )}
                      </div>
                    </div>
                  </article>
                )
              })}
            </div>
          )}
        </div>

        {/* Detalhes da Vaga Selecionada */}
        {selectedJob && (
          <aside className="card job-detail-card" aria-label="Detalhes da vaga">
            <div className="job-detail-header">
              <div>
                <h2>{selectedJob.title}</h2>
                <p className="job-detail-company">{selectedJob.company}</p>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => selectJob(null)}>
                ✕ Fechar
              </button>
            </div>

            <dl className="job-detail-facts">
              <div>
                <dt>Localização / Modelo</dt>
                <dd>{selectedJob.location} ({modalityLabel[selectedJob.modality] ?? selectedJob.modality})</dd>
              </div>
              <div>
                <dt>Nível de experiência</dt>
                <dd>{selectedJob.experienceLevel || 'Não informado'}</dd>
              </div>
              <div>
                <dt>Compatibilidade</dt>
                <dd>{selectedJob.compatibilityPercent}%</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{(statusBadge[selectedJob.status] ?? statusBadge.DISCOVERED).label}</dd>
              </div>
            </dl>

            {selectedJob.compatibilityNote && (
              <p className="job-detail-note">{selectedJob.compatibilityNote}</p>
            )}

            {(selectedJob.matchedRequirements?.length ?? 0) + (selectedJob.missingRequirements?.length ?? 0) > 0 && (
              <div className="job-detail-section">
                <h4>Requisitos x seus estudos</h4>
                <div className="tags-row">
                  {selectedJob.matchedRequirements?.map((r) => (
                    <span key={`m-${r}`} className="tag success">✓ {r}</span>
                  ))}
                  {selectedJob.missingRequirements?.map((r) => (
                    <span key={`x-${r}`} className="tag warning">✗ {r}</span>
                  ))}
                </div>
              </div>
            )}

            {splitTags(selectedJob.tags).length > 0 && (
              <div className="job-detail-section">
                <h4>Tecnologias e tags</h4>
                <div className="tags-row">
                  {splitTags(selectedJob.tags).map((tag) => (
                    <span key={tag} className="tag">{tag}</span>
                  ))}
                </div>
              </div>
            )}

            <div className="job-detail-section">
              <h4>Descrição da vaga</h4>
              <div className="job-detail-description">
                {selectedJob.description || 'Descrição completa disponível no link oficial da vaga.'}
              </div>
            </div>

            <div className="job-detail-section">
              <h4>Andamento da candidatura</h4>
              <div className="segmented" role="group" aria-label="Status da candidatura">
                {(Object.keys(statusBadge) as JobItem['status'][]).map((s) => (
                  <button
                    key={s}
                    type="button"
                    className={`nav-tab-btn ${selectedJob.status === s ? 'active' : ''}`}
                    aria-pressed={selectedJob.status === s}
                    onClick={() => void handleUpdateStatus(selectedJob.id, s)}
                  >
                    {statusBadge[s].label}
                  </button>
                ))}
              </div>
              {selectedJob.statusUpdatedAt && (
                <p className="text-xs text-muted">
                  Atualizado em {new Date(selectedJob.statusUpdatedAt).toLocaleDateString('pt-BR')}
                </p>
              )}
              <label className="form-label-block">
                <span className="form-label-text">Anotações da candidatura</span>
                <textarea
                  className="form-input"
                  rows={3}
                  maxLength={5000}
                  placeholder="Datas de entrevista, contatos, feedback recebido..."
                  value={notesDraft}
                  onChange={(e) => setNotesDraft(e.target.value)}
                />
              </label>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={savingNotes || notesDraft === (selectedJob.applicationNotes ?? '')}
                onClick={() => void handleSaveNotes()}
              >
                {savingNotes ? 'Salvando...' : 'Salvar anotações'}
              </button>
            </div>

            <div className="job-detail-section">
              <h4>Análise da vaga</h4>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={analyzing}
                onClick={() => void handleAnalyze()}
              >
                <SparklesIcon className="w-4 h-4" />
                {analyzing ? 'Analisando...' : analysis ? 'Analisar novamente' : 'Analisar vaga com meu perfil'}
              </button>
              {analysis && (
                <>
                  <p className="text-xs text-muted">
                    {analysis.source} · compatibilidade {analysis.compatibilityPercent}%
                  </p>
                  <div className="job-detail-description">{analysis.text}</div>
                </>
              )}
            </div>

            <div className="job-detail-section">
              <h4>Estudar para esta vaga</h4>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={creatingBlock}
                onClick={() => void handleCreateBlock()}
              >
                {creatingBlock
                  ? 'Criando bloco...'
                  : selectedJob.studyBlockId
                    ? 'Ver bloco criado para a vaga'
                    : 'Criar bloco de estudo com os requisitos'}
              </button>
              {blockMessage && <p className="text-xs text-muted">{blockMessage}</p>}
            </div>

            {selectedJob.url && (
              <a href={selectedJob.url} target="_blank" rel="noopener noreferrer" className="btn btn-primary btn-block">
                Abrir link oficial da vaga ↗
              </a>
            )}
          </aside>
        )}
      </div>
    </div>
  )
}
