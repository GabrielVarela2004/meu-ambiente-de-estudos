import { useEffect, useState } from 'react'
import { request } from '../../api/http'
import { toggleCareerSource } from '../../api/career'
import '../../styles/pages/career.css'

type JobSource = {
  id: string
  name: string
  integrationType: string
  baseUrl: string
  enabled: boolean
  lastCheckedAt: string | null
  lastStatus: 'SUCCESS' | 'ERROR' | 'UNTESTED'
  lastError: string | null
  rateLimitDelayMs: number
  requiresAuth: boolean
}

export function CareerSourcesPage() {
  const [sources, setSources] = useState<JobSource[]>([])
  const [loading, setLoading] = useState(true)
  const [testingId, setTestingId] = useState<string | null>(null)
  const [feedback, setFeedback] = useState<string | null>(null)

  useEffect(() => {
    void (async () => {
      try {
        const data = await request<JobSource[]>('/api/career/sources')
        setSources(data)
      } catch {
        // Silencioso se endpoint mockado
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  const handleToggle = async (sourceId: string, currentEnabled: boolean) => {
    try {
      await toggleCareerSource(sourceId, !currentEnabled)
      setSources((prev) =>
        prev.map((s) => (s.id === sourceId ? { ...s, enabled: !currentEnabled } : s))
      )
    } catch {
      setFeedback('Erro ao alternar status da fonte de vagas.')
    }
  }

  const handleTest = async (sourceId: string) => {
    setTestingId(sourceId)
    setFeedback(null)
    try {
      const res = await request<{ status: string; message?: string }>(`/api/career/sources/${sourceId}/test`, {
        method: 'POST',
      })
      setFeedback(`Teste da fonte concluído: ${res.status}`)
    } catch {
      setFeedback('Falha no teste de conexão com o feed de vagas.')
    } finally {
      setTestingId(null)
    }
  }

  return (
    <div className="page-wrapper page-narrow">
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Fontes do Radar de Vagas</h1>
          <p className="page-main-subtitle">
            Gerencie os provedores e APIs públicas consultadas pelo agente (Remotive, RemoteOK, GitHub Jobs, etc.).
          </p>
        </div>
      </div>

      {feedback && (
        <div className="feedback-banner success mb-4" role="status">
          <span>{feedback}</span>
        </div>
      )}

      {loading ? (
        <div className="card empty-state-box">
          <p>Carregando fontes de vagas...</p>
        </div>
      ) : sources.length === 0 ? (
        <div className="card empty-state-box">
          <h3>Nenhuma fonte cadastrada</h3>
          <p>O sistema vem com fontes padrões pré-configuradas. Execute a migração do banco para ativá-las.</p>
        </div>
      ) : (
        <div className="career-source-list">
          {sources.map((src) => (
            <article key={src.id} className="card career-source-card">
              <div>
                <div className="career-source-heading">
                  <h3 className="career-source-name">
                    {src.name}
                  </h3>
                  <span className={`badge ${src.enabled ? 'badge-success' : 'badge-danger'}`}>
                    {src.enabled ? 'Ativa' : 'Desativada'}
                  </span>
                </div>
                <p className="career-source-url">
                  {src.baseUrl} • Tipo: {src.integrationType}
                </p>
                {src.lastCheckedAt && (
                  <small className="text-xs text-muted">
                    Última verificação: {new Date(src.lastCheckedAt).toLocaleString('pt-BR')} (Status: {src.lastStatus})
                  </small>
                )}
              </div>

              <div className="career-source-actions">
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  disabled={testingId === src.id}
                  onClick={() => void handleTest(src.id)}
                >
                  {testingId === src.id ? 'Testando...' : 'Testar Conexão'}
                </button>
                <button
                  type="button"
                  className={`btn btn-sm ${src.enabled ? 'btn-ghost-danger' : 'btn-primary'}`}
                  onClick={() => void handleToggle(src.id, src.enabled)}
                >
                  {src.enabled ? 'Desativar' : 'Ativar'}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
