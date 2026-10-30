import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { request } from '../../api/http'
import '../../styles/pages/career.css'

type ProfileData = {
  headline: string
  summary: string
  education: string
  experience: string
  projects: string
  certifications: string
  languages: string
  links: string
}

type ProfileResponse = { [K in keyof ProfileData]: string | null } & { updatedAt: string | null }

const EMPTY: ProfileData = {
  headline: '',
  summary: '',
  education: '',
  experience: '',
  projects: '',
  certifications: '',
  languages: '',
  links: '',
}

// Campos de texto longo, na ordem em que aparecem; o limite espelha o @Size do backend
const LONG_FIELDS: { key: keyof ProfileData; label: string; placeholder: string; max: number; rows: number }[] = [
  { key: 'summary', label: 'Resumo profissional', placeholder: 'Quem você é e o que busca, em poucas linhas.', max: 5000, rows: 4 },
  { key: 'experience', label: 'Experiência', placeholder: 'Empregos, estágios e freelas: cargo, período e o que você fez.', max: 20000, rows: 6 },
  { key: 'projects', label: 'Projetos', placeholder: 'Projetos pessoais ou acadêmicos e as tecnologias usadas.', max: 20000, rows: 5 },
  { key: 'education', label: 'Formação', placeholder: 'Curso, instituição e período.', max: 10000, rows: 3 },
  { key: 'certifications', label: 'Certificações', placeholder: 'Ex.: Oracle Certified Associate Java SE.', max: 5000, rows: 2 },
  { key: 'languages', label: 'Idiomas', placeholder: 'Ex.: Inglês intermediário (leitura técnica fluente).', max: 2000, rows: 2 },
  { key: 'links', label: 'Links', placeholder: 'GitHub, LinkedIn, portfólio (um por linha).', max: 2000, rows: 2 },
]

export function CareerProfilePage() {
  const [profile, setProfile] = useState<ProfileData>(EMPTY)
  const [updatedAt, setUpdatedAt] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  const apply = (data: ProfileResponse) => {
    const next = { ...EMPTY }
    for (const key of Object.keys(EMPTY) as (keyof ProfileData)[]) {
      next[key] = data[key] ?? ''
    }
    setProfile(next)
    setUpdatedAt(data.updatedAt)
  }

  useEffect(() => {
    void (async () => {
      try {
        apply(await request<ProfileResponse>('/api/career/profile'))
      } catch {
        setFeedback({ type: 'error', message: 'Não foi possível carregar o perfil profissional.' })
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
      apply(await request<ProfileResponse>('/api/career/profile', {
        method: 'PUT',
        body: JSON.stringify(profile),
      }))
      setFeedback({ type: 'success', message: 'Perfil profissional salvo.' })
    } catch {
      setFeedback({ type: 'error', message: 'Erro ao salvar o perfil profissional.' })
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="page-wrapper">
        <div className="card empty-state-box">
          <p>Carregando perfil profissional...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="page-wrapper page-narrow">
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Perfil Profissional</h1>
          <p className="page-main-subtitle">
            Usado na análise das vagas junto com seus blocos de estudo.
            {updatedAt && ` Atualizado em ${new Date(updatedAt).toLocaleDateString('pt-BR')}.`}
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
        <div className="card">
          <div className="career-field-stack">
            <label className="form-label-block">
              <span className="form-label-text">Título profissional</span>
              <input
                type="text"
                className="form-input"
                maxLength={200}
                placeholder="Ex.: Desenvolvedor Java Júnior"
                value={profile.headline}
                onChange={(e) => setProfile({ ...profile, headline: e.target.value })}
              />
            </label>

            {LONG_FIELDS.map((field) => (
              <label key={field.key} className="form-label-block">
                <span className="form-label-text">{field.label}</span>
                <textarea
                  className="form-input"
                  rows={field.rows}
                  maxLength={field.max}
                  placeholder={field.placeholder}
                  value={profile[field.key]}
                  onChange={(e) => setProfile({ ...profile, [field.key]: e.target.value })}
                />
              </label>
            ))}
          </div>
        </div>

        <div className="form-actions career-form-actions">
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving ? 'Salvando perfil...' : 'Salvar Perfil'}
          </button>
        </div>
      </form>
    </div>
  )
}
