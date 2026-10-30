import { useCallback, useEffect, useState } from 'react'
import { request } from '../api/http'
import { CheckCircleIcon, FileTextIcon, FolderIcon, SparklesIcon } from '../components/icons'
import { SimuladoConfigModal } from '../features/questions/SimuladoConfigModal'
import type { StudyBlock } from './StudyBlocksPage'
import '../styles/pages/exercises-questions.css'

export type QuestionDifficulty = 'EASY' | 'INTERMEDIATE' | 'HARD'
export type QuestionFormat = 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'DISCURSIVE'

export type QuestionOption = {
  id: string
  text: string
  order: number
}

export type StudyQuestion = {
  id: string
  studyBlockId: string
  studyBlockTitle?: string
  materialId?: string | null
  materialTitle?: string | null
  sourceExcerpt?: string | null
  prompt: string
  explanation: string | null
  difficulty: QuestionDifficulty
  format: QuestionFormat
  expectedAnswer: string | null
  options: QuestionOption[]
  status: 'ACTIVE' | 'ARCHIVED'
  nextReviewAt: string
  reviewIntervalDays: number
  correctStreak: number
}

type Props = {
  mode: 'block' | 'global'
  blocks: StudyBlock[]
  selectedBlockId: string | null
  onSelectBlock: (id: string | null) => void
}

const difficultyLabels: Record<QuestionDifficulty, string> = {
  EASY: 'Fácil',
  INTERMEDIATE: 'Intermediária',
  HARD: 'Difícil',
}

const formatLabels: Record<QuestionFormat, string> = {
  MULTIPLE_CHOICE: 'Múltipla Escolha',
  TRUE_FALSE: 'Verdadeiro ou Falso',
  DISCURSIVE: 'Discursiva',
}

