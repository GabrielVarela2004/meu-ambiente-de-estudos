import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { request } from '../../api/http'
import '../../styles/pages/learning-panels.css'
import { useDeferredEffect } from '../../hooks/useDeferredEffect'
import { TopicReviewPanel } from './TopicReviewPanel'

export type LearningState = 'NOT_STUDIED' | 'INSUFFICIENT_DATA' | 'LEARNING' | 'NEEDS_REVIEW' | 'CONSISTENT'
export type KnowledgeTopicOrigin = 'MANUAL' | 'AI_EXTRACTED'

export type KnowledgeTopic = {
  id: string
  parentTopicId: string | null
  prerequisiteTopicId: string | null
  prerequisiteWarning: string | null
  sourceMaterialId: string | null
  sourceMaterialTitle: string | null
  supportingExcerpt: string | null
  origin: KnowledgeTopicOrigin
  userValidated: boolean
  extractedAt: string | null
  title: string
  description: string | null
  status: 'ACTIVE' | 'ARCHIVED' | 'PENDING_REVIEW'
  questionIds: string[]
  learningState: LearningState
  stateReason: string
  attempts: number
  recentAccuracyPercent: number | null
  masteryPercent: number | null
  theoryPercent: number | null
  practicalPercent: number | null
  practicalExercises: number
  mastered: boolean
  masteryReason: string
}

type QuestionSummary = { id: string; prompt: string }

type TopicsPanelProps = {
  blockId: string
  questions: QuestionSummary[]
  // Muda sempre que uma resposta é registrada ou quando fontes mudam, para recalcular os estados com dados atualizados.
  evidenceVersion: number
  onTopicsChanged?: (topics: KnowledgeTopic[]) => void
}

const stateLabels: Record<LearningState, string> = {
  NOT_STUDIED: 'Não estudado',
  INSUFFICIENT_DATA: 'Dados insuficientes',
  LEARNING: 'Em aprendizagem',
  NEEDS_REVIEW: 'Precisa de revisão',
  CONSISTENT: 'Consistente (domínio estimado)',
}

