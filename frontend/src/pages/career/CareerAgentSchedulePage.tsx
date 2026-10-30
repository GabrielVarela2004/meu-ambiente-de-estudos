import { useEffect, useState } from 'react'
import { request } from '../../api/http'
import { runCareerAgent } from '../../api/career'
import '../../styles/pages/career.css'
import { CheckCircleIcon, ClockIcon, SparklesIcon } from '../../components/icons'

type AgentExecution = {
  id: string
  triggerType: 'SCHEDULED' | 'MANUAL' | 'RECOVERED'
  status: 'RUNNING' | 'COMPLETED' | 'TIMED_OUT' | 'FAILED'
  startedAt: string
  finishedAt: string | null
  jobsFound: number
  newJobsCount: number
  durationSeconds: number
  errorMessage: string | null
  executionDetails: string | null
}

type AgentStatusResponse = {
  running: boolean
  latestExecution: AgentExecution | null
  recentExecutions: AgentExecution[]
}

type PreferencesData = {
  searchDays: string
  searchTimeHour: number
  searchTimeMinute: number
  maxRoundMinutes: number
  agentEnabled: boolean
}

const triggerLabels: Record<string, string> = {
  SCHEDULED: 'Programada (Cron)',
  MANUAL: 'Manual (Usuário)',
  RECOVERED: 'Recuperada (Pós-Reinicialização)',
}

const statusLabels: Record<string, string> = {
  RUNNING: 'Em Execução',
  COMPLETED: 'Concluída',
  TIMED_OUT: 'Limite de Tempo (30 min)',
  FAILED: 'Falha na Coleta',
}

