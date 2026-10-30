import { useCallback, useEffect, useState } from 'react'
import { request } from '../../api/http'
import '../../styles/pages/learning-insights.css'

type Period = 'WEEK' | 'MONTH'

type Bucket = {
  label: string
  startDate: string
  endDate: string
  questionAttempts: number
  questionCorrect: number
  practicalAttempts: number
  practicalPassed: number
  accuracyPercent: number | null
  studyMinutes: number
  activeDays: number
}

type DifficultyStat = {
  difficulty: string
  attempts: number
  successes: number
  accuracyPercent: number | null
}

type TopicStat = {
  topic: string
  attempts: number
  accuracyPercent: number
}

type Consistency = {
  activeDaysLast7: number
  activeDaysLast30: number
  currentStreakDays: number
  lastActiveDay: string | null
}

type Insights = {
  period: Period
  buckets: Bucket[]
  byDifficulty: DifficultyStat[]
  weakestTopics: TopicStat[]
  strongestTopics: TopicStat[]
  consistency: Consistency
  trend: number | null
  totalAttempts: number
  practicalAttempts: number
  recommendations: string[]
}

type Analysis = {
  text: string
  source: string
  generatedAt: string
}

const difficultyLabels: Record<string, string> = {
  EASY: 'Fácil',
  BEGINNER: 'Iniciante',
  INTERMEDIATE: 'Intermediária',
  HARD: 'Difícil',
  ADVANCED: 'Avançada',
}

function formatPercent(value: number | null) {
  return value !== null ? `${value}%` : '—'
}

function formatTrend(trend: number | null) {
  if (trend === null) return '—'
  if (trend === 0) return 'estável'
  return `${trend > 0 ? '+' : ''}${trend} p.p.`
}

