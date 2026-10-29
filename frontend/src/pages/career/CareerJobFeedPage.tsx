import { useCallback, useEffect, useState } from 'react'
import { request } from '../../api/http'
import {
  BriefcaseIcon,
  ChevronDownIcon,
  FilterIcon,
  SearchIcon,
  SparklesIcon,
} from '../../components/icons'

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
  tags: string
  status: 'DISCOVERED' | 'APPLIED' | 'DISCARDED' | 'INTERVIEWING'
  matchScore: number
  postedAt: string
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
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'DISCOVERED' | 'APPLIED' | 'DISCARDED'>('ALL')
  const [selectedJob, setSelectedJob] = useState<JobItem | null>(null)

  const loadJobs = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // O endpoint responde com uma página do Spring ({ content: [...] }); aceita também lista simples
      const data = await request<JobItem[] | { content?: JobItem[] }>('/api/career/jobs?limit=50')
      setJobs(Array.isArray(data) ? data : data.content ?? [])
    } catch {
      setError('Não foi possível carregar as vagas.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadJobs()
  }, [loadJobs])

  const handleRunAgent = async () => {
    setSearching(true)
    setError(null)
    try {
      await request('/api/career/agent/search-now', { method: 'POST' })
      await loadJobs()
    } catch {
      setError('Erro ao acionar varredura do agente de vagas.')
    } finally {
      setSearching(false)
    }
  }

  const handleUpdateStatus = async (jobId: string, newStatus: JobItem['status']) => {
    try {
      await request(`/api/career/jobs/${jobId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status: newStatus }),
      })
      setJobs((prev) =>
        prev.map((j) => (j.id === jobId ? { ...j, status: newStatus } : j))
      )
      if (selectedJob?.id === jobId) {
        setSelectedJob((prev) => (prev ? { ...prev, status: newStatus } : null))
      }
    } catch {
      setError('Erro ao atualizar status da vaga.')
    }
  }

  const filteredJobs = jobs.filter((j) => {
    const matchesSearch =
      j.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      j.company.toLowerCase().includes(searchTerm.toLowerCase()) ||
      j.tags.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesModality =
      filterModality === 'ALL' || j.modality === filterModality
    const matchesStatus =
      statusFilter === 'ALL' || j.status === statusFilter
    return matchesSearch && matchesModality && matchesStatus
  })

  return (
    <div className="page-wrapper">
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Radar de Vagas com IA</h1>
          <p className="page-main-subtitle">
            Varredura inteligente e autônoma de vagas alinhadas ao seu perfil e tecnologias de estudo.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          {onNavigateToPreferences && (
            <button
              type="button"
              className="btn-secondary"
              onClick={onNavigateToPreferences}
            >
              Configurar Preferências
            </button>
          )}
          <button
            type="button"
            className="btn-primary"
            disabled={searching}
            onClick={() => void handleRunAgent()}
          >
            <SparklesIcon className="btn-icon" />
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
        <div className="search-input-wrapper" style={{ flex: 1 }}>
          <SearchIcon className="search-input-icon" />
          <input
            type="text"
            className="form-input search-input"
            placeholder="Buscar por cargo, empresa, tecnologia (ex: Java, Spring, React)..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          <div className="filter-select-wrapper">
            <FilterIcon className="filter-select-icon" />
            <select
              className="form-select filter-select"
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
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as any)}
            >
              <option value="ALL">Todos os status</option>
              <option value="DISCOVERED">Novas Descobertas</option>
              <option value="APPLIED">Candidatado</option>
              <option value="DISCARDED">Descartadas</option>
            </select>
            <ChevronDownIcon className="filter-select-arrow" />
          </div>
        </div>
      </div>

      {/* Conteúdo Principal: Lista de Vagas + Painel Lateral de Detalhes */}
      <div style={{ display: 'grid', gridTemplateColumns: selectedJob ? '1.2fr 1fr' : '1fr', gap: '20px' }}>
        <div>
          {loading ? (
            <div className="card empty-state-box">
              <p>Carregando radar de vagas...</p>
            </div>
          ) : filteredJobs.length === 0 ? (
            <div className="card empty-state-box">
              <BriefcaseIcon style={{ width: '40px', height: '40px', color: '#64748B', marginBottom: '12px' }} />
              <h3>Nenhuma vaga encontrada</h3>
              <p>Não há vagas com os filtros selecionados ou o agente ainda não executou a varredura.</p>
              <button
                type="button"
                className="btn-primary"
                style={{ marginTop: '16px' }}
                onClick={() => void handleRunAgent()}
              >
                Buscar Vagas Agora
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {filteredJobs.map((job) => {
                const isSelected = selectedJob?.id === job.id
                return (
                  <article
                    key={job.id}
                    className={`card job-card-item ${isSelected ? 'selected' : ''}`}
                    onClick={() => setSelectedJob(job)}
                    style={{
                      cursor: 'pointer',
                      borderLeft: isSelected ? '4px solid var(--primary-green)' : '1px solid var(--border-color)',
                      transition: 'all 0.2s ease',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <div>
                        <h3 style={{ margin: '0 0 4px', fontSize: '1.05rem', color: 'var(--text-primary)' }}>
                          {job.title}
                        </h3>
                        <p style={{ margin: '0 0 8px', fontSize: '0.88rem', color: 'var(--text-secondary)' }}>
                          <strong>{job.company}</strong> • {job.location} ({job.modality})
                        </p>
                      </div>
                      <span
                        className="badge"
                        style={{
                          background: job.matchScore >= 80 ? '#12372F' : '#1E293B',
                          color: job.matchScore >= 80 ? '#45D6A0' : '#94A3B8',
                          fontWeight: 700,
                        }}
                      >
                        {job.matchScore}% Match
                      </span>
                    </div>

                    {job.tags && (
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', margin: '8px 0' }}>
                        {job.tags.split(',').map((tag, idx) => (
                          <span
                            key={idx}
                            style={{
                              fontSize: '0.75rem',
                              background: '#101722',
                              border: '1px solid #283342',
                              padding: '2px 8px',
                              borderRadius: '4px',
                              color: '#9AA8BA',
                            }}
                          >
                            {tag.trim()}
                          </span>
                        ))}
                      </div>
                    )}

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '10px' }}>
                      <span style={{ fontSize: '0.8rem', color: '#64748B' }}>
                        Fonte: {job.sourceName} • {new Date(job.postedAt).toLocaleDateString('pt-BR')}
                      </span>
                      <div style={{ display: 'flex', gap: '8px' }} onClick={(e) => e.stopPropagation()}>
                        {job.status !== 'APPLIED' && (
                          <button
                            type="button"
                            className="btn-secondary"
                            style={{ padding: '4px 10px', fontSize: '0.78rem' }}
                            onClick={() => void handleUpdateStatus(job.id, 'APPLIED')}
                          >
                            Marcar Candidatado
                          </button>
                        )}
                        {job.status === 'APPLIED' && (
                          <span className="badge" style={{ background: '#12372F', color: '#45D6A0' }}>
                            ✓ Candidatado
                          </span>
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
          <aside className="card job-detail-card" style={{ position: 'sticky', top: '24px', maxHeight: 'calc(100vh - 120px)', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <h2 style={{ fontSize: '1.25rem', margin: '0 0 6px', color: 'var(--text-primary)' }}>
                  {selectedJob.title}
                </h2>
                <p style={{ margin: 0, color: 'var(--primary-green)', fontWeight: 600 }}>
                  {selectedJob.company}
                </p>
              </div>
              <button
                type="button"
                className="btn-secondary"
                style={{ padding: '4px 10px', fontSize: '0.8rem' }}
                onClick={() => setSelectedJob(null)}
              >
                ✕ Fechar
              </button>
            </div>

            <hr style={{ borderColor: 'var(--border-color)', margin: '16px 0' }} />

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '16px' }}>
              <div>
                <small style={{ color: '#64748B' }}>Localização / Modelo</small>
                <p style={{ margin: '2px 0 0', fontSize: '0.9rem', color: 'var(--text-primary)' }}>
                  {selectedJob.location} ({selectedJob.modality})
                </p>
              </div>
              <div>
                <small style={{ color: '#64748B' }}>Nível de Experiência</small>
                <p style={{ margin: '2px 0 0', fontSize: '0.9rem', color: 'var(--text-primary)' }}>
                  {selectedJob.experienceLevel || 'Não informado'}
                </p>
              </div>
            </div>

            <div style={{ marginBottom: '16px' }}>
              <small style={{ color: '#64748B' }}>Tecnologias e Tags</small>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginTop: '6px' }}>
                {selectedJob.tags.split(',').map((tag, idx) => (
                  <span
                    key={idx}
                    style={{
                      fontSize: '0.78rem',
                      background: '#101722',
                      border: '1px solid #283342',
                      padding: '3px 10px',
                      borderRadius: '6px',
                      color: 'var(--primary-green)',
                    }}
                  >
                    {tag.trim()}
                  </span>
                ))}
              </div>
            </div>

            <div style={{ marginBottom: '20px' }}>
              <h4 style={{ margin: '0 0 8px', fontSize: '0.95rem', color: 'var(--text-primary)' }}>
                Descrição da Vaga
              </h4>
              <div
                style={{
                  fontSize: '0.88rem',
                  color: 'var(--text-secondary)',
                  lineHeight: '1.6',
                  whiteSpace: 'pre-wrap',
                  maxHeight: '350px',
                  overflowY: 'auto',
                  padding: '12px',
                  background: '#101722',
                  borderRadius: '8px',
                  border: '1px solid var(--border-color)',
                }}
              >
                {selectedJob.description || 'Descrição completa disponível no link oficial da vaga.'}
              </div>
            </div>

            <div style={{ display: 'flex', gap: '10px' }}>
              {selectedJob.url && (
                <a
                  href={selectedJob.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn-primary"
                  style={{ textDecoration: 'none', textAlign: 'center', flex: 1, display: 'inline-block' }}
                >
                  Abrir Link Oficial da Vaga ↗
                </a>
              )}
              {selectedJob.status !== 'APPLIED' ? (
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => void handleUpdateStatus(selectedJob.id, 'APPLIED')}
                >
                  Marcar Candidatado
                </button>
              ) : (
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => void handleUpdateStatus(selectedJob.id, 'DISCOVERED')}
                >
                  Desmarcar
                </button>
              )}
            </div>
          </aside>
        )}
      </div>
    </div>
  )
}