export function TopicsPanel({ blockId, questions, evidenceVersion, onTopicsChanged }: TopicsPanelProps) {
  const [topics, setTopics] = useState<KnowledgeTopic[]>([])
  const [loading, setLoading] = useState(false)
  const [extracting, setExtracting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [parentTopicId, setParentTopicId] = useState('')
  const [prerequisiteTopicId, setPrerequisiteTopicId] = useState('')
  const [saving, setSaving] = useState(false)
  const [reviewVersion, setReviewVersion] = useState(0)

  const loadTopics = useCallback(async () => {
    setLoading(true)
    try {
      const loaded = await request<KnowledgeTopic[]>(`/api/study-blocks/${blockId}/topics`)
      setTopics(loaded)
      onTopicsChanged?.(loaded)
      setError(null)
    } catch {
      setError('Não foi possível carregar os tópicos deste bloco.')
    } finally {
      setLoading(false)
    }
  }, [blockId, onTopicsChanged])

  useDeferredEffect(() => loadTopics(), [loadTopics, evidenceVersion])

  const extractTopicsFromSources = async () => {
    setExtracting(true)
    setError(null)
    try {
      // A extração devolve os tópicos oficiais; o que ela encontrou de novo vai para a validação manual.
      const extracted = await request<KnowledgeTopic[]>(`/api/study-blocks/${blockId}/topics/extract`, {
        method: 'POST',
      })
      setTopics(extracted)
      onTopicsChanged?.(extracted)
      setReviewVersion((version) => version + 1)
    } catch (err) {
      setError(
        err instanceof Error && err.message
          ? err.message
          : 'Não foi possível extrair tópicos das fontes deste bloco. Adicione fontes concluídas primeiro.',
      )
    } finally {
      setExtracting(false)
    }
  }

  const validateTopic = async (topicId: string) => {
    try {
      await request<KnowledgeTopic>(`/api/study-blocks/${blockId}/topics/${topicId}/validate`, {
        method: 'POST',
      })
      await loadTopics()
    } catch {
      setError('Não foi possível validar o tópico.')
    }
  }

  const createTopic = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    try {
      await request(`/api/study-blocks/${blockId}/topics`, {
        method: 'POST',
        body: JSON.stringify({
          title,
          description,
          parentTopicId: parentTopicId || null,
          prerequisiteTopicId: prerequisiteTopicId || null,
        }),
      })
      setTitle('')
      setDescription('')
      setParentTopicId('')
      setPrerequisiteTopicId('')
      await loadTopics()
    } catch {
      setError('Não foi possível criar o tópico. Informe um título; subtópicos não podem ter outros subtópicos.')
    } finally {
      setSaving(false)
    }
  }

  const updatePrerequisite = async (topicId: string, newPrerequisiteId: string) => {
    try {
      await request(`/api/study-blocks/${blockId}/topics/${topicId}/prerequisite`, {
        method: 'PUT',
        body: JSON.stringify({ prerequisiteTopicId: newPrerequisiteId || null }),
      })
      await loadTopics()
    } catch {
      setError('Não foi possível definir este pré-requisito (verifique se não gera dependência circular).')
    }
  }

  const toggleQuestion = async (topic: KnowledgeTopic, questionId: string) => {
    const linked = topic.questionIds.includes(questionId)
    try {
      await request(`/api/study-blocks/${blockId}/topics/${topic.id}/questions/${questionId}`, {
        method: linked ? 'DELETE' : 'POST',
      })
      await loadTopics()
    } catch {
      setError('Não foi possível atualizar o vínculo da questão.')
    }
  }

  const archiveTopic = async (topic: KnowledgeTopic) => {
    if (!window.confirm(`Arquivar o tópico "${topic.title}"? O histórico de respostas é preservado.`)) return
    try {
      await request(`/api/study-blocks/${blockId}/topics/${topic.id}`, { method: 'DELETE' })
      await loadTopics()
    } catch {
      setError('Não foi possível arquivar. Arquive primeiro os subtópicos ativos.')
    }
  }

  const rootTopics = topics.filter((topic) => topic.parentTopicId === null)
  const childrenOf = (id: string) => topics.filter((topic) => topic.parentTopicId === id)

  const renderTopic = (topic: KnowledgeTopic, isSubtopic: boolean) => (
    <article className={`card topic-card ${isSubtopic ? 'subtopic' : ''}`} key={topic.id}>
      <div className="block-card-header">
        <div>
          <h3>{isSubtopic ? '↳ ' : ''}{topic.title}</h3>
          <div className="topic-badges-row">
            <span className="phase">
              {topic.origin === 'AI_EXTRACTED'
                ? topic.userValidated
                  ? 'IA · Validado pelo usuário'
                  : 'Identificado pela IA (Pendente validação)'
                : 'Criado manualmente'}
            </span>
            {topic.sourceMaterialTitle && (
              <span className="phase">Fonte: {topic.sourceMaterialTitle}</span>
            )}
            {topic.extractedAt && (
              <span className="review-detail">
                Análise: {new Date(topic.extractedAt).toLocaleString('pt-BR')}
              </span>
            )}
          </div>
        </div>
        <span className={`state-badge state-${topic.learningState.toLowerCase()}`}>
          {stateLabels[topic.learningState]}
        </span>
      </div>
      {topic.description && <p>{topic.description}</p>}
      {topic.supportingExcerpt && (
        <blockquote className="topic-supporting-excerpt">
          <strong>Trecho de evidência na fonte:</strong> “{topic.supportingExcerpt}”
        </blockquote>
      )}
      <p className="review-detail"><strong>Por quê:</strong> {topic.stateReason}</p>
      {topic.prerequisiteWarning && (
        <p className="answer-wrong">
          <strong>Atenção (Pré-requisito):</strong> {topic.prerequisiteWarning}
        </p>
      )}
      <p className="review-detail">
        Respostas registradas: {topic.attempts}
        {topic.recentAccuracyPercent !== null && <> · acerto recente: {topic.recentAccuracyPercent}%</>}
      </p>
      <div className="topic-mastery">
        <div className="topic-mastery-head">
          <strong>Desempenho no tópico: {topic.masteryPercent ?? 0}%</strong>
          {topic.mastered && <span className="badge badge-emerald">Dominado</span>}
        </div>
        <div className="topic-mastery-bar" aria-hidden="true">
          <span style={{ width: `${topic.masteryPercent ?? 0}%` }} />
        </div>
        <p className="review-detail">
          Prática (peso 70%): {topic.practicalPercent === null ? 'sem exercícios' : `${topic.practicalPercent}% em ${topic.practicalExercises} exercício(s)`}
          {' · '}Teoria (peso 30%): {topic.theoryPercent === null ? 'sem dados suficientes' : `${topic.theoryPercent}%`}
        </p>
        <p className="review-detail">{topic.masteryReason}</p>
      </div>
      <label className="prerequisite-selector">
        Pré-requisito deste tópico:{' '}
        <select
          value={topic.prerequisiteTopicId ?? ''}
          onChange={(event) => void updatePrerequisite(topic.id, event.target.value)}
        >
          <option value="">Sem pré-requisito</option>
          {topics
            .filter((item) => item.id !== topic.id)
            .map((item) => (
              <option value={item.id} key={item.id}>
                {item.title}
              </option>
            ))}
        </select>
      </label>
      <details>
        <summary>Questões vinculadas ({topic.questionIds.length})</summary>
        {questions.length === 0 ? (
          <p className="empty">Nenhuma questão ativa neste bloco para vincular.</p>
        ) : (
          <div className="topic-question-links">
            {questions.map((question) => (
              <label className="toggle" key={question.id}>
                <input type="checkbox" checked={topic.questionIds.includes(question.id)}
                  onChange={() => void toggleQuestion(topic, question.id)} />
                {question.prompt}
              </label>
            ))}
          </div>
        )}
      </details>
      <div className="card-actions">
        {topic.origin === 'AI_EXTRACTED' && !topic.userValidated && (
          <button type="button" className="btn btn-primary btn-sm" onClick={() => void validateTopic(topic.id)}>
            Validar tópico
          </button>
        )}
        <button type="button" className="btn btn-danger btn-sm" onClick={() => void archiveTopic(topic)}>Arquivar tópico</button>
      </div>
    </article>
  )

  return (
    <div className="topics-section">
      <div className="section-heading">
        <div>
          <h2>Tópicos e mapeamento de assuntos</h2>
          <p className="subtitle">
            A IA sugere só tópicos técnicos das fontes, com relevância; você valida antes de virarem oficiais. Tópicos
            também podem ser criados manualmente.
          </p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => void extractTopicsFromSources()} disabled={extracting}>
          {extracting ? 'Analisando fontes...' : 'Atualizar mapeamento das fontes'}
        </button>
      </div>
      <TopicReviewPanel blockId={blockId} reloadKey={reviewVersion + evidenceVersion} onPublished={() => void loadTopics()} />
      <details className="criteria">
        <summary>Como o estado é calculado</summary>
        <ul>
          <li><strong>Dados insuficientes</strong>: nenhuma questão vinculada ou menos de 3 respostas.</li>
          <li><strong>Não estudado</strong>: há questões vinculadas, mas nenhuma resposta.</li>
          <li><strong>Precisa de revisão</strong>: menos de 60% de acerto nas últimas 5 respostas.</li>
          <li><strong>Consistente</strong>: ao menos 5 respostas, 80% ou mais de acerto recente, acertos em 2 questões
            diferentes e a última resposta correta. É uma estimativa, não uma garantia de domínio.</li>
          <li><strong>Em aprendizagem</strong>: os demais casos.</li>
        </ul>
      </details>
      <form className="card topic-form" onSubmit={createTopic}>
        <input required maxLength={160} value={title} onChange={(event) => setTitle(event.target.value)}
          placeholder="Título do tópico manual (ex.: Listas)" />
        <textarea maxLength={2000} value={description} onChange={(event) => setDescription(event.target.value)}
          placeholder="Descrição (opcional)" />
        <label>Tópico pai (deixe vazio para criar um tópico principal)
          <select value={parentTopicId} onChange={(event) => setParentTopicId(event.target.value)}>
            <option value="">Nenhum (tópico principal)</option>
            {rootTopics.map((topic) => <option value={topic.id} key={topic.id}>{topic.title}</option>)}
          </select>
        </label>
        <label>Tópico pré-requisito (opcional)
          <select value={prerequisiteTopicId} onChange={(event) => setPrerequisiteTopicId(event.target.value)}>
            <option value="">Sem pré-requisito</option>
            {topics.map((topic) => <option value={topic.id} key={topic.id}>{topic.title}</option>)}
          </select>
        </label>
        <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Criando...' : 'Criar tópico'}</button>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
      {loading && topics.length === 0 ? <p className="empty">Carregando tópicos...</p> : topics.length === 0 ? (
        <p className="empty">
          Nenhum tópico oficial neste bloco. Valide as sugestões acima ou clique em "Atualizar mapeamento das fontes".
        </p>
      ) : (
        <div className="topic-list">
          {rootTopics.map((topic) => (
            <div key={topic.id}>
              {renderTopic(topic, false)}
              {childrenOf(topic.id).map((child) => renderTopic(child, true))}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
