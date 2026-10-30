import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { request } from '../../api/http'
import type { SourceType, StudyMaterialItem } from '../sources/AddSourceModal'
import { TimestampText } from '../../components/video/TimestampText'
import { useVideoPlayer } from '../video/videoPlayer'

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

type ReferencedSource = {
  id: string
  title: string
  sourceType: SourceType
  sourceUrl: string | null
  studyBlockId?: string | null
  studyBlockTitle?: string | null
}

type SuggestedQuestion = {
  sourceId: string | null
  sourceTitle: string
  questionText: string
}

type BlockOption = {
  id: string
  title: string
}

type TutorChatPanelProps = {
  selectedBlockId: string | null
  selectedBlockTitle: string | null
  activeSources?: StudyMaterialItem[]
  allBlocks?: BlockOption[]
  onInspectSource?: (sourceId: string) => void
}

const sourceTypeBadgeLabels: Record<SourceType, string> = {
  TEXT: 'Texto',
  FILE_TXT: 'TXT',
  FILE_MD: 'MD',
  FILE_PDF: 'PDF',
  FILE_DOCX: 'DOCX',
  FILE_PPTX: 'PPTX',
  FILE_CODE: 'Código',
  WEB: 'Web',
  YOUTUBE: 'YouTube',
}

export function TutorChatPanel({
  selectedBlockId,
  selectedBlockTitle,
  activeSources = [],
  allBlocks = [],
  onInspectSource,
}: TutorChatPanelProps) {
  const [chatMode, setChatMode] = useState<'block' | 'universal'>(selectedBlockId ? 'block' : 'universal')
  const [message, setMessage] = useState('')
  const [selectedSourceIds, setSelectedSourceIds] = useState<string[]>([])
  const [selectedUniversalBlockIds, setSelectedUniversalBlockIds] = useState<string[]>([])
  const [suggestedQuestions, setSuggestedQuestions] = useState<SuggestedQuestion[]>([])
  const [inspectingSource, setInspectingSource] = useState<{ id: string; title: string; content?: string } | null>(null)
  const [latestAnswer, setLatestAnswer] = useState<{
    answer: string
    model: string
    referencedMaterials: string[]
    referencedSources?: ReferencedSource[]
  } | null>(null)
  const [history, setHistory] = useState<ChatHistoryItem[]>([])
  const videoPlayer = useVideoPlayer()

  /**
   * Vídeo usado pelos minutos clicáveis: a fonte YouTube citada na resposta ou, sem citação,
   * a primeira fonte YouTube ativa no chat. Sem vídeo, os minutos aparecem como texto comum.
   */
  const seekHandlerFor = (referencedIds: string[] = []) => {
    const videos = activeSources.filter((s) => s.sourceType === 'YOUTUBE' && s.externalVideoId)
    const video = videos.find((s) => referencedIds.includes(s.id)) ?? videos[0]
    if (!video || !videoPlayer) return undefined
    return (seconds: number) =>
      videoPlayer.openVideo({ videoId: video.externalVideoId as string, title: video.title, seconds })
  }
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
    if (chatMode === 'block') {
      if (!selectedBlockId) {
        setHistory([])
        return
      }
      try {
        setHistory(await request<ChatHistoryItem[]>(`/api/study-blocks/${selectedBlockId}/chat/messages`))
      } catch {
        setError('Não foi possível carregar o histórico de conversas deste bloco.')
      }
    } else {
      try {
        setHistory(await request<ChatHistoryItem[]>('/api/chat/universal/messages'))
      } catch {
        setError('Não foi possível carregar o histórico do chat universal.')
      }
    }
  }, [chatMode, selectedBlockId])

  const loadSuggestedQuestions = useCallback(async () => {
    if (chatMode === 'block') {
      if (!selectedBlockId) {
        setSuggestedQuestions([])
        return
      }
      try {
        const data = await request<SuggestedQuestion[]>(
          `/api/study-blocks/${selectedBlockId}/chat/suggested-questions`,
        )
        setSuggestedQuestions(data)
      } catch {
        // Silencioso se bloco não tiver fontes
      }
    } else {
      if (allBlocks.length >= 2) {
        setSuggestedQuestions([
          {
            sourceId: null,
            sourceTitle: 'Síntese Transversal',
            questionText: `Faça uma síntese comparativa destacando semelhanças e diferenças entre os conceitos de "${allBlocks[0].title}" e "${allBlocks[1].title}".`,
          },
          {
            sourceId: null,
            sourceTitle: 'Conexões Conceituais',
            questionText: 'Quais conceitos fundamentais conectam os blocos selecionados e como eles se complementam na prática?',
          },
          {
            sourceId: null,
            sourceTitle: 'Visão Holística',
            questionText: 'Com base em todas as fontes dos blocos selecionados, liste os princípios essenciais que um estudante deve dominar.',
          },
        ])
      } else {
        setSuggestedQuestions([
          {
            sourceId: null,
            sourceTitle: 'Visão Geral Universal',
            questionText: 'Sintetize os conceitos mais importantes de todas as fontes cadastradas nos meus blocos.',
          },
        ])
      }
    }
  }, [chatMode, selectedBlockId, allBlocks])

  useEffect(() => {
    void loadStatus()
  }, [loadStatus])

  useEffect(() => {
    void loadHistory()
    void loadSuggestedQuestions()
    setSelectedSourceIds([])
    setLatestAnswer(null)
  }, [loadHistory, loadSuggestedQuestions])

  useEffect(() => {
    if (allBlocks.length > 0 && selectedUniversalBlockIds.length === 0) {
      setSelectedUniversalBlockIds(allBlocks.map((b) => b.id))
    }
  }, [allBlocks, selectedUniversalBlockIds.length])

  const toggleSourceFilter = (sourceId: string) => {
    setSelectedSourceIds((current) =>
      current.includes(sourceId) ? current.filter((id) => id !== sourceId) : [...current, sourceId],
    )
  }

  const toggleUniversalBlock = (blockId: string) => {
    setSelectedUniversalBlockIds((current) =>
      current.includes(blockId) ? current.filter((id) => id !== blockId) : [...current, blockId],
    )
  }

  const askTutor = async (event: FormEvent) => {
    event.preventDefault()
    setLoading(true)
    setLatestAnswer(null)
    setError(null)
    try {
      let bodyData: {
        message: string
        studyBlockId?: string | null
        studyBlockIds?: string[] | null
        sourceIds?: string[] | null
      }

      if (chatMode === 'block') {
        const validSelectedIds = selectedSourceIds.filter((id) =>
          activeSources.some((source) => source.id === id),
        )
        bodyData = {
          message,
          studyBlockId: selectedBlockId,
          sourceIds: validSelectedIds.length > 0 ? validSelectedIds : null,
        }
      } else {
        bodyData = {
          message,
          studyBlockIds: selectedUniversalBlockIds.length > 0 ? selectedUniversalBlockIds : null,
          sourceIds: null,
        }
      }

      const result = await request<{
        answer: string
        model: string
        referencedMaterials: string[]
        referencedSources?: ReferencedSource[]
      }>('/api/chat', {
        method: 'POST',
        body: JSON.stringify(bodyData),
      })
      setLatestAnswer(result)
      setMessage('')
      await loadHistory()
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Não foi possível obter resposta do tutor. Verifique a configuração do modelo de IA.',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="card chat-panel">
      <div className="block-card-header">
        <div>
          <h2>Chat com IA (Tutor Inteligente)</h2>
          <div className="segmented" role="group" aria-label="Modo do chat" style={{ marginTop: '8px' }}>
            <button
              type="button"
              className={chatMode === 'block' ? 'nav-tab-btn active' : 'nav-tab-btn'}
              aria-pressed={chatMode === 'block'}
              onClick={() => setChatMode('block')}
              disabled={!selectedBlockId}
            >
              Chat do Bloco {selectedBlockTitle ? `(${selectedBlockTitle})` : ''}
            </button>
            <button
              type="button"
              className={chatMode === 'universal' ? 'nav-tab-btn active' : 'nav-tab-btn'}
              aria-pressed={chatMode === 'universal'}
              onClick={() => setChatMode('universal')}
            >
              Chat Universal Multi-Bloco 🌐
            </button>
          </div>
          <p className="subtitle" style={{ marginTop: '8px' }}>
            {chatMode === 'block'
              ? selectedBlockTitle
                ? `Tutor fundamentado nas fontes do bloco "${selectedBlockTitle}". Estilo NotebookLM.`
                : 'Abra um bloco para consultar o tutor contextual.'
              : 'Modo Universal: pesquise e compare múltiplos blocos de estudo simultaneamente com síntese cruzada.'}
          </p>
        </div>
        {providerStatus && (
          <span className="badge">
            {providerStatus.configured
              ? `IA Ativa (${providerStatus.providerName})`
              : 'Modo Offline (Sem chave API)'}
          </span>
        )}
      </div>

      {chatMode === 'universal' && allBlocks.length > 0 && (
        <div className="card chat-sources-scope">
          <h4>Blocos incluídos na consulta universal:</h4>
          <div className="chat-source-pills" style={{ marginTop: '6px' }}>
            {allBlocks.map((block) => {
              const isSelected = selectedUniversalBlockIds.includes(block.id)
              return (
                <button
                  type="button"
                  key={block.id}
                  className={isSelected ? 'source-filter-pill active' : 'source-filter-pill'}
                  aria-pressed={isSelected}
                  onClick={() => toggleUniversalBlock(block.id)}
                >
                  {isSelected ? '✓ ' : '+ '}
                  {block.title}
                </button>
              )
            })}
          </div>
        </div>
      )}

      {chatMode === 'block' && selectedBlockId && (
        <div className="card chat-sources-scope">
          <h4>Escopo de fontes para consulta</h4>
          {activeSources.length === 0 ? (
            <p className="empty" style={{ margin: '8px 0 0' }}>
              Nenhuma fonte ativa cadastrada. Adicione arquivos, textos ou vídeos do YouTube na aba
              de fontes para fundamentar as respostas do tutor.
            </p>
          ) : (
            <>
              <p className="subtitle" style={{ margin: '4px 0 8px' }}>
                Clique para filtrar fontes específicas ou deixe todas selecionadas por padrão:
              </p>
              <div className="chat-source-pills">
                {activeSources.map((source) => {
                  const isFiltered = selectedSourceIds.includes(source.id)
                  return (
                    <button
                      type="button"
                      key={source.id}
                      className={
                        selectedSourceIds.length === 0 || isFiltered
                          ? 'source-filter-pill active'
                          : 'source-filter-pill'
                      }
                      onClick={() => toggleSourceFilter(source.id)}
                    >
                      <span className="source-pill-type">
                        {sourceTypeBadgeLabels[source.sourceType] ?? 'Texto'}
                      </span>{' '}
                      {source.title}
                    </button>
                  )
                })}
              </div>
            </>
          )}
        </div>
      )}

      {suggestedQuestions.length > 0 && (
        <div className="chat-suggestions">
          <p className="chat-suggestions-title">
            💡 Perguntas sugeridas com base nas suas fontes:
          </p>
          <div className="chat-suggestions-list">
            {suggestedQuestions.map((sug, idx) => (
              <button
                key={idx}
                type="button"
                className="chat-suggestion-btn"
                onClick={() => setMessage(sug.questionText)}
              >
                {sug.questionText}
              </button>
            ))}
          </div>
        </div>
      )}

      <form className="chat-form" onSubmit={askTutor}>
        <textarea
          required
          maxLength={10000}
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          placeholder={
            chatMode === 'block'
              ? 'Ex.: Com base nas fontes deste bloco, explique os conceitos principais ou compare os tópicos abordados...'
              : 'Ex.: Compare a abordagem dos blocos selecionados, destacando como eles se relacionam e os pontos críticos de cada um...'
          }
        />
        <button type="submit" className="btn btn-primary" disabled={loading} aria-busy={loading || undefined}>
          {loading && <span className="spinner" aria-hidden="true" />}
          {loading ? 'Consultando fontes e gerando resposta...' : 'Perguntar ao tutor'}
        </button>
      </form>

      {error && (
        <p className="error" role="alert" style={{ marginTop: '12px' }}>
          {error}
        </p>
      )}

      {latestAnswer && (
        <article className="card chat-answer">
          <div className="block-card-header">
            <h3>Resposta do Tutor {chatMode === 'universal' ? '(Universal Multi-Bloco)' : ''}</h3>
            <span className="badge">Modelo: {latestAnswer.model}</span>
          </div>
          <p style={{ whiteSpace: 'pre-wrap' }}>
            <TimestampText
              text={latestAnswer.answer}
              onSeek={seekHandlerFor(latestAnswer.referencedSources?.map((s) => s.id))}
            />
          </p>
          {latestAnswer.referencedSources && latestAnswer.referencedSources.length > 0 && (
            <div className="referenced-sources-box">
              <strong>Fontes citadas nesta resposta:</strong>
              <div className="chat-source-pills" style={{ marginTop: '6px' }}>
                {latestAnswer.referencedSources.map((source) => (
                  <button
                    type="button"
                    key={source.id}
                    className="source-citation-pill"
                    onClick={() => {
                      if (onInspectSource) {
                        onInspectSource(source.id)
                      } else {
                        setInspectingSource({ id: source.id, title: source.title })
                      }
                    }}
                    title="Clique para inspecionar esta fonte"
                  >
                    📖 {source.studyBlockTitle ? `[${source.studyBlockTitle}] ` : ''}{source.title}
                  </button>
                ))}
              </div>
            </div>
          )}
        </article>
      )}

      {inspectingSource && (
        <div className="modal-overlay" onClick={() => setInspectingSource(null)}>
          <div className="modal-content card" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '600px' }}>
            <div className="block-card-header">
              <h3>Fonte: {inspectingSource.title}</h3>
              <button className="secondary" onClick={() => setInspectingSource(null)}>Fechar</button>
            </div>
            <p className="subtitle">Esta fonte foi referenciada e utilizada diretamente para fundamentar a resposta do tutor.</p>
          </div>
        </div>
      )}

      {history.length > 0 && (
        <div className="chat-history">
          <h3>
            {chatMode === 'block'
              ? 'Histórico da conversa neste bloco'
              : 'Histórico da conversa no Modo Universal'}
          </h3>
          <div className="material-list">
            {history.map((item) => (
              <article className="card chat-history-card" key={item.id}>
                <p>
                  <strong>Você:</strong> {item.userMessage}
                </p>
                <p style={{ whiteSpace: 'pre-wrap' }}>
                  <strong>Tutor ({item.modelName}):</strong>{' '}
                  <TimestampText text={item.assistantAnswer} onSeek={seekHandlerFor()} />
                </p>
                {item.referencedMaterials && (
                  <p className="subtitle" style={{ fontSize: '0.8rem' }}>
                    Fontes consultadas: {item.referencedMaterials}
                  </p>
                )}
                <small className="chat-history-time">
                  {new Date(item.createdAt).toLocaleString('pt-BR')}
                </small>
              </article>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}
