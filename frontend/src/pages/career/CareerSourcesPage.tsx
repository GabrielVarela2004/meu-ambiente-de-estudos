import { useEffect, useState } from 'react'
import { request } from '../../api/http'

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
      await request(`/api/career/sources/${sourceId}/toggle`, {
        method: 'PATCH',
        body: JSON.stringify({ enabled: !currentEnabled }),
      })
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
    <div className="page-wrapper" style={{ maxWidth: '900px' }}>
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Fontes do Radar de Vagas</h1>
          <p className="page-main-subtitle">
            Gerencie os provedores e APIs públicas consultadas pelo agente (Remotive, RemoteOK, GitHub Jobs, etc.).
          </p>
        </div>
      </div>

      {feedback && (
        <div className="info-banner" style={{ background: '#12372F', border: '1px solid #283342', color: '#45D6A0', padding: '10px 14px', borderRadius: '8px', marginBottom: '16px' }}>
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
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {sources.map((src) => (
            <article key={src.id} className="card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <h3 style={{ margin: 0, fontSize: '1.05rem', color: 'var(--text-primary)' }}>
                    {src.name}
                  </h3>
                  <span
                    className="badge"
                    style={{
                      background: src.enabled ? '#12372F' : '#2D1518',
                      color: src.enabled ? '#45D6A0' : '#FF7575',
                    }}
                  >
                    {src.enabled ? 'Ativa' : 'Desativada'}
                  </span>
                </div>
                <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                  {src.baseUrl} • Tipo: {src.integrationType}
                </p>
                {src.lastCheckedAt && (
                  <small style={{ color: '#64748B' }}>
                    Última verificação: {new Date(src.lastCheckedAt).toLocaleString('pt-BR')} (Status: {src.lastStatus})
                  </small>
                )}
              </div>

              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  type="button"
                  className="btn-secondary"
                  disabled={testingId === src.id}
                  onClick={() => void handleTest(src.id)}
                >
                  {testingId === src.id ? 'Testando...' : 'Testar Conexão'}
                </button>
                <button
                  type="button"
                  className={src.enabled ? 'btn-danger' : 'btn-primary'}
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
