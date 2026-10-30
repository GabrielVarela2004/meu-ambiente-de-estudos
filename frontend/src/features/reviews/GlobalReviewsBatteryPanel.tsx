import { useEffect, useState, useCallback } from 'react'
import { request } from '../../api/http'

export type QuestionDifficulty = 'EASY' | 'INTERMEDIATE' | 'HARD'
export type QuestionFormat = 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'DISCURSIVE'
export type RetentionLevel = 'MEMORIZED' | 'LEARNING' | 'NEEDS_REINFORCEMENT'

export type DueReviewQuestion = {
  id: string
  studyBlockId: string
  studyBlockTitle: string
  prompt: string
  explanation: string | null
  difficulty: QuestionDifficulty
  format: QuestionFormat
  options: { id: string; text: string; order: number }[]
  nextReviewAt: string
  reviewIntervalDays: number
  correctStreak: number
  recommendationReason: string
  retentionLevel?: RetentionLevel
  expectedAnswer?: string | null
}

export type GlobalReviewSummary = {
  totalDue: number
  memorizedCount: number
  learningCount: number
  needsReinforcementCount: number
  totalActiveQuestions: number
}

type AnswerResult = {
  correct: boolean
  explanation: string
}

type Props = {
  onSelectBlock?: (blockId: string) => void
  onReviewCompleted?: () => void
}

const difficultyLabels: Record<QuestionDifficulty, string> = {
  EASY: 'Fácil',
  INTERMEDIATE: 'Média',
  HARD: 'Difícil',
}

const retentionLabels: Record<RetentionLevel, { text: string; className: string }> = {
  MEMORIZED: { text: 'Memorizado', className: 'retention-badge memorized' },
  LEARNING: { text: 'Em Aprendizado', className: 'retention-badge learning' },
  NEEDS_REINFORCEMENT: { text: 'Precisa Reforço', className: 'retention-badge reinforcement' },
}

