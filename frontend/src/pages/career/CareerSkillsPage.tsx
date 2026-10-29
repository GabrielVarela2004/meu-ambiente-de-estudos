import { useEffect, useState } from 'react'
import { request } from '../../api/http'
import { CodeIcon, FileTextIcon, LayersIcon, SparklesIcon } from '../../components/icons'

type CompetencyItem = {
  skillName: string
  category: string
  level: 'DEMONSTRATED' | 'IN_DEVELOPMENT' | 'STUDIED' | 'UNASSESSED'
  exerciseCount: number
  practicalCount: number
  accuracyPercent: number
  sourceBlockTitle: string
}

type SkillsProfileData = {
  demonstrated: CompetencyItem[]
  inDevelopment: CompetencyItem[]
  studied: CompetencyItem[]
  totalSkillsCount: number
  demonstratedSkillsCount: number
}

const levelBadgeStyles: Record<string, { label: string; className: string }> = {
  DEMONSTRATED: { label: 'Demonstrada', className: 'badge-emerald' },
  IN_DEVELOPMENT: { label: 'Em Desenvolvimento', className: 'badge-amber' },
  STUDIED: { label: 'Estudada Teórica', className: 'badge-subtle' },
  UNASSESSED: { label: 'Não Avaliada', className: 'badge-subtle' },
}

export function CareerSkillsPage() {
  const [profile, setProfile] = useState<SkillsProfileData | null>(null)
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadProfile = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await request<SkillsProfileData>('/api/career/skills')
      setProfile(data)
    } catch {
      setError('Não foi possível carregar a matriz de competências técnicas.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadProfile()
  }, [])

  const handleGenerateAssessment = async (skillName: string) => {
    setGenerating(true)
    try {
      await request('/api/career/skills/assess', {
        method: 'POST',
        body: JSON.stringify({ skillName }),
      })
      void loadProfile()
    } catch {
      alert('Erro ao gerar desafio prático de avaliação.')
    } finally {
      setGenerating(false)
    }
  }

  if (loading && !profile) {
    return (
      <div className="page-wrapper">
        <div className="loading-state-card">Carregando matriz de competências...</div>
      </div>
    )
  }

  const allSkills = [
    ...(profile?.demonstrated ?? []),
    ...(profile?.inDevelopment ?? []),
    ...(profile?.studied ?? []),
  ]

  return (
    <div className="page-wrapper">
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Matriz de Competências Técnicas</h1>
          <p className="page-main-subtitle">
            Habilidades mapeadas diretamente a partir das suas sessões de estudo, exercícios práticos e questões resolvidas na plataforma.
          </p>
        </div>
      </div>

      {error && <div className="alert-error-banner mb-6">{error}</div>}

      {/* Grid de KPIs */}
      <div className="stats-metric-grid mb-6">
        <div className="card metric-card">
          <div className="metric-header">
            <span className="metric-title">Competências Mapeadas</span>
            <LayersIcon className="w-5 h-5 text-emerald-400" />
          </div>
          <div className="metric-value-row">
            <span className="metric-big-number">{profile?.totalSkillsCount ?? allSkills.length}</span>
          </div>
          <span className="text-xs text-muted">Extraídas dos seus materiais e blocos</span>
        </div>

        <div className="card metric-card">
          <div className="metric-header">
            <span className="metric-title">Habilidades Demonstradas</span>
            <CodeIcon className="w-5 h-5 text-emerald-400" />
          </div>
          <div className="metric-value-row">
            <span className="metric-big-number text-emerald-400">
              {profile?.demonstratedSkillsCount ?? (profile?.demonstrated?.length ?? 0)}
            </span>
          </div>
          <span className="text-xs text-muted">Comprovadas com exercícios práticos e acertos</span>
        </div>

        <div className="card metric-card">
          <div className="metric-header">
            <span className="metric-title">Em Desenvolvimento</span>
            <FileTextIcon className="w-5 h-5 text-amber-400" />
          </div>
          <div className="metric-value-row">
            <span className="metric-big-number text-amber-400">
              {profile?.inDevelopment?.length ?? 0}
            </span>
          </div>
          <span className="text-xs text-muted">Requerem mais exercícios e prática</span>
        </div>
      </div>

      {/* Lista de Competências */}
      <div className="card p-6">
        <h3 className="section-subtitle mb-4">Competências e Evidências Práticas</h3>

        {allSkills.length === 0 ? (
          <div className="empty-state-box">
            <LayersIcon className="empty-icon" />
            <h3>Nenhuma competência registrada ainda</h3>
            <p>Cadastre materiais de estudo e resolva exercícios nos seus blocos para alimentar o perfil.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Competência</th>
                  <th>Categoria</th>
                  <th>Nível Estimado</th>
                  <th>Exercícios Práticos</th>
                  <th>Assertividade</th>
                  <th>Origem (Bloco)</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {allSkills.map((skill, idx) => {
                  const badge = levelBadgeStyles[skill.level] || levelBadgeStyles.UNASSESSED
                  return (
                    <tr key={idx}>
                      <td className="font-semibold text-primary">{skill.skillName}</td>
                      <td className="text-xs text-secondary">{skill.category}</td>
                      <td>
                        <span className={`badge ${badge.className}`}>{badge.label}</span>
                      </td>
                      <td>{skill.practicalCount} práticos / {skill.exerciseCount} totais</td>
                      <td className="font-semibold text-emerald-400">{skill.accuracyPercent}%</td>
                      <td className="text-xs text-muted">{skill.sourceBlockTitle}</td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-secondary text-xs py-1 px-3"
                          disabled={generating}
                          onClick={() => void handleGenerateAssessment(skill.skillName)}
                        >
                          <SparklesIcon className="w-3 h-3" />
                          <span>Desafio de Validação</span>
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
