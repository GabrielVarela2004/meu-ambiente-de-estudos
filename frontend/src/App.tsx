import { useCallback, useEffect, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import './App.css'
import { request } from './api/http'
import { TopicsPanel } from './features/knowledge/TopicsPanel'
import { TutorChatPanel } from './features/chat/TutorChatPanel'

type StudyBlock = {
  id: string
  title: string
  description: string | null
  learningObjective: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  updatedAt: string
}

type BlockForm = {
  title: string
  description: string
  learningObjective: string
}

type StudyMaterial = {
  id: string
  studyBlockId: string
  title: string
  content: string
  status: 'ACTIVE' | 'ARCHIVED'
  updatedAt: string
}

type MaterialForm = {
  title: string
  content: string
}

type QuestionDifficulty = 'EASY' | 'INTERMEDIATE' | 'HARD'
type QuestionFormat = 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'DISCURSIVE'

type StudyQuestion = {
  id: string
  prompt: string
  explanation: string | null
  difficulty: QuestionDifficulty
  format: QuestionFormat
  expectedAnswer: string | null
  options: { id: string; text: string; order: number }[]
  status: 'ACTIVE' | 'ARCHIVED'
  nextReviewAt: string
  reviewIntervalDays: number
  correctStreak: number
}

type DueReviewQuestion = {
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
}

type QuestionStats = {
  attempts: number
  correct: number
  accuracyPercent: number
}

type QuestionForm = {
  prompt: string
  explanation: string
  difficulty: QuestionDifficulty
  format: QuestionFormat
  expectedAnswer: string
  options: { text: string; correct: boolean }[]
}

const difficultyLabels: Record<QuestionDifficulty, string> = {
  EASY: 'Fácil',
  INTERMEDIATE: 'Intermediária',
  HARD: 'Difícil',
}

const formatLabels: Record<QuestionFormat, string> = {
  MULTIPLE_CHOICE: 'Múltipla escolha',
  TRUE_FALSE: 'Verdadeiro ou falso',
  DISCURSIVE: 'Discursiva',
}

type StudySession = {
  id: string
  plannedMinutes: number
  notes: string | null
  status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  startedAt: string | null
  endedAt: string | null
}

type PracticalExercise = {
  id: string
  title: string
  prompt: string
  guidance: string | null
  status: 'ACTIVE' | 'ARCHIVED'
}

type StudyGoal = {
  id: string
  title: string
  description: string | null
  targetUnits: number
  completedUnits: number
  status: 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'
}

const emptyForm: BlockForm = {
  title: '',
  description: '',
  learningObjective: '',
}

const emptyMaterialForm: MaterialForm = {
  title: '',
  content: '',
}

const emptyQuestionForm: QuestionForm = {
  prompt: '',
  explanation: '',
  difficulty: 'INTERMEDIATE',
  format: 'MULTIPLE_CHOICE',
  expectedAnswer: '',
  options: [
    { text: '', correct: true },
    { text: '', correct: false },
  ],
}

function App() {
  const [blocks, setBlocks] = useState<StudyBlock[]>([])
  const [form, setForm] = useState<BlockForm>(emptyForm)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [includeArchived, setIncludeArchived] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null)
  const [materials, setMaterials] = useState<StudyMaterial[]>([])
  const [materialForm, setMaterialForm] = useState<MaterialForm>(emptyMaterialForm)
  const [editingMaterialId, setEditingMaterialId] = useState<string | null>(null)
  const [includeArchivedMaterials, setIncludeArchivedMaterials] = useState(false)
  const [materialsLoading, setMaterialsLoading] = useState(false)
  const [materialSaving, setMaterialSaving] = useState(false)
  const [fileImporting, setFileImporting] = useState(false)
  const [questions, setQuestions] = useState<StudyQuestion[]>([])
  const [questionForm, setQuestionForm] = useState<QuestionForm>(emptyQuestionForm)
  const [questionSaving, setQuestionSaving] = useState(false)
  const [questionGenerating, setQuestionGenerating] = useState(false)
  const [questionsLoading, setQuestionsLoading] = useState(false)
  const [questionResult, setQuestionResult] = useState<Record<string, { correct: boolean; explanation: string | null }>>({})
  const [discursiveInputs, setDiscursiveInputs] = useState<Record<string, string>>({})
  const [questionStats, setQuestionStats] = useState<QuestionStats>({ attempts: 0, correct: 0, accuracyPercent: 0 })
  const [periodDays, setPeriodDays] = useState(30)
  const [periodStats, setPeriodStats] = useState<QuestionStats>({ attempts: 0, correct: 0, accuracyPercent: 0 })
  const [dueQuestions, setDueQuestions] = useState<StudyQuestion[]>([])
  const [globalDueReviews, setGlobalDueReviews] = useState<DueReviewQuestion[]>([])
  const [evidenceVersion, setEvidenceVersion] = useState(0)
  const [sessions, setSessions] = useState<StudySession[]>([])
  const [sessionMinutes, setSessionMinutes] = useState(25)
  const [sessionNotes, setSessionNotes] = useState('')
  const [sessionsLoading, setSessionsLoading] = useState(false)
  const [sessionSaving, setSessionSaving] = useState(false)
  const [exercises, setExercises] = useState<PracticalExercise[]>([])
  const [exerciseTitle, setExerciseTitle] = useState('')
  const [exercisePrompt, setExercisePrompt] = useState('')
  const [exerciseGuidance, setExerciseGuidance] = useState('')
  const [exerciseAnswers, setExerciseAnswers] = useState<Record<string, string>>({})
  const [exerciseRatings, setExerciseRatings] = useState<Record<string, number>>({})
  const [exerciseFeedback, setExerciseFeedback] = useState<Record<string, string>>({})
  const [exerciseSaving, setExerciseSaving] = useState(false)
  const [goals, setGoals] = useState<StudyGoal[]>([])
  const [goalTitle, setGoalTitle] = useState('')
  const [goalDescription, setGoalDescription] = useState('')
  const [goalTarget, setGoalTarget] = useState(1)
  const [goalSaving, setGoalSaving] = useState(false)

  const loadGlobalReviews = useCallback(async () => {
    try {
      const data = await request<DueReviewQuestion[]>('/api/reviews/due')
      setGlobalDueReviews(data)
    } catch {
      // Silencioso se o backend ainda estiver carregando
    }
  }, [])

  const loadBlocks = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await request<StudyBlock[]>(
        `/api/study-blocks?includeArchived=${includeArchived}`,
      )
      setBlocks(data)
      await loadGlobalReviews()
    } catch {
      setError('Não foi possível carregar os blocos. Verifique se o backend está em execução.')
    } finally {
      setLoading(false)
    }
  }, [includeArchived, loadGlobalReviews])

  useEffect(() => {
    void loadBlocks()
  }, [loadBlocks])

  const loadMaterials = useCallback(async () => {
    if (!selectedBlockId) return
    setMaterialsLoading(true)
    setError(null)
    try {
      const data = await request<StudyMaterial[]>(
        `/api/study-blocks/${selectedBlockId}/materials?includeArchived=${includeArchivedMaterials}`,
      )
      setMaterials(data)
    } catch {
      setError('Não foi possível carregar os materiais deste bloco.')
    } finally {
      setMaterialsLoading(false)
    }
  }, [selectedBlockId, includeArchivedMaterials])

  useEffect(() => {
    void loadMaterials()
  }, [loadMaterials])

  const loadQuestions = useCallback(async () => {
    if (!selectedBlockId) return
    setQuestionsLoading(true)
    try {
      const questionData = await request<StudyQuestion[]>(`/api/study-blocks/${selectedBlockId}/questions`)
      setQuestions(questionData)
      setDueQuestions(await request<StudyQuestion[]>(`/api/study-blocks/${selectedBlockId}/questions/review`))
      setQuestionStats(await request<QuestionStats>(`/api/study-blocks/${selectedBlockId}/questions/stats`))
      setPeriodStats(await request<QuestionStats>(
        `/api/study-blocks/${selectedBlockId}/questions/stats?days=${periodDays}`,
      ))
      await loadGlobalReviews()
    } catch {
      setError('Não foi possível carregar as questões deste bloco.')
    } finally {
      setQuestionsLoading(false)
    }
  }, [selectedBlockId, periodDays, loadGlobalReviews])

  useEffect(() => {
    void loadQuestions()
  }, [loadQuestions])

  const loadSessions = useCallback(async () => {
    if (!selectedBlockId) return
    setSessionsLoading(true)
    try {
      setSessions(await request<StudySession[]>(`/api/study-blocks/${selectedBlockId}/sessions`))
    } catch {
      setError('Não foi possível carregar as sessões deste bloco.')
    } finally {
      setSessionsLoading(false)
    }
  }, [selectedBlockId])

  useEffect(() => {
    void loadSessions()
  }, [loadSessions])

  const loadExercises = useCallback(async () => {
    if (!selectedBlockId) return
    try {
      setExercises(await request<PracticalExercise[]>(`/api/study-blocks/${selectedBlockId}/exercises`))
    } catch {
      setError('Não foi possível carregar os exercícios deste bloco.')
    }
  }, [selectedBlockId])

  useEffect(() => {
    void loadExercises()
  }, [loadExercises])

  const loadGoals = useCallback(async () => {
    if (!selectedBlockId) return
    try {
      setGoals(await request<StudyGoal[]>(`/api/study-blocks/${selectedBlockId}/goals`))
    } catch {
      setError('Não foi possível carregar as metas deste bloco.')
    }
  }, [selectedBlockId])

  useEffect(() => {
    void loadGoals()
  }, [loadGoals])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const url = editingId ? `/api/study-blocks/${editingId}` : '/api/study-blocks'
      const method = editingId ? 'PUT' : 'POST'
      await request<StudyBlock>(url, {
        method,
        body: JSON.stringify(form),
      })
      setForm(emptyForm)
      setEditingId(null)
      await loadBlocks()
    } catch {
      setError('Não foi possível salvar o bloco. Confira o título e tente novamente.')
    } finally {
      setSaving(false)
    }
  }

  const edit = (block: StudyBlock) => {
    setEditingId(block.id)
    setForm({
      title: block.title,
      description: block.description ?? '',
      learningObjective: block.learningObjective ?? '',
    })
  }

  const archive = async (id: string) => {
    if (!window.confirm('Arquivar este bloco? Ele continuará disponível no histórico.')) return
    try {
      await request<void>(`/api/study-blocks/${id}`, { method: 'DELETE' })
      await loadBlocks()
    } catch {
      setError('Não foi possível arquivar o bloco.')
    }
  }

  const submitMaterial = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedBlockId) return
    setMaterialSaving(true)
    setError(null)
    try {
      const url = editingMaterialId
        ? `/api/study-blocks/${selectedBlockId}/materials/${editingMaterialId}`
        : `/api/study-blocks/${selectedBlockId}/materials`
      await request<StudyMaterial>(url, {
        method: editingMaterialId ? 'PUT' : 'POST',
        body: JSON.stringify(materialForm),
      })
      setMaterialForm(emptyMaterialForm)
      setEditingMaterialId(null)
      await loadMaterials()
    } catch {
      setError('Não foi possível salvar o material. Título e conteúdo são obrigatórios.')
    } finally {
      setMaterialSaving(false)
    }
  }

  const importMaterialFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!selectedBlockId || !file) return
    setFileImporting(true)
    setError(null)
    try {
      const body = new FormData()
      body.append('file', file)
      const response = await fetch(`/api/study-blocks/${selectedBlockId}/materials/import`, {
        method: 'POST',
        body,
      })
      if (!response.ok) throw new Error(await response.text())
      await response.json() as StudyMaterial
      await loadMaterials()
    } catch {
      setError('Não foi possível importar o arquivo. Use .txt/.md (até 1 MB) ou .pdf/.docx (até 5 MB) com texto extraível.')
    } finally {
      setFileImporting(false)
      event.target.value = ''
    }
  }

  const selectBlock = (id: string) => {
    setSelectedBlockId(id)
    setMaterialForm(emptyMaterialForm)
    setEditingMaterialId(null)
    setQuestionResult({})
  }

  const changeQuestionFormat = (format: QuestionFormat) => {
    if (format === 'TRUE_FALSE') {
      setQuestionForm({
        ...questionForm,
        format,
        options: [
          { text: 'Verdadeiro', correct: true },
          { text: 'Falso', correct: false },
        ],
      })
      return
    }
    if (format === 'DISCURSIVE') {
      setQuestionForm({
        ...questionForm,
        format,
        options: [],
      })
      return
    }
    setQuestionForm({
      ...questionForm,
      format,
      options: questionForm.options.length >= 2
        ? questionForm.options
        : [
            { text: '', correct: true },
            { text: '', correct: false },
          ],
    })
  }

  const submitQuestion = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedBlockId) return
    setQuestionSaving(true)
    setError(null)
    try {
      const payload = {
        prompt: questionForm.prompt,
        explanation: questionForm.explanation,
        difficulty: questionForm.difficulty,
        format: questionForm.format,
        expectedAnswer: questionForm.format === 'DISCURSIVE' ? questionForm.expectedAnswer : null,
        options: questionForm.format === 'DISCURSIVE' ? [] : questionForm.options,
      }
      await request<StudyQuestion>(`/api/study-blocks/${selectedBlockId}/questions`, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      setQuestionForm(emptyQuestionForm)
      await loadQuestions()
    } catch {
      setError('Não foi possível salvar a questão. Verifique o formato selecionado, opções ou gabarito esperado.')
    } finally {
      setQuestionSaving(false)
    }
  }

  const generateQuestionWithAi = async () => {
    if (!selectedBlockId) return
    setQuestionGenerating(true)
    setError(null)
    try {
      await request<StudyQuestion>(`/api/study-blocks/${selectedBlockId}/questions/generate`, {
        method: 'POST',
        body: JSON.stringify({
          difficulty: questionForm.difficulty,
          format: questionForm.format,
        }),
      })
      await loadQuestions()
    } catch {
      setError(
        'Não foi possível gerar questão com IA. Verifique se há materiais ativos neste bloco e se OPENAI_API_KEY está configurada.',
      )
    } finally {
      setQuestionGenerating(false)
    }
  }

  const answerQuestion = async (questionId: string, optionId: string, blockIdOverride?: string) => {
    const targetBlockId = blockIdOverride ?? selectedBlockId
    if (!targetBlockId) return
    try {
      const result = await request<{ correct: boolean; explanation: string | null }>(
        `/api/study-blocks/${targetBlockId}/questions/${questionId}/answers`,
        { method: 'POST', body: JSON.stringify({ optionId }) },
      )
      setQuestionResult({ ...questionResult, [questionId]: result })
      setEvidenceVersion((current) => current + 1)
      if (selectedBlockId === targetBlockId) {
        await loadQuestions()
      } else {
        await loadGlobalReviews()
      }
    } catch {
      setError('Não foi possível registrar a resposta.')
    }
  }

  const answerDiscursiveQuestion = async (questionId: string, blockIdOverride?: string) => {
    const targetBlockId = blockIdOverride ?? selectedBlockId
    const discursiveAnswer = discursiveInputs[questionId]?.trim()
    if (!targetBlockId || !discursiveAnswer) return
    try {
      const result = await request<{ correct: boolean; explanation: string | null }>(
        `/api/study-blocks/${targetBlockId}/questions/${questionId}/answers`,
        { method: 'POST', body: JSON.stringify({ discursiveAnswer }) },
      )
      setQuestionResult({ ...questionResult, [questionId]: result })
      setEvidenceVersion((current) => current + 1)
      if (selectedBlockId === targetBlockId) {
        await loadQuestions()
      } else {
        await loadGlobalReviews()
      }
    } catch {
      setError('Não foi possível registrar a resposta discursiva.')
    }
  }

  const rescheduleQuestionReview = async (questionId: string, days: number) => {
    try {
      await request(`/api/reviews/questions/${questionId}/reschedule?days=${days}`, {
        method: 'POST',
      })
      await loadGlobalReviews()
      if (selectedBlockId) {
        await loadQuestions()
      }
    } catch {
      setError('Não foi possível reprogramar a revisão.')
    }
  }

  const createSession = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedBlockId) return
    setSessionSaving(true)
    setError(null)
    try {
      await request<StudySession>(`/api/study-blocks/${selectedBlockId}/sessions`, {
        method: 'POST',
        body: JSON.stringify({ plannedMinutes: sessionMinutes, notes: sessionNotes }),
      })
      setSessionNotes('')
      await loadSessions()
    } catch {
      setError('Não foi possível criar a sessão. Use entre 5 e 720 minutos.')
    } finally {
      setSessionSaving(false)
    }
  }

  const updateSession = async (session: StudySession, action: 'start' | 'complete' | 'cancel') => {
    if (!selectedBlockId) return
    try {
      await request<StudySession>(
        `/api/study-blocks/${selectedBlockId}/sessions/${session.id}/${action}`,
        { method: 'POST' },
      )
      await loadSessions()
    } catch {
      setError('Não foi possível atualizar o estado da sessão.')
    }
  }

  const createExercise = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedBlockId) return
    setExerciseSaving(true)
    try {
      await request<PracticalExercise>(`/api/study-blocks/${selectedBlockId}/exercises`, {
        method: 'POST',
        body: JSON.stringify({ title: exerciseTitle, prompt: exercisePrompt, guidance: exerciseGuidance }),
      })
      setExerciseTitle('')
      setExercisePrompt('')
      setExerciseGuidance('')
      await loadExercises()
    } catch {
      setError('Não foi possível criar o exercício. Preencha título e enunciado.')
    } finally {
      setExerciseSaving(false)
    }
  }

  const submitExercise = async (exerciseId: string) => {
    if (!selectedBlockId || !exerciseAnswers[exerciseId]?.trim()) return
    try {
      await request(`/api/study-blocks/${selectedBlockId}/exercises/${exerciseId}/attempts`, {
        method: 'POST',
        body: JSON.stringify({
          answer: exerciseAnswers[exerciseId],
          selfRating: exerciseRatings[exerciseId] ?? 3,
          feedback: exerciseFeedback[exerciseId] ?? '',
        }),
      })
      setExerciseAnswers({ ...exerciseAnswers, [exerciseId]: '' })
      setExerciseFeedback({ ...exerciseFeedback, [exerciseId]: '' })
      setError(null)
    } catch {
      setError('Não foi possível salvar a tentativa do exercício.')
    }
  }

  const archiveExercise = async (exerciseId: string) => {
    if (!selectedBlockId || !window.confirm('Arquivar este exercício?')) return
    try {
      await request<void>(`/api/study-blocks/${selectedBlockId}/exercises/${exerciseId}`, { method: 'DELETE' })
      await loadExercises()
    } catch {
      setError('Não foi possível arquivar o exercício.')
    }
  }

  const createGoal = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedBlockId) return
    setGoalSaving(true)
    try {
      await request<StudyGoal>(`/api/study-blocks/${selectedBlockId}/goals`, {
        method: 'POST',
        body: JSON.stringify({ title: goalTitle, description: goalDescription, targetUnits: goalTarget }),
      })
      setGoalTitle('')
      setGoalDescription('')
      setGoalTarget(1)
      await loadGoals()
    } catch {
      setError('Não foi possível criar a meta. Informe um título e um alvo positivo.')
    } finally {
      setGoalSaving(false)
    }
  }

  const addGoalProgress = async (goal: StudyGoal) => {
    if (!selectedBlockId || goal.status !== 'ACTIVE') return
    try {
      await request<StudyGoal>(`/api/study-blocks/${selectedBlockId}/goals/${goal.id}/progress`, {
        method: 'POST',
        body: JSON.stringify({ units: 1 }),
      })
      await loadGoals()
    } catch {
      setError('Não foi possível atualizar o progresso da meta.')
    }
  }

  const archiveGoal = async (goalId: string) => {
    if (!selectedBlockId || !window.confirm('Arquivar esta meta?')) return
    try {
      await request<void>(`/api/study-blocks/${selectedBlockId}/goals/${goalId}`, { method: 'DELETE' })
      await loadGoals()
    } catch {
      setError('Não foi possível arquivar a meta.')
    }
  }

  const archiveQuestion = async (questionId: string) => {
    if (!selectedBlockId || !window.confirm('Arquivar esta questão?')) return
    try {
      await request<void>(`/api/study-blocks/${selectedBlockId}/questions/${questionId}`, { method: 'DELETE' })
      await loadQuestions()
    } catch {
      setError('Não foi possível arquivar a questão.')
    }
  }

  const archiveMaterial = async (id: string) => {
    if (!selectedBlockId || !window.confirm('Arquivar este material?')) return
    try {
      await request<void>(`/api/study-blocks/${selectedBlockId}/materials/${id}`, { method: 'DELETE' })
      await loadMaterials()
    } catch {
      setError('Não foi possível arquivar o material.')
    }
  }

  const selectedBlockTitle = blocks.find((block) => block.id === selectedBlockId)?.title ?? null

  return (
    <main className="app-shell">
      <header className="page-header">
        <div>
          <p className="eyebrow">Plataforma de estudos</p>
          <h1>Blocos de estudo</h1>
          <p className="subtitle">Organize seus assuntos e objetivos em espaços independentes.</p>
        </div>
        <span className="phase">Dados persistidos</span>
      </header>

      <section className="card global-reviews-section">
        <div className="section-heading">
          <div>
            <h2>Revisar minhas dificuldades</h2>
            <p>
              {globalDueReviews.length === 0
                ? 'Nenhuma questão pendente de revisão neste momento.'
                : `${globalDueReviews.length} questão(ões) pendente(s) em todos os blocos ativos.`}
            </p>
          </div>
        </div>
        {globalDueReviews.length > 0 && (
          <div className="question-list">
            {globalDueReviews.map((review) => {
              const result = questionResult[review.id]
              return (
                <article className="card question-card" key={review.id}>
                  <div className="block-card-header">
                    <h3>{review.prompt}</h3>
                    <span className="badge">
                      {review.studyBlockTitle} · {difficultyLabels[review.difficulty]} · {formatLabels[review.format]}
                    </span>
                  </div>
                  <p className="review-detail">
                    <strong>Motivo da recomendação:</strong> {review.recommendationReason}
                  </p>
                  {review.format === 'DISCURSIVE' ? (
                    <div className="discursive-answer-box">
                      <textarea
                        maxLength={10000}
                        value={discursiveInputs[review.id] ?? ''}
                        onChange={(event) =>
                          setDiscursiveInputs({ ...discursiveInputs, [review.id]: event.target.value })
                        }
                        placeholder="Escreva sua resposta discursiva..."
                      />
                      <button
                        type="button"
                        onClick={() => void answerDiscursiveQuestion(review.id, review.studyBlockId)}
                        disabled={!discursiveInputs[review.id]?.trim()}
                      >
                        Avaliar resposta discursiva
                      </button>
                    </div>
                  ) : (
                    <div className="answer-options">
                      {review.options.map((option) => (
                        <button
                          className="secondary answer-option"
                          key={option.id}
                          onClick={() => void answerQuestion(review.id, option.id, review.studyBlockId)}
                        >
                          {option.text}
                        </button>
                      ))}
                    </div>
                  )}
                  {result && (
                    <p className={result.correct ? 'answer-correct' : 'answer-wrong'}>
                      {result.correct ? 'Resposta correta.' : 'Resposta incorreta.'} {result.explanation}
                    </p>
                  )}
                  <div className="card-actions">
                    <button className="secondary" onClick={() => void rescheduleQuestionReview(review.id, 1)}>
                      Adiar 1 dia
                    </button>
                    <button className="secondary" onClick={() => void rescheduleQuestionReview(review.id, 3)}>
                      Adiar 3 dias
                    </button>
                    <button onClick={() => selectBlock(review.studyBlockId)}>
                      Abrir bloco
                    </button>
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </section>

      <section className="workspace">
        <form className="card block-form" onSubmit={submit}>
          <h2>{editingId ? 'Editar bloco' : 'Novo bloco'}</h2>
          <label>
            Título
            <input
              required
              maxLength={160}
              value={form.title}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
              placeholder="Ex.: Java"
            />
          </label>
          <label>
            Descrição
            <textarea
              maxLength={2000}
              value={form.description}
              onChange={(event) => setForm({ ...form, description: event.target.value })}
              placeholder="O que este bloco reúne?"
            />
          </label>
          <label>
            Objetivo de aprendizagem
            <textarea
              maxLength={2000}
              value={form.learningObjective}
              onChange={(event) => setForm({ ...form, learningObjective: event.target.value })}
              placeholder="O que você quer conseguir fazer?"
            />
          </label>
          <div className="form-actions">
            <button type="submit" disabled={saving}>
              {saving ? 'Salvando...' : editingId ? 'Salvar alterações' : 'Criar bloco'}
            </button>
            {editingId && (
              <button type="button" className="secondary" onClick={() => { setEditingId(null); setForm(emptyForm) }}>
                Cancelar
              </button>
            )}
          </div>
        </form>

        <section className="blocks-section">
          <div className="section-heading">
            <div>
              <h2>{includeArchived ? 'Todos os blocos' : 'Blocos ativos'}</h2>
              <p>{blocks.length} bloco(s) encontrado(s)</p>
            </div>
            <label className="toggle">
              <input type="checkbox" checked={includeArchived} onChange={(event) => setIncludeArchived(event.target.checked)} />
              Incluir arquivados
            </label>
          </div>
          {error && <p className="error" role="alert">{error}</p>}
          {loading ? <p className="empty">Carregando blocos...</p> : blocks.length === 0 ? (
            <p className="empty">Nenhum bloco cadastrado ainda.</p>
          ) : (
            <div className="block-list">
              {blocks.map((block) => (
                <article className={`card block-card ${block.status === 'ARCHIVED' ? 'archived' : ''}`} key={block.id}>
                  <div className="block-card-header">
                    <h3>{block.title}</h3>
                    <span className="badge">{block.status === 'ACTIVE' ? 'Ativo' : 'Arquivado'}</span>
                  </div>
                  {block.description && <p>{block.description}</p>}
                  {block.learningObjective && <p className="objective"><strong>Objetivo:</strong> {block.learningObjective}</p>}
                  <div className="card-actions">
                    {block.status === 'ACTIVE' && <button className="secondary" onClick={() => edit(block)}>Editar</button>}
                    {block.status === 'ACTIVE' && <button onClick={() => selectBlock(block.id)}>Materiais</button>}
                    {block.status === 'ACTIVE' && <button className="danger" onClick={() => void archive(block.id)}>Arquivar</button>}
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </section>
      {selectedBlockId && (
        <section className="materials-panel">
          <div className="section-heading">
            <div>
              <h2>Materiais de estudo</h2>
              <p>{materials.length} material(is) encontrado(s)</p>
            </div>
            <button className="secondary" onClick={() => setSelectedBlockId(null)}>Fechar</button>
          </div>
          <form className="card material-form" onSubmit={submitMaterial}>
            <h2>{editingMaterialId ? 'Editar material' : 'Novo material'}</h2>
            <label>
              Título
              <input required maxLength={160} value={materialForm.title}
                onChange={(event) => setMaterialForm({ ...materialForm, title: event.target.value })}
                placeholder="Ex.: Tipos de dados" />
            </label>
            <label>
              Conteúdo
              <textarea required maxLength={100000} value={materialForm.content}
                onChange={(event) => setMaterialForm({ ...materialForm, content: event.target.value })}
                placeholder="Escreva o conteúdo do material..." />
            </label>
            <div className="form-actions">
              <button type="submit" disabled={materialSaving}>{materialSaving ? 'Salvando...' : editingMaterialId ? 'Salvar alterações' : 'Adicionar material'}</button>
              {editingMaterialId && <button type="button" className="secondary" onClick={() => { setEditingMaterialId(null); setMaterialForm(emptyMaterialForm) }}>Cancelar</button>}
            </div>
          </form>
          <label className="file-import">
            Importar documento (.txt, .md, .pdf ou .docx)
            <input
              type="file"
              accept=".txt,.md,.pdf,.docx,text/plain,text/markdown,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              disabled={fileImporting}
              onChange={(event) => void importMaterialFile(event)}
            />
          </label>
          <label className="toggle material-toggle">
            <input type="checkbox" checked={includeArchivedMaterials} onChange={(event) => setIncludeArchivedMaterials(event.target.checked)} />
            Incluir materiais arquivados
          </label>
          {materialsLoading ? <p className="empty">Carregando materiais...</p> : materials.length === 0 ? (
            <p className="empty">Nenhum material cadastrado neste bloco.</p>
          ) : (
            <div className="material-list">
              {materials.map((material) => (
                <article className={`card material-card ${material.status === 'ARCHIVED' ? 'archived' : ''}`} key={material.id}>
                  <div className="block-card-header"><h3>{material.title}</h3><span className="badge">{material.status === 'ACTIVE' ? 'Ativo' : 'Arquivado'}</span></div>
                  <p className="material-content">{material.content}</p>
                  {material.status === 'ACTIVE' && <div className="card-actions">
                    <button className="secondary" onClick={() => { setEditingMaterialId(material.id); setMaterialForm({ title: material.title, content: material.content }) }}>Editar</button>
                    <button className="danger" onClick={() => void archiveMaterial(material.id)}>Arquivar</button>
                  </div>}
                </article>
              ))}
            </div>
          )}
          <div className="questions-section">
            <h2>Questões</h2>
            <p className="stats-summary">
              Desempenho acumulado: {questionStats.attempts === 0 ? (
                <span>Dados insuficientes para avaliar (0 tentativas).</span>
              ) : (
                <>
                  <strong>{questionStats.correct}</strong> acerto(s) em <strong>{questionStats.attempts}</strong> tentativa(s)
                  {' '}({questionStats.accuracyPercent}%)
                </>
              )}
            </p>
            <p className="stats-summary">
              Últimos <select value={periodDays} onChange={(event) => setPeriodDays(Number(event.target.value))}>
                {[7, 30, 90].map((days) => <option value={days} key={days}>{days} dias</option>)}
              </select>:{' '}
              {periodStats.attempts === 0 ? (
                <span>Sem tentativas registradas neste período.</span>
              ) : (
                <>
                  <strong>{periodStats.correct}</strong> acerto(s) em <strong>{periodStats.attempts}</strong> tentativa(s)
                  {' '}({periodStats.accuracyPercent}%)
                </>
              )}
            </p>
            <p className="review-summary">
              Revisão espaçada no bloco: <strong>{dueQuestions.length}</strong> questão(ões) pendente(s) agora.
            </p>
            <form className="card question-form" onSubmit={submitQuestion}>
              <div className="question-meta-fields">
                <label>
                  Formato
                  <select
                    value={questionForm.format}
                    onChange={(event) => changeQuestionFormat(event.target.value as QuestionFormat)}
                  >
                    <option value="MULTIPLE_CHOICE">Múltipla escolha</option>
                    <option value="TRUE_FALSE">Verdadeiro ou falso</option>
                    <option value="DISCURSIVE">Discursiva</option>
                  </select>
                </label>
                <label>
                  Dificuldade
                  <select
                    value={questionForm.difficulty}
                    onChange={(event) =>
                      setQuestionForm({ ...questionForm, difficulty: event.target.value as QuestionDifficulty })
                    }
                  >
                    <option value="EASY">Fácil</option>
                    <option value="INTERMEDIATE">Intermediária</option>
                    <option value="HARD">Difícil</option>
                  </select>
                </label>
              </div>
              <label>
                Pergunta
                <textarea required maxLength={2000} value={questionForm.prompt}
                  onChange={(event) => setQuestionForm({ ...questionForm, prompt: event.target.value })}
                  placeholder="Ex.: Qual conceito define uma lista?" />
              </label>
              <label>
                Explicação da resposta
                <textarea maxLength={2000} value={questionForm.explanation}
                  onChange={(event) => setQuestionForm({ ...questionForm, explanation: event.target.value })}
                  placeholder="Explique por que a alternativa ou gabarito é a resposta." />
              </label>
              {questionForm.format === 'DISCURSIVE' ? (
                <label>
                  Gabarito / Critérios-chave esperados
                  <textarea
                    required
                    maxLength={4000}
                    value={questionForm.expectedAnswer}
                    onChange={(event) => setQuestionForm({ ...questionForm, expectedAnswer: event.target.value })}
                    placeholder="Informe os conceitos-chave exigidos para avaliar a resposta discursiva..."
                  />
                </label>
              ) : (
                questionForm.options.map((option, index) => (
                  <div className="option-editor" key={index}>
                    <input
                      required
                      maxLength={500}
                      value={option.text}
                      readOnly={questionForm.format === 'TRUE_FALSE'}
                      placeholder={`Alternativa ${index + 1}`}
                      onChange={(event) => {
                        const options = [...questionForm.options]
                        options[index] = { ...option, text: event.target.value }
                        setQuestionForm({ ...questionForm, options })
                      }}
                    />
                    <label className="correct-option">
                      <input
                        type="radio"
                        name="correct-option"
                        checked={option.correct}
                        onChange={() =>
                          setQuestionForm({
                            ...questionForm,
                            options: questionForm.options.map((item, itemIndex) => ({
                              ...item,
                              correct: itemIndex === index,
                            })),
                          })
                        }
                      />
                      Correta
                    </label>
                  </div>
                ))
              )}
              <div className="form-actions">
                <button type="submit" disabled={questionSaving}>
                  {questionSaving ? 'Salvando...' : 'Criar questão'}
                </button>
                <button
                  type="button"
                  className="secondary"
                  disabled={questionGenerating}
                  onClick={() => void generateQuestionWithAi()}
                >
                  {questionGenerating
                    ? 'Gerando com IA...'
                    : `Gerar questão com IA (${difficultyLabels[questionForm.difficulty]} · ${formatLabels[questionForm.format]})`}
                </button>
              </div>
            </form>
            {questionsLoading ? <p className="empty">Carregando questões...</p> : questions.length === 0 ? (
              <p className="empty">Nenhuma questão cadastrada neste bloco.</p>
            ) : (
              <div className="question-list">
                {questions.map((question) => {
                  const result = questionResult[question.id]
                  return <article className="card question-card" key={question.id}>
                    <div className="block-card-header">
                      <h3>{question.prompt}</h3>
                      <span className="badge">
                        {difficultyLabels[question.difficulty]} · {formatLabels[question.format]}
                      </span>
                    </div>
                    {question.format === 'DISCURSIVE' ? (
                      <div className="discursive-answer-box">
                        <textarea
                          maxLength={10000}
                          value={discursiveInputs[question.id] ?? ''}
                          onChange={(event) =>
                            setDiscursiveInputs({ ...discursiveInputs, [question.id]: event.target.value })
                          }
                          placeholder="Escreva sua resposta discursiva..."
                        />
                        <button
                          type="button"
                          onClick={() => void answerDiscursiveQuestion(question.id)}
                          disabled={!discursiveInputs[question.id]?.trim()}
                        >
                          Avaliar resposta discursiva
                        </button>
                      </div>
                    ) : (
                      <div className="answer-options">
                        {question.options.map((option) => (
                          <button className="secondary answer-option" key={option.id} onClick={() => void answerQuestion(question.id, option.id)}>
                            {option.text}
                          </button>
                        ))}
                      </div>
                    )}
                    {result && <p className={result.correct ? 'answer-correct' : 'answer-wrong'}>
                      {result.correct ? 'Resposta correta (critérios atendidos).' : 'Resposta incorreta ou incompleta.'} {result.explanation}
                    </p>}
                    <p className="review-detail">
                      Próxima revisão em {question.reviewIntervalDays} dia(s); sequência de acertos: {question.correctStreak}.
                    </p>
                    <button className="danger" onClick={() => void archiveQuestion(question.id)}>Arquivar questão</button>
                  </article>
                })}
              </div>
            )}
          </div>
          <TopicsPanel
            blockId={selectedBlockId}
            questions={questions.map((question) => ({ id: question.id, prompt: question.prompt }))}
            evidenceVersion={evidenceVersion}
          />
          <div className="sessions-section">
            <h2>Sessões de estudo</h2>
            <form className="card session-form" onSubmit={createSession}>
              <label>
                Duração planejada (minutos)
                <input type="number" min={5} max={720} value={sessionMinutes}
                  onChange={(event) => setSessionMinutes(Number(event.target.value))} />
              </label>
              <label>
                Observações
                <textarea maxLength={2000} value={sessionNotes}
                  onChange={(event) => setSessionNotes(event.target.value)}
                  placeholder="Ex.: Resolver exercícios de listas" />
              </label>
              <button type="submit" disabled={sessionSaving}>
                {sessionSaving ? 'Criando...' : 'Planejar sessão'}
              </button>
            </form>
            {sessionsLoading ? <p className="empty">Carregando sessões...</p> : sessions.length === 0 ? (
              <p className="empty">Nenhuma sessão planejada neste bloco.</p>
            ) : (
              <div className="session-list">
                {sessions.map((session) => (
                  <article className="card session-card" key={session.id}>
                    <div className="block-card-header">
                      <h3>{session.plannedMinutes} minutos</h3>
                      <span className="badge">{session.status === 'PLANNED' ? 'Planejada'
                        : session.status === 'IN_PROGRESS' ? 'Em andamento'
                          : session.status === 'COMPLETED' ? 'Concluída' : 'Cancelada'}</span>
                    </div>
                    {session.notes && <p>{session.notes}</p>}
                    <div className="card-actions">
                      {session.status === 'PLANNED' && <button onClick={() => void updateSession(session, 'start')}>Iniciar</button>}
                      {session.status === 'IN_PROGRESS' && <button onClick={() => void updateSession(session, 'complete')}>Concluir</button>}
                      {(session.status === 'PLANNED' || session.status === 'IN_PROGRESS') &&
                        <button className="danger" onClick={() => void updateSession(session, 'cancel')}>Cancelar</button>}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </div>
          <div className="exercises-section">
            <h2>Exercícios práticos</h2>
            <p className="subtitle">Responda por texto e registre sua própria avaliação. O sistema não executa código enviado.</p>
            <form className="card exercise-form" onSubmit={createExercise}>
              <input required maxLength={160} value={exerciseTitle} onChange={(event) => setExerciseTitle(event.target.value)} placeholder="Título do exercício" />
              <textarea required maxLength={10000} value={exercisePrompt} onChange={(event) => setExercisePrompt(event.target.value)} placeholder="Enunciado e objetivo prático" />
              <textarea maxLength={5000} value={exerciseGuidance} onChange={(event) => setExerciseGuidance(event.target.value)} placeholder="Dicas para orientar o estudo" />
              <button type="submit" disabled={exerciseSaving}>{exerciseSaving ? 'Criando...' : 'Criar exercício'}</button>
            </form>
            {exercises.length === 0 ? <p className="empty">Nenhum exercício cadastrado neste bloco.</p> : (
              <div className="exercise-list">
                {exercises.map((exercise) => <article className="card exercise-card" key={exercise.id}>
                  <h3>{exercise.title}</h3>
                  <p className="material-content">{exercise.prompt}</p>
                  {exercise.guidance && <p><strong>Dica:</strong> {exercise.guidance}</p>}
                  <textarea maxLength={20000} value={exerciseAnswers[exercise.id] ?? ''}
                    onChange={(event) => setExerciseAnswers({ ...exerciseAnswers, [exercise.id]: event.target.value })}
                    placeholder="Escreva sua resposta..." />
                  <label>Autoavaliação
                    <select value={exerciseRatings[exercise.id] ?? 3}
                      onChange={(event) => setExerciseRatings({ ...exerciseRatings, [exercise.id]: Number(event.target.value) })}>
                      {[1, 2, 3, 4, 5].map((rating) => <option value={rating} key={rating}>{rating}/5</option>)}
                    </select>
                  </label>
                  <input maxLength={5000} value={exerciseFeedback[exercise.id] ?? ''}
                    onChange={(event) => setExerciseFeedback({ ...exerciseFeedback, [exercise.id]: event.target.value })}
                    placeholder="O que você aprendeu ou precisa revisar?" />
                  <button onClick={() => void submitExercise(exercise.id)}>Salvar tentativa</button>
                  <button className="danger" onClick={() => void archiveExercise(exercise.id)}>Arquivar exercício</button>
                </article>)}
              </div>
            )}
          </div>
          <div className="goals-section">
            <h2>Metas de estudo</h2>
            <form className="card goal-form" onSubmit={createGoal}>
              <input required maxLength={160} value={goalTitle}
                onChange={(event) => setGoalTitle(event.target.value)} placeholder="Título da meta" />
              <textarea maxLength={2000} value={goalDescription}
                onChange={(event) => setGoalDescription(event.target.value)} placeholder="Descrição da meta" />
              <label>Unidades necessárias
                <input type="number" min={1} max={100000} value={goalTarget}
                  onChange={(event) => setGoalTarget(Number(event.target.value))} />
              </label>
              <button type="submit" disabled={goalSaving}>{goalSaving ? 'Criando...' : 'Criar meta'}</button>
            </form>
            {goals.length === 0 ? <p className="empty">Nenhuma meta cadastrada neste bloco.</p> : (
              <div className="goal-list">
                {goals.map((goal) => <article className="card goal-card" key={goal.id}>
                  <div className="block-card-header">
                    <h3>{goal.title}</h3>
                    <span className="badge">{goal.status === 'COMPLETED' ? 'Concluída' : goal.status === 'ARCHIVED' ? 'Arquivada' : 'Ativa'}</span>
                  </div>
                  {goal.description && <p>{goal.description}</p>}
                  <p>Progresso: <strong>{goal.completedUnits}</strong> de <strong>{goal.targetUnits}</strong></p>
                  {goal.status === 'ACTIVE' && <div className="card-actions">
                    <button onClick={() => void addGoalProgress(goal)}>Registrar 1 unidade</button>
                    <button className="danger" onClick={() => void archiveGoal(goal.id)}>Arquivar meta</button>
                  </div>}
                </article>)}
              </div>
            )}
          </div>
        </section>
      )}
      <TutorChatPanel selectedBlockId={selectedBlockId} selectedBlockTitle={selectedBlockTitle} />
    </main>
  )
}

export default App
