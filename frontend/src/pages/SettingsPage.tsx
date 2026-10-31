import { SettingsIcon } from '../components/icons'
import { AiProvidersPanel } from '../features/ai/AiProvidersPanel'
import { EvaluationMonitoringPanel } from '../features/questions/EvaluationMonitoringPanel'
import { EvaluationRigorPanel } from '../features/questions/EvaluationRigorPanel'
import '../styles/pages/settings-forum.css'

// Amostras lidas de styles/tokens.css (fonte única das cores).
const themeSwatches = [
  { name: 'Fundo Geral', token: '--color-bg' },
  { name: 'Sidebar', token: '--color-bg-subtle' },
  { name: 'Cards & Painéis', token: '--color-surface' },
  { name: 'Bordas', token: '--color-border' },
  { name: 'Verde de Destaque', token: '--color-accent' },
  { name: 'Item Selecionado', token: '--color-accent-soft' },
]

function readToken(token: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(token).trim().toUpperCase()
}

type Props = {
  onNavigateToGoals?: () => void
}

export function SettingsPage({ onNavigateToGoals }: Props) {
  return (
    <div className="page-wrapper max-w-4xl">
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Configurações do Sistema</h1>
          <p className="page-main-subtitle">
            Tema visual, provedores de inteligência artificial e informações da instalação.
          </p>
        </div>
      </div>

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
          {themeSwatches.map((swatch) => (
            <div key={swatch.token} className="swatch-item">
              <div className="swatch-box" style={{ background: `var(${swatch.token})` }} />
              <span className="swatch-name">{swatch.name}</span>
              <code>{readToken(swatch.token)}</code>
            </div>
          ))}
        </div>
      </div>

      {/* 2. Provedores de IA: rota por operação, modelo, limite diário e consumo */}
      <AiProvidersPanel />

      {/* Correção das questões abertas: nível de rigor e monitoramento */}
      <EvaluationRigorPanel />
      <EvaluationMonitoringPanel />

      {/* 3. Uso individual (sem login) */}
      <div className="card mb-6">
        <h2 className="text-lg font-bold mb-2 flex-gap-center">
          <SettingsIcon className="w-5 h-5 text-emerald-400" />
          <span>Uso Individual</span>
        </h2>
        <p className="text-sm text-secondary mb-2">
          Esta instalação é de um único usuário e não possui login. Todos os blocos, fontes, questões e métricas
          pertencem ao usuário local padrão.
        </p>
        <p className="text-xs text-muted">
          O backend já separa os dados por proprietário internamente, o que facilita adicionar autenticação no futuro.
        </p>
      </div>

      {/* 4. Metas de estudo */}
      <div className="card">
        <h2 className="text-lg font-bold mb-2">Metas de Estudo</h2>
        <p className="text-sm text-secondary mb-4">
          Metas mensuráveis e sessões cronometradas são definidas por bloco e ficam salvas no banco.
        </p>
        {onNavigateToGoals && (
          <button type="button" className="btn btn-secondary" onClick={onNavigateToGoals}>
            Abrir Metas e Sessões
          </button>
        )}
      </div>
    </div>
  )
}
