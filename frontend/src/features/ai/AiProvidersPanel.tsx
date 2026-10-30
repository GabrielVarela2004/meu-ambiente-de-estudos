import { useCallback, useState } from 'react'
import { request } from '../../api/http'
import { SparklesIcon } from '../../components/icons'
import '../../styles/pages/ai-providers.css'
import { useDeferredEffect } from '../../hooks/useDeferredEffect'

type AiOperation = 'CHAT' | 'QUESTION_GENERATION' | 'EXERCISE_GENERATION' | 'TOPIC_EXTRACTION' | 'MATERIAL_SUMMARY'

type Provider = {
  id: string
  displayName: string
  keyConfigured: boolean
  enabled: boolean
  defaultModel: string
  modelOverride: string | null
  effectiveModel: string
  suggestedModels: string[]
  dailyRequestLimit: number | null
  requestsToday: number
  unavailableReason: string | null
}

type Route = {
  operation: AiOperation
  label: string
  providerId: string
  fallbackEnabled: boolean
}

type UsageSummary = {
  providerId: string
  operation: AiOperation
  requests: number
  successes: number
  inputTokens: number
  outputTokens: number
  averageLatencyMs: number
}

type ProviderDraft = { enabled: boolean; model: string; dailyRequestLimit: string }

const envVarByProvider: Record<string, string> = {
  openai: 'OPENAI_API_KEY',
  anthropic: 'ANTHROPIC_API_KEY',
  gemini: 'GEMINI_API_KEY',
}

const operationLabel: Record<AiOperation, string> = {
  CHAT: 'Tutor IA',
  QUESTION_GENERATION: 'Questões',
  EXERCISE_GENERATION: 'Exercícios',
  TOPIC_EXTRACTION: 'Tópicos',
  MATERIAL_SUMMARY: 'Resumos',
}

const numberFormat = new Intl.NumberFormat('pt-BR')

