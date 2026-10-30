import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { request } from '../../api/http'
import '../../styles/pages/career.css'

type PreferencesData = {
  id: string
  targetRoles: string
  experienceLevels: string
  technologies: string
  mandatoryKeywords: string
  preferredKeywords: string
  location: string
  modality: 'REMOTE' | 'HYBRID' | 'ON_SITE' | 'ANY'
  minSalary: number | null
  searchDays: string
  searchTimeHour: number
  searchTimeMinute: number
  maxRoundMinutes: number
  agentEnabled: boolean
}

type Props = {
  onSaved?: () => void
}

export function CareerPreferencesPage({ onSaved }: Props) {
  const [preferences, setPreferences] = useState<PreferencesData>({
    id: '',
    targetRoles: 'Desenvolvedor Java, Backend Developer, Engenheiro de Software',
    experienceLevels: 'JUNIOR,PLENO',
    technologies: 'Java, Spring Boot, PostgreSQL, Docker, AWS',
    mandatoryKeywords: 'Java, Spring',
    preferredKeywords: 'JUnit, Microservices, Clean Architecture',
    location: 'Brasil, São Paulo, Remoto',
    modality: 'REMOTE',
    minSalary: null,
    searchDays: 'MON,TUE,WED,THU,FRI',
    searchTimeHour: 9,
    searchTimeMinute: 0,
    maxRoundMinutes: 15,
    agentEnabled: true,
  })

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  useEffect(() => {
    void (async () => {
      try {
        const data = await request<PreferencesData>('/api/career/preferences')
        if (data) {
          setPreferences(data)
        }
      } catch {
        // Se ainda não existirem preferências no backend, mantém defaults
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setFeedback(null)
    try {
      await request('/api/career/preferences', {
        method: 'PUT',
        body: JSON.stringify(preferences),
      })
      setFeedback({ type: 'success', message: 'Preferências de busca e radar salvas com sucesso!' })
      onSaved?.()
    } catch {
      setFeedback({ type: 'error', message: 'Erro ao salvar preferências de carreira.' })
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="page-wrapper">
        <div className="card empty-state-box">
          <p>Carregando preferências do agente...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="page-wrapper page-narrow">
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Preferências do Radar de Vagas</h1>
          <p className="page-main-subtitle">
            Configure as regras de busca, tecnologias alvo e horários para o agente autônomo monitorar vagas.
          </p>
        </div>
      </div>

      {feedback && (
        <div className={feedback.type === 'success' ? 'success-banner' : 'error-banner'}>
          <span>{feedback.message}</span>
          <button type="button" className="banner-dismiss" aria-label="Fechar aviso" onClick={() => setFeedback(null)}>✕</button>
        </div>
      )}

      <form onSubmit={handleSubmit} className="career-form-stack">
        {/* Bloco 1: Cargo e Tecnologias */}
        <div className="card">
          <h3 className="career-card-title">
            1. Cargos Alvo e Tecnologias
          </h3>

          <div className="career-field-stack">
            <label className="form-label-block">
              <span className="form-label-text">Cargos Desejados (separados por vírgula)</span>
              <input
                type="text"
                className="form-input"
                placeholder="Ex.: Desenvolvedor Java, Backend Engineer, Java Pleno"
                value={preferences.targetRoles}
                onChange={(e) => setPreferences({ ...preferences, targetRoles: e.target.value })}
                required
              />
            </label>

            <label className="form-label-block">
              <span className="form-label-text">Tecnologias e Ferramentas Alvo</span>
              <input
                type="text"
                className="form-input"
                placeholder="Ex.: Java 21, Spring Boot, PostgreSQL, Docker, AWS"
                value={preferences.technologies}
                onChange={(e) => setPreferences({ ...preferences, technologies: e.target.value })}
                required
              />
            </label>

            <div className="form-grid-2col">
              <label className="form-label-block">
                <span className="form-label-text">Palavras-chave Obrigatórias</span>
                <input
                  type="text"
                  className="form-input"
                  placeholder="Ex.: Java, Spring"
                  value={preferences.mandatoryKeywords}
                  onChange={(e) => setPreferences({ ...preferences, mandatoryKeywords: e.target.value })}
                />
              </label>

              <label className="form-label-block">
                <span className="form-label-text">Palavras-chave Desejáveis (Diferenciais)</span>
                <input
                  type="text"
                  className="form-input"
                  placeholder="Ex.: Kubernetes, Kafka, CI/CD"
                  value={preferences.preferredKeywords}
                  onChange={(e) => setPreferences({ ...preferences, preferredKeywords: e.target.value })}
                />
              </label>
            </div>
          </div>
        </div>

        {/* Bloco 2: Localidade, Modalidade e Salário */}
        <div className="card">
          <h3 className="career-card-title">
            2. Modalidade, Localidade e Nível
          </h3>

          <div className="form-grid-2col">
            <label className="form-label-block">
              <span className="form-label-text">Modalidade de Trabalho</span>
              <select
                className="form-select"
                value={preferences.modality}
                onChange={(e) => setPreferences({ ...preferences, modality: e.target.value as any })}
              >
                <option value="REMOTE">Exclusivamente Remoto</option>
                <option value="HYBRID">Híbrido ou Remoto</option>
                <option value="ON_SITE">Presencial</option>
                <option value="ANY">Qualquer Modalidade</option>
              </select>
            </label>

            <label className="form-label-block">
              <span className="form-label-text">Níveis de Experiência</span>
              <input
                type="text"
                className="form-input"
                placeholder="Ex.: JUNIOR, PLENO"
                value={preferences.experienceLevels}
                onChange={(e) => setPreferences({ ...preferences, experienceLevels: e.target.value })}
              />
            </label>

            <label className="form-label-block">
              <span className="form-label-text">Localidades Aceitas</span>
              <input
                type="text"
                className="form-input"
                placeholder="Ex.: Brasil, São Paulo, Remoto"
                value={preferences.location}
                onChange={(e) => setPreferences({ ...preferences, location: e.target.value })}
              />
            </label>

            <label className="form-label-block">
              <span className="form-label-text">Salário Mínimo Desejado (R$ opcional)</span>
              <input
                type="number"
                className="form-input"
                placeholder="Ex.: 6000"
                value={preferences.minSalary ?? ''}
                onChange={(e) =>
                  setPreferences({
                    ...preferences,
                    minSalary: e.target.value ? Number(e.target.value) : null,
                  })
                }
              />
            </label>
          </div>
        </div>

        {/* Bloco 3: Configurações do Agente Autônomo */}
        <div className="card">
          <h3 className="career-card-title">
            3. Automação do Agente de Varredura
          </h3>

          <div className="career-field-stack">
            <label className="checkbox-row">
              <input
                type="checkbox"
                checked={preferences.agentEnabled}
                onChange={(e) => setPreferences({ ...preferences, agentEnabled: e.target.checked })}
              />
              <span className="checkbox-text font-semibold">
                Ativar agente autônomo para varreduras agendadas em segundo plano
              </span>
            </label>

            <div className="career-grid-3col">
              <label className="form-label-block">
                <span className="form-label-text">Dias da Semana</span>
                <input
                  type="text"
                  className="form-input"
                  placeholder="MON,TUE,WED,THU,FRI"
                  value={preferences.searchDays}
                  onChange={(e) => setPreferences({ ...preferences, searchDays: e.target.value })}
                />
              </label>

              <label className="form-label-block">
                <span className="form-label-text">Horário (Hora 0-23)</span>
                <input
                  type="number"
                  min="0"
                  max="23"
                  className="form-input"
                  value={preferences.searchTimeHour}
                  onChange={(e) => setPreferences({ ...preferences, searchTimeHour: Number(e.target.value) })}
                />
              </label>

              <label className="form-label-block">
                <span className="form-label-text">Horário (Minutos 0-59)</span>
                <input
                  type="number"
                  min="0"
                  max="59"
                  className="form-input"
                  value={preferences.searchTimeMinute}
                  onChange={(e) => setPreferences({ ...preferences, searchTimeMinute: Number(e.target.value) })}
                />
              </label>
            </div>
          </div>
        </div>

        <div className="form-actions career-form-actions">
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving ? 'Salvando preferências...' : 'Salvar Preferências'}
          </button>
        </div>
      </form>
    </div>
  )
}
