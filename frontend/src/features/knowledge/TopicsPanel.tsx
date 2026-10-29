import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { request } from '../../api/http'

export type LearningState = 'NOT_STUDIED' | 'INSUFFICIENT_DATA' | 'LEARNING' | 'NEEDS_REVIEW' | 'CONSISTENT'

type KnowledgeTopic = {
  id: string
  parentTopicId: string | null
  prerequisiteTopicId: string | null
  prerequisiteWarning: string | null
  title: string
  description: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  questionIds: string[]
  learningState: LearningState
  stateReason: string
  attempts: number
  recentAccuracyPercent: number | null
}

type QuestionSummary = { id: string; prompt: string }

type TopicsPanelProps = {
  blockId: string
  questions: QuestionSummary[]
  // Muda sempre que uma resposta é registrada, para recalcular os estados com dados atualizados.
  evidenceVersion: number
}

const stateLabels: Record<LearningState, string> = {
  NOT_STUDIED: 'Não estudado',
  INSUFFICIENT_DATA: 'Dados insuficientes',
  LEARNING: 'Em aprendizagem',
  NEEDS_REVIEW: 'Precisa de revisão',
  CONSISTENT: 'Consistente (domínio estimado)',
}

export function TopicsPanel({ blockId, questions, evidenceVersion }: TopicsPanelProps) {
  const [topics, setTopics] = useState<KnowledgeTopic[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [parentTopicId, setParentTopicId] = useState('')
  const [prerequisiteTopicId, setPrerequisiteTopicId] = useState('')
  const [saving, setSaving] = useState(false)

  const loadTopics = useCallback(async () => {
    setLoading(true)
    try {
      setTopics(await request<KnowledgeTopic[]>(`/api/study-blocks/${blockId}/topics`))
      setError(null)
    } catch {
      setError('Não foi possível carregar os tópicos deste bloco.')
    } finally {
      setLoading(false)
    }
  }, [blockId])

  useEffect(() => {
    void loadTopics()
  }, [loadTopics, evidenceVersion])

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
        <h3>{isSubtopic ? '↳ ' : ''}{topic.title}</h3>
        <span className={`state-badge state-${topic.learningState.toLowerCase()}`}>
          {stateLabels[topic.learningState]}
        </span>
      </div>
      {topic.description && <p>{topic.description}</p>}
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
        <button className="danger" onClick={() => void archiveTopic(topic)}>Arquivar tópico</button>
      </div>
    </article>
  )

  return (
    <div className="topics-section">
      <h2>Tópicos e estado de aprendizagem</h2>
      <p className="subtitle">
        O estado de cada tópico é estimado apenas pelas respostas às questões vinculadas a ele.
      </p>
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
          placeholder="Título do tópico (ex.: Listas)" />
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
        <button type="submit" disabled={saving}>{saving ? 'Criando...' : 'Criar tópico'}</button>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
      {loading && topics.length === 0 ? <p className="empty">Carregando tópicos...</p> : topics.length === 0 ? (
        <p className="empty">Nenhum tópico cadastrado neste bloco.</p>
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