export function CareerAgentSchedulePage() {
  const [statusData, setStatusData] = useState<AgentStatusResponse | null>(null)
  const [prefs, setPrefs] = useState<PreferencesData | null>(null)
  const [loading, setLoading] = useState(true)
  const [runningManual, setRunningManual] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadData = async () => {
    setLoading(true)
    setError(null)
    try {
      const [agentRes, prefsRes] = await Promise.all([
        request<AgentStatusResponse>('/api/career/agent/status'),
        request<PreferencesData>('/api/career/preferences'),
      ])
      setStatusData(agentRes)
      setPrefs(prefsRes)
    } catch {
      setError('Não foi possível carregar o estado do agente de busca.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadData()
    const interval = setInterval(() => {
      void loadData()
    }, 15000)
    return () => clearInterval(interval)
  }, [])

  const handleRunManual = async () => {
    setRunningManual(true)
    try {
      await runCareerAgent()
      void loadData()
    } catch (err: any) {
      alert(err.message || 'Erro ao iniciar rodada do agente.')
    } finally {
      setRunningManual(false)
    }
  }

  if (loading && !statusData) {
    return <div className="page-wrapper"><div className="loading-state-card">Carregando agendador...</div></div>
  }

  const isRunning = statusData?.running ?? false
  const latest = statusData?.latestExecution

  return (
    <div className="page-wrapper">
      {/* Header */}
      <div className="page-header-flex">
        <div>
          <div className="flex-inline-gap">
            <h1 className="page-main-title">Agendamento do Agente de Vagas</h1>
            <span className={`badge ${isRunning ? 'badge-emerald' : 'badge-subtle'}`}>
              {isRunning ? '● Executando Coleta Agora' : 'Aguardando Horário'}
            </span>
          </div>
          <p className="page-main-subtitle">
            Monitore as coletas automáticas, limites de tempo de 30 minutos e o mecanismo de recuperação de rodadas perdidas após desligamento.
          </p>
        </div>

        <div className="page-actions-group">
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleRunManual}
            disabled={runningManual || isRunning}
          >
            <SparklesIcon className="w-4 h-4" />
            <span>{runningManual || isRunning ? 'Executando Coleta...' : 'Executar Rodada Imediata'}</span>
          </button>
        </div>
      </div>

      {error && <div className="alert-error-banner mb-6">{error}</div>}

      {/* Grid de Status e Próximo Agendamento */}
      <div className="stats-metric-grid mb-6">
        <div className="card metric-card">
          <div className="metric-header">
            <span className="metric-title">Status do Agendador</span>
            <ClockIcon className="w-5 h-5 text-emerald-400" />
          </div>
          <div className="metric-value-row">
            <span className="metric-big-number">
              {prefs?.agentEnabled ? 'Ativo' : 'Pausado'}
            </span>
          </div>
          <span className="text-xs text-muted">
            Horário: {String(prefs?.searchTimeHour ?? 9).padStart(2, '0')}:
            {String(prefs?.searchTimeMinute ?? 0).padStart(2, '0')}h ({prefs?.searchDays || 'Seg-Sex'})
          </span>
        </div>

        <div className="card metric-card">
          <div className="metric-header">
            <span className="metric-title">Duração Máxima da Rodada</span>
            <ClockIcon className="w-5 h-5 text-amber-400" />
          </div>
          <div className="metric-value-row">
            <span className="metric-big-number">{prefs?.maxRoundMinutes ?? 30} min</span>
          </div>
          <span className="text-xs text-muted">Encerramento seguro com persistência parcial</span>
        </div>

        <div className="card metric-card">
          <div className="metric-header">
            <span className="metric-title">Última Rodada</span>
            <CheckCircleIcon className="w-5 h-5 text-emerald-400" />
          </div>
          <div className="metric-value-row">
            <span className="metric-big-number">
              {latest ? `${latest.newJobsCount} novas` : 'Nenhuma'}
            </span>
          </div>
          <span className="text-xs text-muted">
            {latest ? `${latest.jobsFound} vagas encontradas em ${latest.durationSeconds}s` : 'Aguardando 1ª coleta'}
          </span>
        </div>
      </div>

      {/* Card Informativo de Recuperação de Desligamento */}
      <div className="card p-6 mb-6 career-info-card">
        <h3 className="text-emerald-400 font-bold mb-2">
          🔄 Mecanismo de Recuperação Pós-Desligamento (Offline Catch-Up)
        </h3>
        <p className="text-xs text-secondary leading-relaxed mb-3">
          Se o seu computador estiver desligado no horário programado da pesquisa de vagas, o backend detectará
          automaticamente a ausência da coleta assim que o sistema for iniciado.
        </p>
        <ul className="text-xs text-secondary list-disc pl-5 space-y-1">
          <li>Uma <strong>única rodada consolidada</strong> é iniciada com a tag <code>RECOVERED</code>.</li>
          <li>Evita acúmulo ou repetição desnecessária de coletas antigas.</li>
          <li>Todos os horários futuros permanecem inalterados.</li>
        </ul>
      </div>

      {/* Tabela de Histórico de Execuções */}
      <div className="card p-6">
        <h3 className="section-subtitle mb-4">Histórico Recente de Execuções do Agente</h3>

        {(!statusData?.recentExecutions || statusData.recentExecutions.length === 0) ? (
          <div className="empty-state-box">
            <ClockIcon className="empty-icon" />
            <h3>Nenhuma execução registrada</h3>
            <p>Clique no botão acima para acionar a primeira coleta do agente de vagas.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Data / Início</th>
                  <th>Gatilho</th>
                  <th>Status</th>
                  <th>Vagas Encontradas</th>
                  <th>Novas Vagas</th>
                  <th>Duração</th>
                  <th>Detalhes</th>
                </tr>
              </thead>
              <tbody>
                {statusData.recentExecutions.map((exec) => (
                  <tr key={exec.id}>
                    <td>
                      <span className="text-xs font-semibold">
                        {new Date(exec.startedAt).toLocaleString('pt-BR')}
                      </span>
                    </td>
                    <td>
                      <span className={`badge ${exec.triggerType === 'RECOVERED' ? 'badge-amber' : 'badge-subtle'}`}>
                        {triggerLabels[exec.triggerType] || exec.triggerType}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`badge ${
                          exec.status === 'COMPLETED'
                            ? 'badge-emerald'
                            : exec.status === 'TIMED_OUT'
                            ? 'badge-amber'
                            : exec.status === 'FAILED'
                            ? 'badge-danger'
                            : 'badge-subtle'
                        }`}
                      >
                        {statusLabels[exec.status] || exec.status}
                      </span>
                    </td>
                    <td className="font-semibold">{exec.jobsFound}</td>
                    <td className="font-semibold text-emerald-400">+{exec.newJobsCount}</td>
                    <td className="text-xs text-muted">{exec.durationSeconds}s</td>
                    <td>
                      <span className="text-xs text-secondary truncate max-w-xs block" title={exec.executionDetails || exec.errorMessage || ''}>
                        {exec.errorMessage ? `⚠️ ${exec.errorMessage}` : exec.executionDetails ? '✓ Coleta realizada com sucesso' : '-'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
