import { useCallback, useEffect, useState } from 'react'
import { request } from '../api/http'
import { CodeIcon, FolderIcon, SparklesIcon, CheckCircleIcon } from '../components/icons'
import type { StudyBlock } from './StudyBlocksPage'

export type PracticalExercise = {
  id: string
  studyBlockId?: string
  studyBlockTitle?: string
  studyMaterialId?: string | null
  studyMaterialTitle?: string | null
  sourceExcerpt?: string | null
  title: string
  prompt: string
  guidance: string | null
  difficulty?: 'EASY' | 'INTERMEDIATE' | 'HARD'
  exerciseType?: string
  starterCode?: string | null
  solutionCode?: string | null
  hintsJson?: string | null
  testCasesJson?: string | null
  status: 'ACTIVE' | 'ARCHIVED'
}

type Props = {
  mode: 'block' | 'global'
  blocks: StudyBlock[]
  selectedBlockId: string | null
  onSelectBlock: (id: string | null) => void
}

export function PracticalExercisesPage({
  mode,
  blocks,
  selectedBlockId,
  onSelectBlock,
}: Props) {
  const [exercises, setExercises] = useState<PracticalExercise[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Submissão e respostas
  const [answers, setAnswers] = useState<Record<string, string>>({})
  const [ratings, setRatings] = useState<Record<string, number>>({})
  const [feedback, setFeedback] = useState<Record<string, { selfRating: number; passed: boolean }>>({})
  const [submittingId, setSubmittingId] = useState<string | null>(null)

  // Visualização de excerto / dicas
  const [viewingExcerpt, setViewingExcerpt] = useState<Record<string, boolean>>({})

  // Geração com IA
  const [generating, setGenerating] = useState(false)

  const activeBlock = blocks.find((b) => b.id === selectedBlockId)

  const loadExercises = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (mode === 'block') {
        if (!selectedBlockId) {
          setExercises([])
          return
        }
        const data = await request<PracticalExercise[]>(`/api/study-blocks/${selectedBlockId}/exercises`)
        setExercises(data)
      } else {
        // Modo Global: reúne exercícios de todos os blocos ativos
        const allExercises: PracticalExercise[] = []
        for (const block of blocks.filter((b) => b.status === 'ACTIVE')) {
          try {
            const blockExs = await request<PracticalExercise[]>(`/api/study-blocks/${block.id}/exercises`)
            allExercises.push(
              ...blockExs.map((ex) => ({
                ...ex,
                studyBlockTitle: ex.studyBlockTitle || block.title,
              }))
            )
          } catch {
            // Continua coletando dos outros blocos
          }
        }
        setExercises(allExercises)
      }
    } catch {
      setError('Não foi possível carregar os exercícios práticos.')
    } finally {
      setLoading(false)
    }
  }, [mode, selectedBlockId, blocks])

  useEffect(() => {
    void loadExercises()
  }, [loadExercises])

  const handleGenerateAi = async () => {
    const targetBlockId = selectedBlockId || (blocks.length > 0 ? blocks[0].id : null)
    if (!targetBlockId) {
      setError('Selecione um bloco de estudos para gerar exercícios práticos.')
      return
    }

    setGenerating(true)
    setError(null)
    try {
      const generated = await request<PracticalExercise>(
        `/api/study-blocks/${targetBlockId}/exercises/generate`,
        {
          method: 'POST',
          body: JSON.stringify({
            difficulty: 'INTERMEDIATE',
            exerciseType: 'CODE_IMPLEMENTATION',
          }),
        }
      )
      setExercises((prev) => [generated, ...prev])
    } catch {
      setError('Erro ao gerar exercício prático via IA. Verifique se o bloco possui fontes cadastradas.')
    } finally {
      setGenerating(false)
    }
  }

  const handleSubmitExercise = async (ex: PracticalExercise) => {
    const userAnswer = answers[ex.id] ?? (ex.starterCode || '')
    const selfRating = ratings[ex.id] || 4

    setSubmittingId(ex.id)
    try {
      // O backend registra a tentativa com a autoavaliação (1–5); não há correção automática.
      const res = await request<{ id: string; selfRating: number }>(
        `/api/study-blocks/${ex.studyBlockId || selectedBlockId}/exercises/${ex.id}/attempts`,
        {
          method: 'POST',
          body: JSON.stringify({
            answer: userAnswer,
            selfRating,
          }),
        }
      )
      setFeedback((prev) => ({
        ...prev,
        [ex.id]: {
          selfRating: res.selfRating,
          passed: res.selfRating >= 4,
        },
      }))
    } catch {
      setError('Erro ao enviar avaliação do exercício.')
    } finally {
      setSubmittingId(null)
    }
  }

  return (
    <div className="page-wrapper">
      {/* Header */}
      <div className="page-header-flex">
        <div>
          <div className="flex-inline-gap">
            <h1 className="page-main-title">
              {mode === 'block'
                ? activeBlock
                  ? `Exercícios Práticos: ${activeBlock.title}`
                  : 'Exercícios Práticos (Por Bloco)'
                : 'Exercícios Práticos Globais (Todos os Conteúdos)'}
            </h1>
            <span className="badge badge-emerald">
              {mode === 'block' ? 'Bloco Específico' : 'Visão Geral'}
            </span>
          </div>
          <p className="page-main-subtitle">
            {mode === 'block'
              ? 'Pratique implementações de código, comandos e cenários reais focados exclusivamente no bloco de estudo selecionado.'
              : 'Treine com o acervo completo de exercícios práticos de todos os seus blocos de estudo.'}
          </p>
        </div>

        <div className="page-actions-group">
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleGenerateAi}
            disabled={generating || (mode === 'block' && !selectedBlockId)}
          >
            <SparklesIcon className="w-4 h-4" />
            <span>{generating ? 'Gerando com IA...' : 'Gerar Exercício Prático'}</span>
          </button>
        </div>
      </div>

      {/* Seletor de Bloco no Modo Específico */}
      {mode === 'block' && (
        <div className="card toolbar-card mb-6">
          <div className="flex-gap-center flex-1">
            <FolderIcon className="w-5 h-5 text-emerald-400" />
            <div className="flex-1">
              <label htmlFor="practical-block-picker" className="text-xs text-muted block mb-1">
                Selecione o Bloco de Estudo:
              </label>
              <select
                id="practical-block-picker"
                className="form-select w-full"
                value={selectedBlockId || ''}
                onChange={(e) => onSelectBlock(e.target.value || null)}
              >
                <option value="">-- Escolha um bloco --</option>
                {blocks.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.title} ({b.status === 'ACTIVE' ? 'Ativo' : 'Arquivado'})
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>
      )}

      {error && <div className="alert-error-banner mb-6">{error}</div>}

      {/* Lista de Exercícios */}
      {loading ? (
        <div className="loading-state-card">Carregando exercícios práticos...</div>
      ) : exercises.length === 0 ? (
        <div className="empty-state-box">
          <CodeIcon className="empty-icon" />
          <h3>Nenhum exercício prático encontrado</h3>
          <p>
            {mode === 'block'
              ? 'Este bloco ainda não possui exercícios. Clique no botão acima para gerar o primeiro exercício com IA a partir das fontes!'
              : 'Você ainda não possui exercícios cadastrados em nenhum bloco ativo.'}
          </p>
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleGenerateAi}
            disabled={generating}
          >
            Gerar Primeiro Exercício
          </button>
        </div>
      ) : (
        <div className="exercises-list-stack">
          {exercises.map((ex, index) => {
            const result = feedback[ex.id]
            const isSubmitting = submittingId === ex.id

            return (
              <div key={ex.id} className="card exercise-item-card">
                <div className="exercise-card-header">
                  <div className="flex-gap-center">
                    <span className="exercise-index-pill">#{index + 1}</span>
                    <h2 className="exercise-title">{ex.title}</h2>
                  </div>
                  <div className="flex-gap-center">
                    {ex.studyBlockTitle && (
                      <span className="badge badge-subtle">{ex.studyBlockTitle}</span>
                    )}
                    <span className={`difficulty-badge ${ex.difficulty?.toLowerCase() || 'intermediate'}`}>
                      {ex.difficulty || 'INTERMEDIATE'}
                    </span>
                  </div>
                </div>

                {/* Enunciado */}
                <div className="exercise-prompt-box">
                  <p className="whitespace-pre-wrap">{ex.prompt}</p>
                </div>

                {/* Orientações e Citações */}
                {ex.guidance && (
                  <div className="exercise-guidance-box">
                    <strong>Orientações:</strong> {ex.guidance}
                  </div>
                )}

                {ex.sourceExcerpt && (
                  <div className="source-excerpt-accordion">
                    <button
                      type="button"
                      className="btn-text-toggle"
                      onClick={() =>
                        setViewingExcerpt((prev) => ({ ...prev, [ex.id]: !prev[ex.id] }))
                      }
                    >
                      {viewingExcerpt[ex.id] ? '▲ Ocultar Trecho da Fonte Canônica' : '▼ Ver Trecho da Fonte Canônica'}
                    </button>
                    {viewingExcerpt[ex.id] && (
                      <blockquote className="excerpt-blockquote">
                        {ex.studyMaterialTitle && <strong>Fonte: {ex.studyMaterialTitle}</strong>}
                        <p>{ex.sourceExcerpt}</p>
                      </blockquote>
                    )}
                  </div>
                )}

                {/* Área de Resposta do Estudante */}
                <div className="exercise-answer-section">
                  <label htmlFor={`answer-${ex.id}`} className="text-xs text-muted block mb-1">
                    Sua Solução / Resposta de Código:
                  </label>
                  <textarea
                    id={`answer-${ex.id}`}
                    className="form-textarea font-mono text-sm"
                    rows={6}
                    placeholder="Escreva ou cole seu código ou raciocínio prático de resolução..."
                    value={answers[ex.id] ?? (ex.starterCode || '')}
                    onChange={(e) =>
                      setAnswers((prev) => ({ ...prev, [ex.id]: e.target.value }))
                    }
                  />
                </div>

                {/* Autoavaliação e Envio */}
                <div className="exercise-actions-bar">
                  <div className="self-eval-group">
                    <label htmlFor={`rating-${ex.id}`} className="text-xs text-muted">
                      Autoavaliação de Domínio Prático:
                    </label>
                    <select
                      id={`rating-${ex.id}`}
                      className="form-select form-select-sm"
                      value={ratings[ex.id] || 4}
                      onChange={(e) =>
                        setRatings((prev) => ({ ...prev, [ex.id]: Number(e.target.value) }))
                      }
                    >
                      <option value="5">★★★★★ Excelente (Total Domínio)</option>
                      <option value="4">★★★★☆ Bom (Conseguiu Resolver)</option>
                      <option value="3">★★★☆☆ Regular (Com Pequenas Dúvidas)</option>
                      <option value="2">★★☆☆☆ Difícil (Precisou de Ajuda)</option>
                      <option value="1">★☆☆☆☆ Não Conseguiu (Necessita Revisão)</option>
                    </select>
                  </div>

                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={() => void handleSubmitExercise(ex)}
                    disabled={isSubmitting}
                  >
                    {isSubmitting ? 'Registrando...' : 'Registrar Tentativa'}
                  </button>
                </div>

                {/* Feedback da Avaliação */}
                {result && (
                  <div className={`exercise-feedback-panel ${result.passed ? 'passed' : 'needs-review'}`}>
                    <div className="flex-between mb-2">
                      <div className="flex-gap-center">
                        <CheckCircleIcon className="w-5 h-5 text-emerald-400" />
                        <strong className="text-emerald-400">
                          Tentativa registrada no histórico
                        </strong>
                      </div>
                      <span className={`badge ${result.passed ? 'badge-emerald' : 'badge-amber'}`}>
                        Autoavaliação: {result.selfRating}/5
                      </span>
                    </div>
                    <p className="text-sm">
                      {result.passed
                        ? 'Bom domínio declarado. A próxima revisão deste exercício será espaçada.'
                        : 'Domínio ainda baixo. Este exercício voltará antes na sua fila de revisão.'}
                    </p>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
