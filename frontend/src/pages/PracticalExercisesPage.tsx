import { useCallback, useEffect, useState } from 'react'
import { request } from '../api/http'
import { submitExerciseAttempt } from '../api/exercises'
import { CodeIcon, FolderIcon, SparklesIcon } from '../components/icons'
import { AttemptResultPanel, type AttemptResult } from '../features/exercises/AttemptResultPanel'
import { ExerciseTutorChat } from '../features/exercises/ExerciseTutorChat'
import type { StudyBlock } from './StudyBlocksPage'
import '../styles/pages/exercises-questions.css'
import { useDeferredEffect } from '../hooks/useDeferredEffect'

const difficultyLabels: Record<string, string> = {
  EASY: 'Fácil',
  INTERMEDIATE: 'Intermediária',
  HARD: 'Difícil',
}

const exerciseTypeLabels: Record<string, string> = {
  PROBLEM_SOLVING: 'Resolução de problema',
  METHOD_IMPLEMENTATION: 'Implementar método',
  FEATURE_IMPLEMENTATION: 'Implementar funcionalidade',
  CODE_FIX: 'Corrigir código',
  CODE_ANALYSIS: 'Analisar código',
  SMALL_SOLUTION: 'Solução curta',
  SQL_QUERY: 'Consulta SQL',
  MULTI_TOPIC_INTEGRATION: 'Integrar vários tópicos',
}

type SourceOption = { id: string; title: string }


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
  steps?: string[]
  language?: string | null
  testCode?: string | null
  autoEvaluable?: boolean | null
  status: 'ACTIVE' | 'ARCHIVED'
}

