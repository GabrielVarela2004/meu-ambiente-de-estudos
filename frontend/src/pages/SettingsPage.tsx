import { useEffect, useState } from 'react'
import { request } from '../api/http'
import { CheckCircleIcon, SettingsIcon, SparklesIcon } from '../components/icons'

type ProviderStatus = {
  providerName: string
  configured: boolean
  model: string
}

export function SettingsPage() {
  const [aiStatus, setAiStatus] = useState<ProviderStatus | null>(null)
  const [activeUserId, setActiveUserId] = useState<string>(
    localStorage.getItem('estudos_user_id') || 'usuario-padrao'
  )
  const [tempUserId, setTempUserId] = useState(activeUserId)
  const [dailyGoalTarget, setDailyGoalTarget] = useState<number>(5)
  const [savedNotice, setSavedNotice] = useState(false)

  useEffect(() => {
    async function checkStatus() {
      try {
        const res = await request<ProviderStatus>('/api/chat/status')
        setAiStatus(res)
      } catch {
        // Ignora se indisponível
      }
    }
    void checkStatus()
  }, [])

  const handleSaveUserIsolation = (e: React.FormEvent) => {
    e.preventDefault()
    if (!tempUserId.trim()) return

    localStorage.setItem('estudos_user_id', tempUserId.trim())
    setActiveUserId(tempUserId.trim())
    setSavedNotice(true)
    setTimeout(() => {
      setSavedNotice(false)
      window.location.reload()
    }, 800)
  }

  return (
    <div className="page-wrapper max-w-4xl">
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Configurações do Sistema</h1>
          <p className="page-main-subtitle">
            Gerencie preferências de interface, integração com IA e isolamento multi-inquilino.
          </p>
        </div>
      </div>

      {savedNotice && (
        <div className="alert-banner success mb-6">
          <CheckCircleIcon className="w-5 h-5" />
          <span>Configuração salva com sucesso! Atualizando sessão...</span>
        </div>
      )}

      {/* 1. Tema Noturno */}
      <div className="card mb-6">
        <h2 className="text-lg font-bold mb-2 flex-gap-center">
          <span className="w-3 h-3 rounded-full bg-emerald-400 inline-block" />
          <span>Tema Visual: Modo Noturno de Alta Fidelidade (Dark Mode)</span>
        </h2>
        <p className="text-sm text-secondary mb-4">
          Paleta escura ergonômica especialmente calibrada para longas sessões de estudo com foco profundo e alto contraste.
        </p>

        <div className="color-swatches-grid">
          <div className="swatch-item">
            <div className="swatch-box" style={{ background: '#0B0F14' }} />
            <span className="swatch-name">Fundo Geral</span>
            <code>#0B0F14</code>
          </div>
          <div className="swatch-item">
            <div className="swatch-box" style={{ background: '#111720' }} />
            <span className="swatch-name">Sidebar</span>
            <code>#111720</code>
          </div>
          <div className="swatch-item">
            <div className="swatch-box" style={{ background: '#161E29' }} />
            <span className="swatch-name">Cards & Painéis</span>
            <code>#161E29</code>
          </div>
          <div className="swatch-item">
            <div className="swatch-box" style={{ background: '#283342' }} />
            <span className="swatch-name">Bordas</span>
            <code>#283342</code>
          </div>
          <div className="swatch-item">
            <div className="swatch-box" style={{ background: '#22C58B' }} />
            <span className="swatch-name">Verde Primário</span>
            <code>#22C58B</code>
          </div>
          <div className="swatch-item">
            <div className="swatch-box" style={{ background: '#12372F' }} />
            <span className="swatch-name">Item Selecionado</span>
            <code>#12372F</code>
          </div>
        </div>
      </div>

      {/* 2. Integração com Provedor de IA */}
      <div className="card mb-6">
        <div className="flex-between mb-2">
          <h2 className="text-lg font-bold flex-gap-center">
            <SparklesIcon className="w-5 h-5 text-emerald-400" />
            <span>Provedor de Inteligência Artificial (OpenAI)</span>
          </h2>
          <span className={`badge ${aiStatus?.configured ? 'badge-emerald' : 'badge-subtle'}`}>
            {aiStatus?.configured ? 'Configurado & Ativo' : 'Aguardando Chave API'}
          </span>
        </div>
        <p className="text-sm text-secondary mb-4">
          O modelo configurado no backend é <strong>{aiStatus?.model || 'gpt-4o-mini'}</strong>.
          Sem a chave da OpenAI, toda a plataforma funciona normalmente com algoritmos determinísticos e degradação graciosa.
        </p>

        <div className="info-box-terminal">
          <p className="text-xs text-muted mb-1">Para habilitar a geração em tempo real com a OpenAI:</p>
          <code className="block text-emerald-400">
            Adicione sua chave no arquivo .env: OPENAI_API_KEY=sk-...
          </code>
        </div>
      </div>

      {/* 3. Isolamento Multi-Inquilino (Multi-Tenant) */}
      <div className="card mb-6">
        <h2 className="text-lg font-bold mb-2 flex-gap-center">
          <SettingsIcon className="w-5 h-5 text-emerald-400" />
          <span>Isolamento Multi-Inquilino (Sessão de Usuário)</span>
        </h2>
        <p className="text-sm text-secondary mb-4">
          Todas as consultas, blocos, fontes, questões e métricas são blindadas e isoladas por usuário via cabeçalho <code>X-User-Id</code>. Você pode alternar o identificador de usuário para verificar o isolamento completo de dados na prática.
        </p>

        <form onSubmit={handleSaveUserIsolation} className="flex-gap-center">
          <div className="flex-1">
            <label htmlFor="user-id-input" className="text-xs text-muted block mb-1">
              Identificador do Usuário Ativo (X-User-Id):
            </label>
            <input
              id="user-id-input"
              type="text"
              className="form-input"
              value={tempUserId}
              onChange={(e) => setTempUserId(e.target.value)}
              placeholder="Ex: usuario-padrao, aluno-alfa-01..."
              required
            />
          </div>
          <button type="submit" className="btn btn-primary self-end">
            Salvar e Alternar Usuário
          </button>
        </form>
      </div>

      {/* 4. Meta de Estudos Diária */}
      <div className="card">
        <h2 className="text-lg font-bold mb-2">Meta Diária de Estudos</h2>
        <p className="text-sm text-secondary mb-4">
          Defina o número alvo de questões e exercícios para completar por dia.
        </p>
        <div className="flex-gap-center max-w-xs">
          <input
            type="number"
            min={1}
            max={50}
            className="form-input"
            value={dailyGoalTarget}
            onChange={(e) => setDailyGoalTarget(Number(e.target.value))}
          />
          <span className="text-sm text-muted">unidades / dia</span>
        </div>
      </div>
    </div>
  )
}