export function AiProvidersPanel() {
  const [providers, setProviders] = useState<Provider[]>([])
  const [routes, setRoutes] = useState<Route[]>([])
  const [usage, setUsage] = useState<UsageSummary[]>([])
  const [drafts, setDrafts] = useState<Record<string, ProviderDraft>>({})
  const [loading, setLoading] = useState(true)
  const [savingId, setSavingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    setError(null)
    try {
      const [settings, usageData] = await Promise.all([
        request<{ providers: Provider[]; routes: Route[] }>('/api/ai/settings'),
        request<UsageSummary[]>('/api/ai/usage?days=7'),
      ])
      setProviders(settings.providers)
      setRoutes(settings.routes)
      setUsage(usageData)
      setDrafts(
        Object.fromEntries(
          settings.providers.map((p) => [
            p.id,
            { enabled: p.enabled, model: p.modelOverride ?? '', dailyRequestLimit: p.dailyRequestLimit?.toString() ?? '' },
          ]),
        ),
      )
    } catch {
      setError('Não foi possível carregar as configurações de IA.')
    } finally {
      setLoading(false)
    }
  }, [])

  useDeferredEffect(() => load(), [load])

  const saveProvider = async (provider: Provider) => {
    const draft = drafts[provider.id]
    const limit = draft.dailyRequestLimit.trim() === '' ? null : Number(draft.dailyRequestLimit)
    if (limit !== null && (!Number.isInteger(limit) || limit < 1)) {
      setError('O limite diário deve ser um número inteiro maior que zero (ou vazio para sem limite).')
      return
    }
    setSavingId(provider.id)
    setError(null)
    setNotice(null)
    try {
      await request(`/api/ai/providers/${provider.id}`, {
        method: 'PUT',
        body: JSON.stringify({ enabled: draft.enabled, model: draft.model.trim() || null, dailyRequestLimit: limit }),
      })
      setNotice(`Preferências de ${provider.displayName} salvas.`)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao salvar o provedor.')
    } finally {
      setSavingId(null)
    }
  }

  const saveRoute = async (route: Route, changes: Partial<Pick<Route, 'providerId' | 'fallbackEnabled'>>) => {
    const next = { ...route, ...changes }
    setRoutes((prev) => prev.map((r) => (r.operation === route.operation ? next : r)))
    setError(null)
    setNotice(null)
    try {
      await request(`/api/ai/routes/${route.operation}`, {
        method: 'PUT',
        body: JSON.stringify({ providerId: next.providerId, fallbackEnabled: next.fallbackEnabled }),
      })
      setNotice(`Rota de "${route.label}" atualizada.`)
    } catch (err) {
      setRoutes((prev) => prev.map((r) => (r.operation === route.operation ? route : r)))
      setError(err instanceof Error ? err.message : 'Erro ao salvar a rota.')
    }
  }

  const providerName = (id: string) => providers.find((p) => p.id === id)?.displayName ?? id
  const anyConfigured = providers.some((p) => p.keyConfigured)

  return (
    <div className="card mb-6" aria-labelledby="ai-providers-heading">
      <div className="section-heading">
        <div>
          <h2 id="ai-providers-heading" className="text-lg font-bold flex-gap-center">
            <SparklesIcon className="w-5 h-5 text-emerald-400" />
            <span>Provedores de Inteligência Artificial</span>
          </h2>
          <p className="subtitle">
            Escolha o provedor de cada operação, o modelo e o limite diário de chamadas. Sem nenhuma chave configurada,
            a plataforma usa os algoritmos determinísticos baseados nas suas fontes.
          </p>
        </div>
      </div>

      {error && <div className="alert-error-banner mb-4">{error}</div>}
      {notice && (
        <div className="feedback-banner success mb-4" role="status">
          {notice}
        </div>
      )}

      {loading ? (
        <p className="loading-state">Carregando provedores…</p>
      ) : (
        <>
          {/* ---- Provedores ---- */}
          <div className="ai-provider-grid">
            {providers.map((provider) => {
              const draft = drafts[provider.id]
              if (!draft) return null
              const status = provider.unavailableReason
                ? { label: provider.keyConfigured ? 'Indisponível' : 'Sem chave', className: provider.keyConfigured ? 'badge-warning' : 'badge-subtle' }
                : { label: 'Ativo', className: 'badge-success' }
              const listId = `models-${provider.id}`
              return (
                <section key={provider.id} className="ai-provider-card" aria-label={provider.displayName}>
                  <div className="ai-provider-header">
                    <strong>{provider.displayName}</strong>
                    <span className={`badge ${status.className}`}>{status.label}</span>
                  </div>

                  {!provider.keyConfigured ? (
                    <p className="form-hint">
                      Defina <code>{envVarByProvider[provider.id] ?? 'a chave'}</code> no arquivo <code>.env</code> e reinicie o backend.
                    </p>
                  ) : (
                    provider.unavailableReason && <p className="form-hint">Motivo: {provider.unavailableReason}.</p>
                  )}

                  <label className="checkbox-row">
                    <input
                      type="checkbox"
                      checked={draft.enabled}
                      onChange={(e) => setDrafts((prev) => ({ ...prev, [provider.id]: { ...draft, enabled: e.target.checked } }))}
                    />
                    <span className="checkbox-text">Habilitado</span>
                  </label>

                  <div className="form-field">
                    <label htmlFor={`model-${provider.id}`}>Modelo</label>
                    <input
                      id={`model-${provider.id}`}
                      className="form-input"
                      list={listId}
                      placeholder={`Padrão: ${provider.defaultModel}`}
                      maxLength={120}
                      value={draft.model}
                      onChange={(e) => setDrafts((prev) => ({ ...prev, [provider.id]: { ...draft, model: e.target.value } }))}
                    />
                    <datalist id={listId}>
                      {provider.suggestedModels.map((m) => (
                        <option key={m} value={m} />
                      ))}
                    </datalist>
                  </div>

                  <div className="form-field">
                    <label htmlFor={`limit-${provider.id}`}>Limite de chamadas por dia</label>
                    <input
                      id={`limit-${provider.id}`}
                      type="number"
                      min={1}
                      className="form-input"
                      placeholder="Sem limite"
                      value={draft.dailyRequestLimit}
                      onChange={(e) =>
                        setDrafts((prev) => ({ ...prev, [provider.id]: { ...draft, dailyRequestLimit: e.target.value } }))
                      }
                    />
                    <span className="form-hint">
                      Hoje: {numberFormat.format(provider.requestsToday)}
                      {provider.dailyRequestLimit ? ` de ${numberFormat.format(provider.dailyRequestLimit)}` : ''} chamadas
                    </span>
                  </div>

                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    disabled={savingId === provider.id}
                    onClick={() => void saveProvider(provider)}
                  >
                    {savingId === provider.id ? 'Salvando…' : 'Salvar'}
                  </button>
                </section>
              )
            })}
          </div>

          {/* ---- Rotas por operação ---- */}
          <h3 className="ai-subheading">Provedor por operação</h3>
          {!anyConfigured && (
            <p className="form-hint mb-3">
              Nenhuma chave configurada ainda: as rotas abaixo passam a valer assim que um provedor tiver chave.
            </p>
          )}
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Operação</th>
                  <th>Provedor</th>
                  <th>Fallback para os demais</th>
                </tr>
              </thead>
              <tbody>
                {routes.map((route) => (
                  <tr key={route.operation}>
                    <td>{route.label}</td>
                    <td>
                      <select
                        className="form-select form-select-sm"
                        aria-label={`Provedor para ${route.label}`}
                        value={route.providerId}
                        onChange={(e) => void saveRoute(route, { providerId: e.target.value })}
                      >
                        {providers.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.displayName}
                            {p.unavailableReason ? ' (indisponível)' : ''}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <label className="checkbox-row ai-inline-check">
                        <input
                          type="checkbox"
                          checked={route.fallbackEnabled}
                          onChange={(e) => void saveRoute(route, { fallbackEnabled: e.target.checked })}
                        />
                        <span className="checkbox-text">{route.fallbackEnabled ? 'Ativo' : 'Desligado'}</span>
                      </label>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* ---- Consumo ---- */}
          <h3 className="ai-subheading">Consumo nos últimos 7 dias</h3>
          {usage.length === 0 ? (
            <p className="empty-subnotice">Nenhuma chamada a provedores de IA registrada no período.</p>
          ) : (
            <div className="table-responsive">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Provedor</th>
                    <th>Operação</th>
                    <th>Chamadas</th>
                    <th>Sucesso</th>
                    <th>Tokens (entrada / saída)</th>
                    <th>Latência média</th>
                  </tr>
                </thead>
                <tbody>
                  {usage.map((row) => (
                    <tr key={`${row.providerId}-${row.operation}`}>
                      <td>{providerName(row.providerId)}</td>
                      <td>{operationLabel[row.operation] ?? row.operation}</td>
                      <td>{numberFormat.format(row.requests)}</td>
                      <td>{row.requests ? Math.round((row.successes / row.requests) * 100) : 0}%</td>
                      <td>
                        {numberFormat.format(row.inputTokens)} / {numberFormat.format(row.outputTokens)}
                      </td>
                      <td>{(row.averageLatencyMs / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} s</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  )
}