export function GlobalReviewsBatteryPanel({ onSelectBlock, onReviewCompleted }: Props) {
  const [summary, setSummary] = useState<GlobalReviewSummary | null>(null)
  const [questions, setQuestions] = useState<DueReviewQuestion[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Battery execution mode
  const [batteryMode, setBatteryMode] = useState(false)
  const [currentIndex, setCurrentIndex] = useState(0)
  const [batteryAnswers, setBatteryAnswers] = useState<Record<string, AnswerResult>>({})
  const [batteryStats, setBatteryStats] = useState({ correct: 0, wrong: 0 })
  const [discursiveText, setDiscursiveText] = useState('')
  const [submittingAnswer, setSubmittingAnswer] = useState(false)
  const [feedbackMessage, setFeedbackMessage] = useState<string | null>(null)

  // Filtering for list mode
  const [difficultyFilter, setDifficultyFilter] = useState<'ALL' | QuestionDifficulty>('ALL')
  const [discursiveListAnswers, setDiscursiveListAnswers] = useState<Record<string, string>>({})
  const [inlineResults, setInlineResults] = useState<Record<string, AnswerResult>>({})

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [sum, battery] = await Promise.all([
        request<GlobalReviewSummary>('/api/reviews/summary'),
        request<DueReviewQuestion[]>('/api/reviews/battery?limit=25'),
      ])
      setSummary(sum)
      setQuestions(battery)
    } catch {
      setError('Não foi possível carregar a fila de repetição espaçada.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadData()
  }, [loadData])

  const startBattery = () => {
    if (questions.length === 0) return
    setCurrentIndex(0)
    setBatteryAnswers({})
    setBatteryStats({ correct: 0, wrong: 0 })
    setDiscursiveText('')
    setFeedbackMessage(null)
    setBatteryMode(true)
  }

  const exitBattery = () => {
    setBatteryMode(false)
    void loadData()
    if (onReviewCompleted) onReviewCompleted()
  }

  const handleAnswerBattery = async (optionId?: string, textAnswer?: string) => {
    const currentQ = questions[currentIndex]
    if (!currentQ || submittingAnswer) return

    setSubmittingAnswer(true)
    setFeedbackMessage(null)

    try {
      const payload: { optionId?: string; discursiveAnswer?: string } = {}
      if (optionId) payload.optionId = optionId
      if (textAnswer) payload.discursiveAnswer = textAnswer

      const res = await request<AnswerResult>(
        `/api/study-blocks/${currentQ.studyBlockId}/questions/${currentQ.id}/answers`,
        {
          method: 'POST',
          body: JSON.stringify(payload),
        },
      )

      setBatteryAnswers((prev) => ({ ...prev, [currentQ.id]: res }))
      if (res.correct) {
        setBatteryStats((prev) => ({ ...prev, correct: prev.correct + 1 }))
        const nextStreak = currentQ.correctStreak + 1
        setFeedbackMessage(`Acertou! Streak aumentou para ${nextStreak}. Intervalo expandido com base na dificuldade ${difficultyLabels[currentQ.difficulty]}.`)
      } else {
        setBatteryStats((prev) => ({ ...prev, wrong: prev.wrong + 1 }))
        setFeedbackMessage(`Incorreto. A questão retornou ao intervalo de 1 dia para fixação da dificuldade ${difficultyLabels[currentQ.difficulty]}.`)
      }
    } catch {
      setError('Falha ao registrar resposta da revisão.')
    } finally {
      setSubmittingAnswer(false)
    }
  }

  const handleNextInBattery = () => {
    setFeedbackMessage(null)
    setDiscursiveText('')
    if (currentIndex < questions.length - 1) {
      setCurrentIndex((prev) => prev + 1)
    } else {
      // Completed battery
      setCurrentIndex(questions.length)
    }
  }

  const handleReschedule = async (questionId: string, days: number) => {
    try {
      await request<DueReviewQuestion>(
        `/api/reviews/questions/${questionId}/reschedule?days=${days}`,
        { method: 'POST' },
      )
      setQuestions((prev) => prev.filter((q) => q.id !== questionId))
      if (summary) {
        setSummary({ ...summary, totalDue: Math.max(0, summary.totalDue - 1) })
      }
      if (onReviewCompleted) onReviewCompleted()
    } catch {
      setError('Não foi possível adiar a revisão da questão.')
    }
  }

  const handleAnswerInline = async (question: DueReviewQuestion, optionId?: string) => {
    try {
      const payload: { optionId?: string; discursiveAnswer?: string } = {}
      if (optionId) {
        payload.optionId = optionId
      } else if (discursiveListAnswers[question.id]) {
        payload.discursiveAnswer = discursiveListAnswers[question.id]
      } else {
        return
      }

      const res = await request<AnswerResult>(
        `/api/study-blocks/${question.studyBlockId}/questions/${question.id}/answers`,
        {
          method: 'POST',
          body: JSON.stringify(payload),
        },
      )

      setInlineResults((prev) => ({ ...prev, [question.id]: res }))
      void loadData()
      if (onReviewCompleted) onReviewCompleted()
    } catch {
      setError('Erro ao enviar resposta inline.')
    }
  }

  const filteredQuestions = questions.filter((q) => {
    if (difficultyFilter === 'ALL') return true
    return q.difficulty === difficultyFilter
  })

  if (loading && !summary) {
    return <p className="empty">Carregando painel de revisão espaçada...</p>
  }

  return (
    <section className="global-reviews-container card">
      <div className="section-heading">
        <div>
          <h2>Revisão Espaçada Global Baseada em Dificuldade</h2>
          <p>
            Algoritmo inteligente de retenção: questões difíceis têm ciclos mais curtos e progressão criteriosa;
            acertos consistentes expandem o intervalo e erros reiniciam a sequência para garantir consolidação na memória.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          {!batteryMode && questions.length > 0 && (
            <button type="button" className="primary" onClick={startBattery}>
              Iniciar Bateria de Revisão ({questions.length})
            </button>
          )}
          <button type="button" className="secondary" onClick={() => void loadData()}>
            Atualizar
          </button>
        </div>
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      {/* Retention KPIs Cards */}
      {summary && (
        <div className="retention-kpi-grid">
          <div className="retention-kpi-card due">
            <span className="kpi-label">Vencidas Hoje</span>
            <span className="kpi-value">{summary.totalDue}</span>
            <span className="kpi-sub">Priorizadas por dificuldade</span>
          </div>
          <div className="retention-kpi-card reinforcement">
            <span className="kpi-label">Precisando de Reforço</span>
            <span className="kpi-value">{summary.needsReinforcementCount}</span>
            <span className="kpi-sub">Streak 0 ou erros recentes</span>
          </div>
          <div className="retention-kpi-card learning">
            <span className="kpi-label">Em Aprendizado</span>
            <span className="kpi-value">{summary.learningCount}</span>
            <span className="kpi-sub">Streak 1 a 2</span>
          </div>
          <div className="retention-kpi-card memorized">
            <span className="kpi-label">Memorizadas</span>
            <span className="kpi-value">{summary.memorizedCount}</span>
            <span className="kpi-sub">Streak ≥ 3 acertos</span>
          </div>
        </div>
      )}

      {/* BATTERY MODE */}
      {batteryMode ? (
        <div className="battery-session-wrapper">
          {currentIndex < questions.length ? (
            (() => {
              const currentQ = questions[currentIndex]
              const currentResult = batteryAnswers[currentQ.id]
              const retention = currentQ.retentionLevel ?? 'NEEDS_REINFORCEMENT'

              return (
                <div className="battery-card card">
                  <div className="battery-progress-bar">
                    <div
                      className="battery-progress-fill"
                      style={{ width: `${((currentIndex + 1) / questions.length) * 100}%` }}
                    />
                  </div>
                  <div className="battery-header">
                    <span>
                      Questão {currentIndex + 1} de {questions.length} · Bloco: <strong>{currentQ.studyBlockTitle}</strong>
                    </span>
                    <div style={{ display: 'flex', gap: '6px' }}>
                      <span className={`badge difficulty-${currentQ.difficulty.toLowerCase()}`}>
                        {difficultyLabels[currentQ.difficulty]}
                      </span>
                      <span className={retentionLabels[retention].className}>
                        {retentionLabels[retention].text}
                      </span>
                    </div>
                  </div>

                  <p className="recommendation-badge-line">
                    <small>Motivo de agendamento: {currentQ.recommendationReason}</small>
                  </p>

                  <h3 className="battery-prompt">{currentQ.prompt}</h3>

                  {currentQ.format === 'DISCURSIVE' ? (
                    <div className="discursive-battery-box">
                      <textarea
                        maxLength={10000}
                        rows={4}
                        placeholder="Escreva sua resposta para avaliação..."
                        value={discursiveText}
                        disabled={!!currentResult || submittingAnswer}
                        onChange={(e) => setDiscursiveText(e.target.value)}
                      />
                      {!currentResult && (
                        <button
                          type="button"
                          className="primary"
                          disabled={!discursiveText.trim() || submittingAnswer}
                          onClick={() => void handleAnswerBattery(undefined, discursiveText)}
                        >
                          {submittingAnswer ? 'Avaliando resposta...' : 'Confirmar e Avaliar'}
                        </button>
                      )}
                    </div>
                  ) : (
                    <div className="options-battery-list">
                      {currentQ.options.map((opt) => (
                        <button
                          type="button"
                          key={opt.id}
                          className="option-battery-btn"
                          disabled={!!currentResult || submittingAnswer}
                          onClick={() => void handleAnswerBattery(opt.id)}
                        >
                          {opt.text}
                        </button>
                      ))}
                    </div>
                  )}

                  {/* Feedback on answer */}
                  {currentResult && (
                    <div className={`battery-feedback-box ${currentResult.correct ? 'correct' : 'wrong'}`}>
                      <h4>{currentResult.correct ? '✔ Resposta Correta!' : '✖ Resposta Incorreta'}</h4>
                      <p>{currentResult.explanation}</p>
                      {feedbackMessage && <p className="streak-feedback">{feedbackMessage}</p>}
                      <div style={{ marginTop: '12px' }}>
                        <button type="button" className="primary" onClick={handleNextInBattery}>
                          {currentIndex < questions.length - 1 ? 'Próxima Questão →' : 'Finalizar Bateria'}
                        </button>
                      </div>
                    </div>
                  )}

                  {/* Controls to reschedule or exit */}
                  <div className="battery-footer-actions">
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => void handleReschedule(currentQ.id, 1)}
                    >
                      Adiar +1 dia
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => void handleReschedule(currentQ.id, 3)}
                    >
                      Adiar +3 dias
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => void handleReschedule(currentQ.id, 7)}
                    >
                      Adiar +7 dias
                    </button>
                    <button type="button" className="danger" onClick={exitBattery}>
                      Encerrar Sessão
                    </button>
                  </div>
                </div>
              )
            })()
          ) : (
            <div className="battery-completion-card card">
              <h3>🎉 Bateria de Revisão Concluída!</h3>
              <p>Você revisou todas as questões programadas para este ciclo.</p>
              <div className="battery-summary-stats">
                <div>
                  <strong>{batteryStats.correct}</strong>
                  <span>Acertos</span>
                </div>
                <div>
                  <strong>{batteryStats.wrong}</strong>
                  <span>Erros</span>
                </div>
                <div>
                  <strong>
                    {batteryStats.correct + batteryStats.wrong > 0
                      ? Math.round((batteryStats.correct / (batteryStats.correct + batteryStats.wrong)) * 100)
                      : 0}
                    %
                  </strong>
                  <span>Aproveitamento</span>
                </div>
              </div>
              <button type="button" className="primary" onClick={exitBattery}>
                Voltar à Lista de Revisões
              </button>
            </div>
          )}
        </div>
      ) : (
        /* LIST / TABLE MODE */
        <div className="reviews-list-mode">
          <div className="filters-bar" style={{ display: 'flex', gap: '12px', alignItems: 'center', marginBottom: '16px' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              Filtrar por dificuldade:
              <select
                value={difficultyFilter}
                onChange={(e) => setDifficultyFilter(e.target.value as 'ALL' | QuestionDifficulty)}
              >
                <option value="ALL">Todas as dificuldades</option>
                <option value="HARD">Apenas Difíceis (Prioritárias)</option>
                <option value="INTERMEDIATE">Intermediárias</option>
                <option value="EASY">Fáceis</option>
              </select>
            </label>
            <span style={{ fontSize: '0.9rem', color: '#64748b' }}>
              Exibindo {filteredQuestions.length} de {questions.length} questão(ões)
            </span>
          </div>

          {filteredQuestions.length === 0 ? (
            <p className="empty">Nenhuma questão encontrada para os critérios selecionados.</p>
          ) : (
            <div className="question-list">
              {filteredQuestions.map((q) => {
                const res = inlineResults[q.id]
                const retention = q.retentionLevel ?? 'NEEDS_REINFORCEMENT'

                return (
                  <article className="card question-card" key={q.id}>
                    <div className="block-card-header">
                      <div>
                        <h3 style={{ margin: '0 0 6px 0' }}>{q.prompt}</h3>
                        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                          <span className="badge">{q.studyBlockTitle}</span>
                          <span className={`badge difficulty-${q.difficulty.toLowerCase()}`}>
                            {difficultyLabels[q.difficulty]}
                          </span>
                          <span className={retentionLabels[retention].className}>
                            {retentionLabels[retention].text}
                          </span>
                          <small style={{ color: '#64748b' }}>
                            Streak atual: {q.correctStreak} | Intervalo: {q.reviewIntervalDays}d
                          </small>
                        </div>
                      </div>
                    </div>

                    <p className="review-detail" style={{ fontSize: '0.85rem', color: '#475569', margin: '8px 0' }}>
                      <strong>Motivo da recomendação:</strong> {q.recommendationReason}
                    </p>

                    {q.format === 'DISCURSIVE' ? (
                      <div className="discursive-answer-box" style={{ marginTop: '8px' }}>
                        <textarea
                          maxLength={10000}
                          value={discursiveListAnswers[q.id] ?? ''}
                          onChange={(e) =>
                            setDiscursiveListAnswers({ ...discursiveListAnswers, [q.id]: e.target.value })
                          }
                          placeholder="Escreva sua resposta discursiva..."
                        />
                        <button
                          type="button"
                          className="primary"
                          onClick={() => void handleAnswerInline(q)}
                          disabled={!discursiveListAnswers[q.id]?.trim()}
                        >
                          Avaliar resposta
                        </button>
                      </div>
                    ) : (
                      <div className="answer-options" style={{ marginTop: '8px' }}>
                        {q.options.map((opt) => (
                          <button
                            type="button"
                            className="secondary answer-option"
                            key={opt.id}
                            onClick={() => void handleAnswerInline(q, opt.id)}
                          >
                            {opt.text}
                          </button>
                        ))}
                      </div>
                    )}

                    {res && (
                      <p className={res.correct ? 'answer-correct' : 'answer-wrong'} style={{ marginTop: '8px' }}>
                        {res.correct ? '✔ Resposta correta!' : '✖ Resposta incorreta.'} {res.explanation}
                      </p>
                    )}

                    <div className="card-actions" style={{ marginTop: '12px' }}>
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => void handleReschedule(q.id, 1)}
                      >
                        Adiar 1 dia
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => void handleReschedule(q.id, 3)}
                      >
                        Adiar 3 dias
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => void handleReschedule(q.id, 7)}
                      >
                        Adiar 7 dias
                      </button>
                      {onSelectBlock && (
                        <button type="button" className="primary" onClick={() => onSelectBlock(q.studyBlockId)}>
                          Abrir bloco
                        </button>
                      )}
                    </div>
                  </article>
                )
              })}
            </div>
          )}
        </div>
      )}
    </section>
  )
}