/** Exercício em Java com testes: será corrigido automaticamente se o ambiente de execução estiver disponível. */
function hasAutomaticTests(ex: PracticalExercise) {
  return ex.language === 'JAVA' && !!ex.testCode && ex.autoEvaluable !== false
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
  const [feedback, setFeedback] = useState<Record<string, AttemptResult>>({})
  const [attemptCounts, setAttemptCounts] = useState<Record<string, number>>({})
  const [submittingId, setSubmittingId] = useState<string | null>(null)

  // Visualização de excerto / dicas
  const [viewingExcerpt, setViewingExcerpt] = useState<Record<string, boolean>>({})

  // Geração com IA: qualquer bloco ativo, opcionalmente focada num material ou tópico
  const [generating, setGenerating] = useState(false)
  const [genBlockId, setGenBlockId] = useState<string>('')
  const [genMaterialId, setGenMaterialId] = useState('')
  const [genTopicId, setGenTopicId] = useState('')
  const [genDifficulty, setGenDifficulty] = useState('INTERMEDIATE')
  // Vazio: o backend escolhe o formato menos praticado no bloco, para variar os exercícios.
  const [genType, setGenType] = useState('')
  const [genMaterials, setGenMaterials] = useState<SourceOption[]>([])
  const [genTopics, setGenTopics] = useState<SourceOption[]>([])

  const activeBlock = blocks.find((b) => b.id === selectedBlockId)
  const activeBlocks = blocks.filter((b) => b.status === 'ACTIVE')
  const targetBlockId = mode === 'block' ? selectedBlockId || '' : genBlockId || activeBlocks[0]?.id || ''

  // Seleções de outro bloco são ignoradas até o usuário escolher de novo.
  const effectiveMaterialId = genMaterials.some((m) => m.id === genMaterialId) ? genMaterialId : ''
  const effectiveTopicId = genTopics.some((t) => t.id === genTopicId) ? genTopicId : ''

  useEffect(() => {
    if (!targetBlockId) return
    let cancelled = false
    void Promise.all([
      request<SourceOption[]>(`/api/study-blocks/${targetBlockId}/materials?includeArchived=false`).catch(() => []),
      request<SourceOption[]>(`/api/study-blocks/${targetBlockId}/topics`).catch(() => []),
    ]).then(([materials, topics]) => {
      if (!cancelled) {
        setGenMaterials(materials)
        setGenTopics(topics)
      }
    })
    return () => {
      cancelled = true
    }
  }, [targetBlockId])

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

  useDeferredEffect(() => loadExercises(), [loadExercises])

  const handleGenerateAi = async () => {
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
            materialId: effectiveMaterialId || null,
            topicId: effectiveTopicId || null,
            difficulty: genDifficulty,
            exerciseType: genType || null,
          }),
        }
      )
      const blockTitle = blocks.find((b) => b.id === targetBlockId)?.title
      setExercises((prev) => [{ ...generated, studyBlockTitle: blockTitle }, ...prev])
    } catch {
      setError('Erro ao gerar exercício prático via IA. Verifique se o bloco possui fontes cadastradas.')
    } finally {
      setGenerating(false)
    }
  }

  const handleSubmitExercise = async (ex: PracticalExercise) => {
    const userAnswer = answers[ex.id] ?? (ex.starterCode || '')
    // Com correção automática confirmada, a nota vem da execução; senão, vale a autoavaliação.
    const selfRating = ex.autoEvaluable === true ? null : ratings[ex.id] || 4
    const blockId = ex.studyBlockId || selectedBlockId
    if (!blockId) {
      setError('Não foi possível identificar o bloco deste exercício.')
      return
    }

    setSubmittingId(ex.id)
    try {
      // O backend executa o código (quando há testes) e aplica a autonomia, calculada pelas ajudas da tutora.
      const res = await submitExerciseAttempt<AttemptResult & { id: string }>(
        blockId,
        ex.id,
        { answer: userAnswer, selfRating },
      )
      setFeedback((prev) => ({ ...prev, [ex.id]: res }))
      setAttemptCounts((prev) => ({ ...prev, [ex.id]: (prev[ex.id] ?? 0) + 1 }))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao enviar avaliação do exercício.')
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
            disabled={generating || !targetBlockId}
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

      {/* Conteúdo do exercício: qualquer bloco cadastrado, com foco opcional em material ou tópico */}
      <div className="card exercise-generator-card mb-6">
        <div className="exercise-generator-grid">
          {mode === 'global' && (
            <label className="exercise-generator-field">
              <span className="text-xs text-muted">Bloco de conteúdo</span>
              <select className="form-select" value={targetBlockId} onChange={(e) => setGenBlockId(e.target.value)}>
                {activeBlocks.length === 0 && <option value="">Nenhum bloco ativo</option>}
                {activeBlocks.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.title}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label className="exercise-generator-field">
            <span className="text-xs text-muted">Tópico</span>
            <select className="form-select" value={effectiveTopicId} onChange={(e) => setGenTopicId(e.target.value)} disabled={!targetBlockId}>
              <option value="">Qualquer tópico do bloco</option>
              {genTopics.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.title}
                </option>
              ))}
            </select>
          </label>
          <label className="exercise-generator-field">
            <span className="text-xs text-muted">Material</span>
            <select className="form-select" value={effectiveMaterialId} onChange={(e) => setGenMaterialId(e.target.value)} disabled={!targetBlockId}>
              <option value="">Materiais mais recentes do bloco</option>
              {genMaterials.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.title}
                </option>
              ))}
            </select>
          </label>
          <label className="exercise-generator-field">
            <span className="text-xs text-muted">Dificuldade</span>
            <select className="form-select" value={genDifficulty} onChange={(e) => setGenDifficulty(e.target.value)}>
              {Object.entries(difficultyLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="exercise-generator-field">
            <span className="text-xs text-muted">Tipo</span>
            <select className="form-select" value={genType} onChange={(e) => setGenType(e.target.value)}>
              <option value="">Variar automaticamente</option>
              {Object.entries(exerciseTypeLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

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
            const exerciseBlockId = ex.studyBlockId || selectedBlockId
            const steps = ex.steps ?? []

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
                    {hasAutomaticTests(ex) && <span className="badge badge-info">Correção automática</span>}
                    <span className={`difficulty-badge ${ex.difficulty?.toLowerCase() || 'intermediate'}`}>
                      {difficultyLabels[ex.difficulty || 'INTERMEDIATE'] ?? ex.difficulty}
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

                {steps.length > 0 && (
                  <ol className="exercise-steps-list">
                    {steps.map((step, i) => (
                      <li key={i}>
                        <span className="exercise-step-num">{i + 1}</span>
                        <span>{step}</span>
                      </li>
                    ))}
                  </ol>
                )}

                {hasAutomaticTests(ex) && (
                  <details className="attempt-details">
                    <summary>Ver os testes que seu código precisa passar</summary>
                    <pre className="exercise-test-code">{ex.testCode}</pre>
                  </details>
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

                {exerciseBlockId && (
                  <ExerciseTutorChat
                    exercisePath={`/api/study-blocks/${exerciseBlockId}/exercises/${ex.id}`}
                    steps={steps}
                    currentCode={answers[ex.id] ?? (ex.starterCode || '')}
                    refreshKey={attemptCounts[ex.id] ?? 0}
                  />
                )}

                {/* Autoavaliação e Envio */}
                <div className="exercise-actions-bar">
                  {ex.autoEvaluable === true ? (
                    <span className="text-xs text-muted">
                      Seu código será executado com os testes e analisado (conceito e boas práticas).
                    </span>
                  ) : (
                  <div className="self-eval-group">
                    <label htmlFor={`rating-${ex.id}`} className="text-xs text-muted">
                      {hasAutomaticTests(ex)
                        ? 'Autoavaliação (usada só se a correção automática estiver indisponível):'
                        : 'Autoavaliação (este exercício não tem correção automática):'}
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
                  )}

                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={() => void handleSubmitExercise(ex)}
                    disabled={isSubmitting}
                  >
                    {isSubmitting ? (ex.autoEvaluable === true ? 'Executando...' : 'Registrando...') : 'Enviar Solução'}
                  </button>
                </div>

                {/* Resultado da Avaliação */}
                {result && <AttemptResultPanel result={result} />}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
