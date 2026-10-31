import { useCallback, useEffect, useState } from 'react'
import { request } from '../../api/http'
import { LearningInsightsPanel } from './LearningInsightsPanel'
import '../../styles/pages/learning-panels.css'
import { useDeferredEffect } from '../../hooks/useDeferredEffect'

type OverallPerformanceSummary = {
  totalAttempts: number
  uniqueQuestionsAnswered: number
  evaluatedAttempts: number
  correctAttempts: number
  incorrectAttempts: number
  accuracyPercent: number | null
  errorPercent: number | null
}

type BlockPerformanceSummary = {
  studyBlockId: string
  blockTitle: string
  totalAttempts: number
  uniqueQuestions: number
  evaluatedAttempts: number
  correctAttempts: number
  incorrectAttempts: number
  accuracyPercent: number | null
  errorPercent: number | null
  /** Respostas parciais (avaliadas, mas não contam como acerto). */
  partialAttempts: number
  /** Nota média (0–100) das tentativas corrigidas. */
  averageScorePercent: number | null
}

type TimelinePoint = {
  date: string
  totalAttempts: number
  correctAttempts: number
  incorrectAttempts: number
  accuracyPercent: number | null
}

type HistoricalAttemptDetail = {
  attemptId: string
  answeredAt: string
  studyBlockId: string
  studyBlockTitle: string
  questionId: string | null
  difficulty: string
  questionFormat: string
  prompt: string
  userAnswer: string | null
  expectedAnswer: string | null
  explanation: string | null
  correct: boolean
  evaluationStatus: string
  scorePercent: number
  sourceTitle: string | null
  sourceExcerpt: string | null
}

type GlobalPerformanceData = {
  overall: OverallPerformanceSummary
  blocks: BlockPerformanceSummary[]
  timeline: TimelinePoint[]
  recentAttempts: HistoricalAttemptDetail[]
}

type StudyBlockOption = {
  id: string
  title: string
}

interface GlobalPerformancePanelProps {
  onSelectBlock?: (blockId: string) => void
}

const difficultyLabels: Record<string, string> = {
  EASY: 'Fácil',
  INTERMEDIATE: 'Intermediária',
  HARD: 'Difícil',
}

const formatLabels: Record<string, string> = {
  MULTIPLE_CHOICE: 'Múltipla escolha',
  TRUE_FALSE: 'Verdadeiro ou falso',
  DISCURSIVE: 'Discursiva',
}