export function TheoreticalQuestionsPage({
  mode,
  blocks,
  selectedBlockId,
  onSelectBlock,
}: Props) {
  const [questions, setQuestions] = useState<StudyQuestion[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Filtros
  const [difficultyFilter, setDifficultyFilter] = useState<string>('ALL')
  const [formatFilter, setFormatFilter] = useState<string>('ALL')

  // Resoluções de respostas
  const [selectedOptions, setSelectedOptions] = useState<Record<string, string>>({})
  const [discursiveInputs, setDiscursiveInputs] = useState<Record<string, string>>({})
  const [questionResults, setQuestionResults] = useState<
    Record<string, { correct: boolean; explanation: string | null }>
  >({})
  const [submittingId, setSubmittingId] = useState<string | null>(null)

  // Visualização de citações
  const [viewingExcerpt, setViewingExcerpt] = useState<Record<string, boolean>>({})

  // Modal do Gerador de Simulado por IA
  const [isSimuladoModalOpen, setIsSimuladoModalOpen] = useState(false)

  const activeBlock = blocks.find((b) => b.id === selectedBlockId)

  const loadQuestions = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (mode === 'block') {
        if (!selectedBlockId) {
          setQuestions([])
          return
        }
        const data = await request<StudyQuestion[]>(`/api/study-blocks/${selectedBlockId}/questions`)
        setQuestions(data.map((q) => ({ ...q, studyBlockId: selectedBlockId, studyBlockTitle: activeBlock?.title })))
      } else {
        // Modo Global: reúne questões de todos os blocos
        const allQuestions: StudyQuestion[] = []
        for (const block of blocks.filter((b) => b.status === 'ACTIVE')) {
          try {
            const data = await request<StudyQuestion[]>(`/api/study-blocks/${block.id}/questions`)
            allQuestions.push(
              ...data.map((q) => ({ ...q, studyBlockId: block.id, studyBlockTitle: block.title }))
            )
          } catch {
            // Ignora bloco isolado
          }
        }
        setQuestions(allQuestions)
      }
    } catch {
      setError('Não foi possível carregar as questões teóricas.')
    } finally {
      setLoading(false)
    }
  }, [mode, selectedBlockId, activeBlock, blocks])

  useEffect(() => {
    void loadQuestions()
  }, [loadQuestions])

  const handleAnswerQuestion = async (q: StudyQuestion, optId?: string) => {
    setSubmittingId(q.id)
    try {
      let body: { optionId?: string; discursiveAnswer?: string } = {}
      if (q.format === 'DISCURSIVE') {
        const text = discursiveInputs[q.id]?.trim()
        if (!text) {
          alert('Por favor, digite sua resposta antes de enviar.')
          setSubmittingId(null)
          return
        }
        body = { discursiveAnswer: text }
      } else {
        const optionId = optId || selectedOptions[q.id]
        if (!optionId) {
          alert('Selecione uma alternativa antes de enviar.')
          setSubmittingId(null)
          return
        }
        body = { optionId }
      }

      const res = await request<{ correct: boolean; explanation: string | null }>(
        `/api/study-blocks/${q.studyBlockId}/questions/${q.id}/answers`,
        {
          method: 'POST',
          body: JSON.stringify(body),
        }
      )

      setQuestionResults((prev) => ({
        ...prev,
        [q.id]: res,
      }))
    } catch {
      alert('Falha ao submeter resposta da questão.')
    } finally {
      setSubmittingId(null)
    }
  }

  const filteredQuestions = questions.filter((q) => {
    const matchesDiff = difficultyFilter === 'ALL' || q.difficulty === difficultyFilter
    const matchesFormat = formatFilter === 'ALL' || q.format === formatFilter
    return matchesDiff && matchesFormat
  })

  return (
    <div className="page-wrapper">
      {/* Header */}
      <div className="page-header-flex">
        <div>
          <div className="flex-inline-gap">
            <h1 className="page-main-title">
              {mode === 'block' ? 'Exercícios Teóricos (Por Bloco)' : 'Exercícios Teóricos Globais (Todos os Conteúdos)'}
            </h1>
            <span className="badge badge-emerald">
              {mode === 'block' ? 'Bloco Específico' : 'Simulado Geral'}
            </span>
          </div>
          <p className="page-main-subtitle">
            {mode === 'block'
              ? 'Fixação conceitual, questões de múltipla escolha e discursivas baseadas estritamente nas fontes do bloco selecionado.'
              : 'Simulado geral contínuo integrando todo o conhecimento acumulado na plataforma.'}
          </p>
        </div>

        <div className="page-actions-group">
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setIsSimuladoModalOpen(true)}
            disabled={mode === 'block' && !selectedBlockId}
          >
            <SparklesIcon className="w-4 h-4" />
            <span>Gerar Questões com IA</span>
          </button>
        </div>
      </div>

      {/* Toolbar / Filtros */}
      <div className="card toolbar-card mb-6">
        <div className="toolbar-flex-row">
          {mode === 'block' && (
            <div className="flex-gap-center flex-1">
              <FolderIcon className="w-4 h-4 text-emerald-400" />
              <select
                className="form-select"
                value={selectedBlockId || ''}
                onChange={(e) => onSelectBlock(e.target.value || null)}
              >
                <option value="">-- Selecione um Bloco --</option>
                {blocks.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.title}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="filter-select-group">
            <label className="text-xs text-muted">Dificuldade:</label>
            <select
              className="form-select form-select-sm"
              value={difficultyFilter}
              onChange={(e) => setDifficultyFilter(e.target.value)}
            >
              <option value="ALL">Todas</option>
              <option value="EASY">Fácil</option>
              <option value="INTERMEDIATE">Intermediária</option>
              <option value="HARD">Difícil</option>
            </select>
          </div>

          <div className="filter-select-group">
            <label className="text-xs text-muted">Formato:</label>
            <select
              className="form-select form-select-sm"
              value={formatFilter}
              onChange={(e) => setFormatFilter(e.target.value)}
            >
              <option value="ALL">Todos os Formatos</option>
              <option value="MULTIPLE_CHOICE">Múltipla Escolha</option>
              <option value="TRUE_FALSE">Verdadeiro ou Falso</option>
              <option value="DISCURSIVE">Discursiva</option>
            </select>
          </div>
        </div>
      </div>

      {error && <div className="alert-error-banner mb-6">{error}</div>}

      {/* Lista de Questões */}
      {loading ? (
        <div className="loading-state-card">Carregando questões...</div>
      ) : filteredQuestions.length === 0 ? (
        <div className="empty-state-box">
          <FileTextIcon className="empty-icon" />
          <h3>Nenhuma questão teórica encontrada</h3>
          <p>
            {mode === 'block'
              ? 'Este bloco ainda não possui questões. Gere novas questões com o botão acima!'
              : 'Nenhum simulado disponível com os filtros selecionados.'}
          </p>
        </div>
      ) : (
        <div className="questions-list-stack">
          {filteredQuestions.map((q, idx) => {
            const result = questionResults[q.id]
            const isSubmitting = submittingId === q.id

            return (
              <div key={q.id} className="card question-item-card">
                <div className="question-card-header">
                  <div className="flex-gap-center">
                    <span className="question-num-pill">#{idx + 1}</span>
                    <span className="badge badge-subtle">{formatLabels[q.format] || q.format}</span>
                    <span className={`difficulty-badge ${q.difficulty.toLowerCase()}`}>
                      {difficultyLabels[q.difficulty] || q.difficulty}
                    </span>
                  </div>

                  <div className="flex-gap-center">
                    {q.studyBlockTitle && (
                      <span className="badge badge-emerald">{q.studyBlockTitle}</span>
                    )}
                    <span className="text-xs text-muted">
                      Intervalo: {q.reviewIntervalDays}d | Streak: {q.correctStreak}
                    </span>
                  </div>
                </div>

                {/* Enunciado */}
                <div className="question-prompt-text">
                  <p>{q.prompt}</p>
                </div>

                {/* Excerto de Apoio */}
                {q.sourceExcerpt && (
                  <div className="source-excerpt-accordion">
                    <button
                      type="button"
                      className="btn-text-toggle"
                      onClick={() =>
                        setViewingExcerpt((prev) => ({ ...prev, [q.id]: !prev[q.id] }))
                      }
                    >
                      {viewingExcerpt[q.id] ? '▲ Ocultar Trecho da Fonte Canônica' : '▼ Ver Trecho da Fonte Canônica'}
                    </button>
                    {viewingExcerpt[q.id] && (
                      <blockquote className="excerpt-blockquote">
                        {q.materialTitle && <strong>Fonte: {q.materialTitle}</strong>}
                        <p>{q.sourceExcerpt}</p>
                      </blockquote>
                    )}
                  </div>
                )}

                {/* Alternativas Objetivas / V ou F */}
                {q.format !== 'DISCURSIVE' ? (
                  <div className="question-options-stack">
                    {q.options.map((opt) => {
                      const isSelected = selectedOptions[q.id] === opt.id

                      return (
                        <label
                          key={opt.id}
                          className={`question-option-item ${isSelected ? 'selected' : ''}`}
                        >
                          <input
                            type="radio"
                            name={`question-${q.id}`}
                            value={opt.id}
                            checked={isSelected}
                            onChange={() =>
                              setSelectedOptions((prev) => ({ ...prev, [q.id]: opt.id }))
                            }
                            disabled={!!result}
                          />
                          <span className="option-text">{opt.text}</span>
                        </label>
                      )
                    })}
                  </div>
                ) : (
                  /* Campo Discursivo */
                  <div className="discursive-input-container">
                    <label htmlFor={`discursive-${q.id}`} className="text-xs text-muted block mb-1">
                      Sua Resposta Discursiva:
                    </label>
                    <textarea
                      id={`discursive-${q.id}`}
                      className="form-textarea"
                      rows={4}
                      placeholder="Redija sua resposta técnica para avaliação semântica dos critérios..."
                      value={discursiveInputs[q.id] || ''}
                      onChange={(e) =>
                        setDiscursiveInputs((prev) => ({ ...prev, [q.id]: e.target.value }))
                      }
                      disabled={!!result}
                    />
                  </div>
                )}

                {/* Botão de Envio */}
                {!result && (
                  <div className="question-actions-bar">
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={() => void handleAnswerQuestion(q)}
                      disabled={isSubmitting}
                    >
                      {isSubmitting ? 'Avaliando Gabarito...' : 'Confirmar Resposta'}
                    </button>
                  </div>
                )}

                {/* Feedback e Gabarito */}
                {result && (
                  <div className={`answer-feedback-card ${result.correct ? 'correct' : 'incorrect'}`}>
                    <div className="flex-gap-center mb-1">
                      <CheckCircleIcon className="w-5 h-5 flex-shrink-0" />
                      <strong>{result.correct ? 'Resposta Correta!' : 'Resposta Incorreta'}</strong>
                    </div>
                    {result.explanation && (
                      <p className="explanation-text">{result.explanation}</p>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Modal de Simulado / Geração por IA */}
      {isSimuladoModalOpen && selectedBlockId && (
        <SimuladoConfigModal
          blockId={selectedBlockId}
          blockTitle={activeBlock?.title || 'Bloco'}
          isOpen={isSimuladoModalOpen}
          activeSourceCount={1}
          onClose={() => setIsSimuladoModalOpen(false)}
          onSimuladoGenerated={() => {
            setIsSimuladoModalOpen(false)
            void loadQuestions()
          }}
        />
      )}
    </div>
  )
}