/** Análises de desempenho: os números vêm calculados do backend; a IA só escreve a interpretação. */
export function LearningInsightsPanel() {
  const [period, setPeriod] = useState<Period>('WEEK')
  const [insights, setInsights] = useState<Insights | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [analysis, setAnalysis] = useState<Analysis | null>(null)
  const [analyzing, setAnalyzing] = useState(false)
  const [analysisError, setAnalysisError] = useState<string | null>(null)

  const loadInsights = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setInsights(await request<Insights>(`/api/performance/insights?period=${period}`))
    } catch {
      setError('Não foi possível carregar as análises de desempenho.')
    } finally {
      setLoading(false)
    }
  }, [period])

  useEffect(() => {
    void loadInsights()
  }, [loadInsights])

  function changePeriod(next: Period) {
    setPeriod(next)
    setAnalysis(null)
    setAnalysisError(null)
  }

  async function generateAnalysis() {
    setAnalyzing(true)
    setAnalysisError(null)
    try {
      setAnalysis(await request<Analysis>(`/api/performance/insights/analysis?period=${period}`, { method: 'POST' }))
    } catch (err) {
      setAnalysisError(err instanceof Error ? err.message : 'Não foi possível gerar a análise.')
    } finally {
      setAnalyzing(false)
    }
  }

  const maxBucketAttempts = Math.max(
    1,
    ...(insights?.buckets.map((b) => b.questionAttempts + b.practicalAttempts) ?? []),
  )

  return (
    <section className="card insights-section">
      <div className="section-heading insights-heading">
        <div>
          <h3>Análises de Desempenho</h3>
          <p>Evolução {period === 'WEEK' ? 'semanal' : 'mensal'}, desempenho por dificuldade e recomendações sobre seus dados reais.</p>
        </div>
        <div className="segmented" role="group" aria-label="Período da análise">
          <button
            type="button"
            className={period === 'WEEK' ? 'nav-tab-btn active' : 'nav-tab-btn'}
            aria-pressed={period === 'WEEK'}
            onClick={() => changePeriod('WEEK')}
          >
            Semanal
          </button>
          <button
            type="button"
            className={period === 'MONTH' ? 'nav-tab-btn active' : 'nav-tab-btn'}
            aria-pressed={period === 'MONTH'}
            onClick={() => changePeriod('MONTH')}
          >
            Mensal
          </button>
        </div>
      </div>

      {error && <div className="feedback-banner error">{error}</div>}
      {loading && !insights && <p className="empty-notice">Carregando análises...</p>}

      {insights && (
        <>
          <div className="metrics-summary-grid">
            <div className="metric-card">
              <span className="metric-label">Tendência</span>
              <span className="metric-value">{formatTrend(insights.trend)}</span>
              <span className="metric-subtext">precisão vs. {period === 'WEEK' ? 'semana' : 'mês'} anterior</span>
            </div>
            <div className="metric-card">
              <span className="metric-label">Sequência atual</span>
              <span className="metric-value">{insights.consistency.currentStreakDays}</span>
              <span className="metric-subtext">dia(s) seguidos de estudo</span>
            </div>
            <div className="metric-card">
              <span className="metric-label">Dias ativos</span>
              <span className="metric-value">{insights.consistency.activeDaysLast7}/7</span>
              <span className="metric-subtext">{insights.consistency.activeDaysLast30} nos últimos 30 dias</span>
            </div>
            <div className="metric-card">
              <span className="metric-label">Tentativas totais</span>
              <span className="metric-value">{insights.totalAttempts}</span>
              <span className="metric-subtext">{insights.practicalAttempts} exercício(s) prático(s)</span>
            </div>
          </div>

          <div className="insights-buckets" role="list" aria-label="Evolução por período">
            {insights.buckets.map((bucket) => {
              const attempts = bucket.questionAttempts + bucket.practicalAttempts
              return (
                <div key={bucket.startDate} className="insights-bucket" role="listitem">
                  <div className="insights-bucket-track">
                    <div
                      className="insights-bucket-fill"
                      style={{ height: `${Math.round((attempts / maxBucketAttempts) * 100)}%` }}
                      title={`${attempts} tentativa(s), ${bucket.studyMinutes} min de estudo, ${bucket.activeDays} dia(s) ativo(s)`}
                    />
                  </div>
                  <span className="insights-bucket-accuracy">{formatPercent(bucket.accuracyPercent)}</span>
                  <span className="insights-bucket-label">{bucket.label}</span>
                  <span className="insights-bucket-meta">{attempts} tent. · {bucket.studyMinutes} min</span>
                </div>
              )
            })}
          </div>

          <div className="insights-grid">
            <div className="insights-box">
              <h4>Por dificuldade</h4>
              {insights.byDifficulty.length === 0 ? (
                <p className="empty-subnotice">Sem tentativas avaliadas.</p>
              ) : (
                <ul className="insights-list">
                  {insights.byDifficulty.map((d) => (
                    <li key={d.difficulty}>
                      <span>{difficultyLabels[d.difficulty] ?? d.difficulty}</span>
                      <span className="badge accuracy">{formatPercent(d.accuracyPercent)}</span>
                      <span className="insights-muted">{d.successes}/{d.attempts}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="insights-box">
              <h4>Tópicos mais fracos</h4>
              {insights.weakestTopics.length === 0 ? (
                <p className="empty-subnotice">Sem tópicos com tentativas suficientes.</p>
              ) : (
                <ul className="insights-list">
                  {insights.weakestTopics.map((t) => (
                    <li key={t.topic}>
                      <span>{t.topic}</span>
                      <span className="badge danger">{t.accuracyPercent}%</span>
                      <span className="insights-muted">{t.attempts} tent.</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="insights-box">
              <h4>Tópicos mais fortes</h4>
              {insights.strongestTopics.length === 0 ? (
                <p className="empty-subnotice">Nenhum tópico acima de 70% ainda.</p>
              ) : (
                <ul className="insights-list">
                  {insights.strongestTopics.map((t) => (
                    <li key={t.topic}>
                      <span>{t.topic}</span>
                      <span className="badge accuracy">{t.accuracyPercent}%</span>
                      <span className="insights-muted">{t.attempts} tent.</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {insights.recommendations.length > 0 && (
            <div className="insights-box">
              <h4>Recomendações</h4>
              <ul className="insights-recommendations">
                {insights.recommendations.map((rec) => (
                  <li key={rec}>{rec}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="insights-box insights-analysis">
            <div className="insights-analysis-header">
              <h4>Análise escrita</h4>
              <button
                type="button"
                className="btn-secondary btn-sm"
                onClick={() => void generateAnalysis()}
                disabled={analyzing}
                aria-busy={analyzing}
              >
                {analyzing ? 'Gerando...' : analysis ? 'Gerar novamente' : 'Gerar análise'}
              </button>
            </div>
            {analysisError && <div className="feedback-banner error">{analysisError}</div>}
            {analysis ? (
              <>
                <p className="insights-analysis-text">{analysis.text}</p>
                <span className="insights-muted">
                  Fonte: {analysis.source} ·{' '}
                  {new Date(analysis.generatedAt).toLocaleString('pt-BR', {
                    day: '2-digit',
                    month: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </span>
              </>
            ) : (
              <p className="empty-subnotice">
                A IA interpreta os números acima (sem recalculá-los). Sem IA configurada, a análise é feita por regras.
              </p>
            )}
          </div>
        </>
      )}
    </section>
  )
}