export function GlobalPerformancePanel({ onSelectBlock }: GlobalPerformancePanelProps) {
  const [data, setData] = useState<GlobalPerformanceData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Filtros dinâmicos
  const [days, setDays] = useState<number | ''>(30)
  const [selectedBlockId, setSelectedBlockId] = useState<string>('')
  const [selectedDifficulty, setSelectedDifficulty] = useState<string>('')

  // Opções para os selects
  const [blocks, setBlocks] = useState<StudyBlockOption[]>([])

  // Carrega blocos para o filtro
  useEffect(() => {
    async function loadBlocks() {
      try {
        const result = await request<StudyBlockOption[]>('/api/study-blocks')
        setBlocks(result)
      } catch {
        // Silencioso
      }
    }
    void loadBlocks()
  }, [])

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const params = new URLSearchParams()
      if (days !== '') {
        params.append('days', String(days))
      }
      if (selectedBlockId) {
        params.append('studyBlockId', selectedBlockId)
      }
      if (selectedDifficulty) {
        params.append('difficulty', selectedDifficulty)
      }

      const queryString = params.toString() ? `?${params.toString()}` : ''
      const result = await request<GlobalPerformanceData>(`/api/performance/global${queryString}`)
      setData(result)
    } catch {
      setError('Não foi possível carregar os dados de desempenho global.')
    } finally {
      setLoading(false)
    }
  }, [days, selectedBlockId, selectedDifficulty])

  useDeferredEffect(() => loadData(), [loadData])

  const overall = data?.overall

  return (
    <div className="global-performance-panel">
      <div className="panel-header">
        <div>
          <h2>Desempenho Global e Histórico Permanente</h2>
          <p className="subtitle">
            Métricas unificadas em todos os seus blocos de estudo com auditoria cronológica e imutabilidade de dados.
          </p>
        </div>
      </div>

      <LearningInsightsPanel />

      {/* Barra de Filtros Dinâmicos */}
      <section className="card filters-card">
        <div className="filters-grid">
          <div className="filter-group">
            <label htmlFor="filter-period">Período</label>
            <select
              id="filter-period"
              value={days}
              onChange={(e) => setDays(e.target.value === '' ? '' : Number(e.target.value))}
            >
              <option value="7">Últimos 7 dias</option>
              <option value="30">Últimos 30 dias</option>
              <option value="90">Últimos 90 dias</option>
              <option value="">Todo o histórico</option>
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="filter-block">Bloco de Estudo</label>
            <select
              id="filter-block"
              value={selectedBlockId}
              onChange={(e) => setSelectedBlockId(e.target.value)}
            >
              <option value="">Todos os blocos</option>
              {blocks.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.title}
                </option>
              ))}
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="filter-difficulty">Dificuldade</label>
            <select
              id="filter-difficulty"
              value={selectedDifficulty}
              onChange={(e) => setSelectedDifficulty(e.target.value)}
            >
              <option value="">Todas as dificuldades</option>
              <option value="EASY">Fácil</option>
              <option value="INTERMEDIATE">Intermediária</option>
              <option value="HARD">Difícil</option>
            </select>
          </div>
        </div>
      </section>

      {error && <div className="feedback-banner error">{error}</div>}

      {loading && !data && (
        <div className="loading-state">
          <p>Carregando métricas de desempenho...</p>
        </div>
      )}

      {overall && (
        <>
          {/* Cards de Métricas Principais (KPIs) */}
          <div className="metrics-summary-grid">
            <div className="metric-card">
              <span className="metric-label">Total de Tentativas</span>
              <span className="metric-value">{overall.totalAttempts}</span>
              <span className="metric-subtext">registros no período</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Questões Únicas</span>
              <span className="metric-value">{overall.uniqueQuestionsAnswered}</span>
              <span className="metric-subtext">questões distintas praticadas</span>
            </div>

            <div className="metric-card">
              <span className="metric-label">Tentativas Avaliadas</span>
              <span className="metric-value">{overall.evaluatedAttempts}</span>
              <span className="metric-subtext">excluindo itens sem nota</span>
            </div>

            <div className="metric-card success">
              <span className="metric-label">Taxa de Precisão</span>
              <span className="metric-value">
                {overall.accuracyPercent !== null ? `${overall.accuracyPercent}%` : '—'}
              </span>
              <span className="metric-subtext">{overall.correctAttempts} acertos</span>
            </div>

            <div className="metric-card danger">
              <span className="metric-label">Taxa de Erro</span>
              <span className="metric-value">
                {overall.errorPercent !== null ? `${overall.errorPercent}%` : '—'}
              </span>
              <span className="metric-subtext">{overall.incorrectAttempts} erros</span>
            </div>
          </div>

          {/* Gráfico / Evolução Cronológica */}
          {data.timeline.length > 0 && (
            <section className="card timeline-section">
              <div className="section-heading">
                <div>
                  <h3>Evolução Cronológica no Período</h3>
                  <p>Volume diário de tentativas e assertividade ao longo do tempo.</p>
                </div>
              </div>
              <div className="timeline-bars-container">
                {data.timeline.map((point) => (
                  <div key={point.date} className="timeline-day-item">
                    <div className="timeline-bars">
                      <div
                        className="bar-correct"
                        style={{ height: `${Math.min(100, point.correctAttempts * 16)}px` }}
                        title={`${point.correctAttempts} acertos`}
                      />
                      <div
                        className="bar-incorrect"
                        style={{ height: `${Math.min(100, point.incorrectAttempts * 16)}px` }}
                        title={`${point.incorrectAttempts} erros`}
                      />
                    </div>
                    <span className="timeline-date-label">
                      {new Date(point.date).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })}
                    </span>
                    <span className="timeline-accuracy-badge">
                      {point.accuracyPercent !== null ? `${point.accuracyPercent}%` : '—'}
                    </span>
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* Detalhamento por Bloco */}
          <section className="card breakdown-section">
            <div className="section-heading">
              <div>
                <h3>Detalhamento por Bloco</h3>
                <p>Desempenho de cada bloco de estudo no período e nos filtros selecionados.</p>
              </div>
            </div>

            {data.blocks.length === 0 ? (
              <p className="empty-notice">Nenhum dado encontrado para os filtros selecionados.</p>
            ) : (
              <div className="breakdown-table-wrapper">
                <table className="breakdown-table">
                  <thead>
                    <tr>
                      <th>Bloco</th>
                      <th>Tentativas</th>
                      <th>Questões Únicas</th>
                      <th>Acertos</th>
                      <th>Parciais</th>
                      <th>Erros</th>
                      <th>Nota Média</th>
                      <th>Precisão</th>
                      {onSelectBlock && <th aria-label="Ações" />}
                    </tr>
                  </thead>
                  <tbody>
                    {data.blocks.map((block) => (
                      <tr key={block.studyBlockId}>
                        <td>
                          <strong>{block.blockTitle}</strong>
                        </td>
                        <td>{block.totalAttempts}</td>
                        <td>{block.uniqueQuestions}</td>
                        <td className="text-success">{block.correctAttempts}</td>
                        <td>{block.partialAttempts}</td>
                        <td className="text-danger">{block.incorrectAttempts}</td>
                        <td>{block.averageScorePercent !== null ? `${block.averageScorePercent}%` : '—'}</td>
                        <td>
                          <span className="badge accuracy">
                            {block.accuracyPercent !== null ? `${block.accuracyPercent}%` : '—'}
                          </span>
                        </td>
                        {onSelectBlock && (
                          <td>
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              onClick={() => onSelectBlock(block.studyBlockId)}
                            >
                              Abrir Bloco
                            </button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {/* Histórico Permanente de Tentativas (Snapshots) */}
          <section className="card history-audit-section">
            <div className="section-heading">
              <div>
                <h3>Histórico Permanente de Tentativas (Snapshots)</h3>
                <p>
                  Registro imutável preservado integralmente, mesmo após edição ou exclusão de questões, opções ou fontes.
                </p>
              </div>
            </div>

            {data.recentAttempts.length === 0 ? (
              <p className="empty-notice">Nenhuma tentativa registrada no histórico para os filtros selecionados.</p>
            ) : (
              <div className="historical-attempts-list">
                {data.recentAttempts.map((attempt) => (
                  <article
                    key={attempt.attemptId}
                    className={`card history-attempt-card ${attempt.correct ? 'status-correct' : 'status-incorrect'}`}
                  >
                    <div className="history-attempt-header">
                      <div className="tags-row">
                        <span className={`badge ${attempt.correct ? 'badge-success' : 'badge-danger'}`}>
                          {attempt.correct ? 'Acertou' : 'Errou'}
                        </span>
                        <span className="badge">{attempt.studyBlockTitle}</span>
                        <span className="badge">
                          {difficultyLabels[attempt.difficulty] ?? attempt.difficulty}
                        </span>
                        <span className="badge">
                          {formatLabels[attempt.questionFormat] ?? attempt.questionFormat}
                        </span>
                      </div>
                      <time className="history-time">
                        {new Date(attempt.answeredAt).toLocaleString('pt-BR', {
                          day: '2-digit',
                          month: '2-digit',
                          year: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </time>
                    </div>

                    <div className="history-prompt">
                      <strong>Enunciado:</strong> {attempt.prompt}
                    </div>

                    <div className="history-answers-grid">
                      <div className="history-user-answer">
                        <span className="label">Sua resposta:</span>
                        <p>{attempt.userAnswer || '—'}</p>
                      </div>
                      {attempt.expectedAnswer && (
                        <div className="history-expected-answer">
                          <span className="label">Resposta esperada:</span>
                          <p>{attempt.expectedAnswer}</p>
                        </div>
                      )}
                    </div>

                    {attempt.explanation && (
                      <div className="history-explanation">
                        <span className="label">Explicação / Justificativa:</span>
                        <p>{attempt.explanation}</p>
                      </div>
                    )}

                    {attempt.sourceTitle && (
                      <div className="history-source-info">
                        <span className="label">Fonte de referência:</span>
                        <p>
                          <strong>{attempt.sourceTitle}</strong>
                          {attempt.sourceExcerpt && ` — "${attempt.sourceExcerpt}"`}
                        </p>
                      </div>
                    )}
                  </article>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
