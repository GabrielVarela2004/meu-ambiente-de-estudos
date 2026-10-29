import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { request } from '../../api/http'

type ChatHistoryItem = {
  id: string
  studyBlockId: string | null
  userMessage: string
  assistantAnswer: string
  providerName: string
  modelName: string
  referencedMaterials: string | null
  createdAt: string
}

type ProviderStatus = {
  providerName: string
  configured: boolean
}

type TutorChatPanelProps = {
  selectedBlockId: string | null
  selectedBlockTitle: string | null
}

export function TutorChatPanel({ selectedBlockId, selectedBlockTitle }: TutorChatPanelProps) {
  const [message, setMessage] = useState('')
  const [latestAnswer, setLatestAnswer] = useState<{
    answer: string
    model: string
    referencedMaterials: string[]
  } | null>(null)
  const [history, setHistory] = useState<ChatHistoryItem[]>([])
  const [providerStatus, setProviderStatus] = useState<ProviderStatus | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadStatus = useCallback(async () => {
    try {
      setProviderStatus(await request<ProviderStatus>('/api/chat/status'))
    } catch {
      // Ignorado se o backend ainda estiver subindo
    }
  }, [])

  const loadHistory = useCallback(async () => {
    if (!selectedBlockId) {
      setHistory([])
      return
    }
    try {
      setHistory(await request<ChatHistoryItem[]>(`/api/study-blocks/${selectedBlockId}/chat/messages`))
    } catch {
      setError('Não foi possível carregar o histórico de conversas deste bloco.')
    }
  }, [selectedBlockId])

  useEffect(() => {
    void loadStatus()
  }, [loadStatus])

  useEffect(() => {
    void loadHistory()
  }, [loadHistory])

  const askTutor = async (event: FormEvent) => {
    event.preventDefault()
    setLoading(true)
    setLatestAnswer(null)
    setError(null)
    try {
      const result = await request<{
        answer: string
        model: string
        referencedMaterials: string[]
      }>('/api/chat', {
        method: 'POST',
        body: JSON.stringify({ message, studyBlockId: selectedBlockId }),
      })
      setLatestAnswer(result)
      setMessage('')
      await loadHistory()
    } catch (requestError) {
      setError(
        requestError instanceof Error && requestError.message.includes('OpenAI integration')
          ? 'O provedor de IA não está configurado. Defina OPENAI_API_KEY no ambiente do backend.'
          : 'Não foi possível obter uma resposta do tutor.',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="chat-panel">
      <div className="section-heading">
        <div>
          <h2>Tutor de estudos com IA</h2>
          <p className="subtitle">
            {selectedBlockTitle
              ? `Contexto ativo: bloco "${selectedBlockTitle}". Os materiais ativos serão enviados como referência.`
              : 'Selecione um bloco acima para usar seus materiais como contexto e salvar o histórico da conversa.'}
          </p>
        </div>
        {providerStatus && (
          <span className={`state-badge ${providerStatus.configured ? 'state-consistent' : 'state-needs_review'}`}>
            {providerStatus.providerName}: {providerStatus.configured ? 'Configurado' : 'Sem chave (OPENAI_API_KEY)'}
          </span>
        )}
      </div>

      <form className="chat-form" onSubmit={askTutor}>
        <textarea
          required
          maxLength={10000}
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          placeholder="Ex.: Explique passo a passo os conceitos deste bloco ou tire uma dúvida..."
        />
        <button type="submit" disabled={loading}>
          {loading ? 'Consultando...' : 'Perguntar ao tutor'}
        </button>
      </form>

      {error && <p className="error" role="alert">{error}</p>}

      {latestAnswer && (
        <article className="card chat-answer">
          <div className="block-card-header">
            <strong>Resposta do tutor</strong>
            <span className="badge">{latestAnswer.model}</span>
          </div>
          <p>{latestAnswer.answer}</p>
          {latestAnswer.referencedMaterials.length > 0 ? (
            <p className="review-detail">
              <strong>Materiais consultados no bloco:</strong> {latestAnswer.referencedMaterials.join(', ')}
            </p>
          ) : (
            <p className="review-detail">
              <strong>Nota:</strong> Nenhum material ativo do bloco foi utilizado nesta resposta.
            </p>
          )}
        </article>
      )}

      {selectedBlockId && (
        <div className="chat-history">
          <h3>Histórico de conversas do bloco ({history.length})</h3>
          {history.length === 0 ? (
            <p className="empty">Nenhuma conversa registrada neste bloco ainda.</p>
          ) : (
            <div className="question-list">
              {history.map((item) => (
                <article className="card chat-history-card" key={item.id}>
                  <div className="block-card-header">
                    <strong>Você perguntou:</strong>
                    <span className="badge">{item.providerName} · {item.modelName}</span>
                  </div>
                  <p className="material-content">{item.userMessage}</p>
                  <strong>Resposta registrada:</strong>
                  <p className="material-content">{item.assistantAnswer}</p>
                  {item.referencedMaterials && (
                    <p className="review-detail">
                      <strong>Materiais de referência na ocasião:</strong> {item.referencedMaterials}
                    </p>
                  )}
                </article>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  )
}
