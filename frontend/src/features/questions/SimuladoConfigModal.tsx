import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { request } from '../../api/http'
import type { KnowledgeTopic } from '../knowledge/TopicsPanel'

export type QuestionDifficulty = 'EASY' | 'INTERMEDIATE' | 'HARD'
export type QuestionFormat = 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'DISCURSIVE'

type SimuladoConfigModalProps = {
  blockId: string
  blockTitle: string
  isOpen: boolean
  activeSourceCount: number
  onClose: () => void
  onSimuladoGenerated: (generatedCount: number) => Promise<void> | void
}

export function SimuladoConfigModal({
  blockId,
  blockTitle,
  isOpen,
  activeSourceCount,
  onClose,
  onSimuladoGenerated,
}: SimuladoConfigModalProps) {
  const [quantity, setQuantity] = useState(3)
  const [difficulty, setDifficulty] = useState<QuestionDifficulty>('INTERMEDIATE')
  const [format, setFormat] = useState<QuestionFormat>('MULTIPLE_CHOICE')
  const [selectedTopicId, setSelectedTopicId] = useState('')
  const [topics, setTopics] = useState<KnowledgeTopic[]>([])
  const [loadingTopics, setLoadingTopics] = useState(false)
  const [extractingTopics, setExtractingTopics] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!isOpen) return
    let active = true
    const fetchTopics = async () => {
      setLoadingTopics(true)
      setErrorMessage(null)
      try {
        const loaded = await request<KnowledgeTopic[]>(`/api/study-blocks/${blockId}/topics`)
        if (active) {
          setTopics(loaded)
        }
      } catch {
        if (active) {
          setTopics([])
        }
      } finally {
        if (active) {
          setLoadingTopics(false)
        }
      }
    }
    void fetchTopics()
    return () => {
      active = false
    }
  }, [blockId, isOpen])

  if (!isOpen) return null

  const handleExtractTopics = async () => {
    setExtractingTopics(true)
    setErrorMessage(null)
    try {
      const extracted = await request<KnowledgeTopic[]>(`/api/study-blocks/${blockId}/topics/extract`, {
        method: 'POST',
      })
      setTopics(extracted)
    } catch (err) {
      setErrorMessage(
        err instanceof Error && err.message
          ? err.message
          : 'Não foi possível mapear os tópicos. Verifique se há fontes concluídas no bloco.',
      )
    } finally {
      setExtractingTopics(false)
    }
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (quantity < 1 || quantity > 10) {
      setErrorMessage('Selecione uma quantidade válida entre 1 e 10 questões.')
      return
    }
    if (activeSourceCount === 0) {
      setErrorMessage('Adicione pelo menos uma fonte concluída ao bloco antes de gerar um simulado.')
      return
    }

    setGenerating(true)
    setErrorMessage(null)
    try {
      const generated = await request<unknown[]>(`/api/study-blocks/${blockId}/questions/generate-simulado`, {
        method: 'POST',
        body: JSON.stringify({
          quantity,
          difficulty,
          format,
          topicId: selectedTopicId || null,
        }),
      })
      await onSimuladoGenerated(generated.length)
      onClose()
    } catch (err) {
      setErrorMessage(
        err instanceof Error && err.message
          ? err.message
          : 'Não foi possível gerar o simulado. Verifique se o conteúdo das fontes é suficiente.',
      )
    } finally {
      setGenerating(false)
    }
  }

  const rootTopics = topics.filter((t) => t.parentTopicId === null)
  const subtopicsOf = (parentId: string) => topics.filter((t) => t.parentTopicId === parentId)

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="simulado-modal-title">
      <div className="card modal-panel">
        <div className="modal-header">
          <div>
            <p className="eyebrow">Simulado fundamentado nas fontes</p>
            <h2 id="simulado-modal-title">Configurar Simulado — {blockTitle}</h2>
          </div>
          <button
            type="button"
            className="secondary"
            onClick={onClose}
            disabled={generating}
          >
            Ver questões existentes
          </button>
        </div>

        <p className="subtitle">
          As questões serão elaboradas exclusivamente a partir das {activeSourceCount}{' '}
          {activeSourceCount === 1 ? 'fonte ativa' : 'fontes ativas'} deste bloco.
        </p>

        <form className="modal-form" onSubmit={handleSubmit}>
          <div className="question-meta-fields">
            <label>
              Quantidade de questões (1 a 10)
              <input
                type="number"
                min={1}
                max={10}
                required
                value={quantity}
                onChange={(e) => setQuantity(Math.max(1, Math.min(10, Number(e.target.value) || 1)))}
                disabled={generating}
              />
            </label>

            <label>
              Nível de dificuldade
              <select
                value={difficulty}
                onChange={(e) => setDifficulty(e.target.value as QuestionDifficulty)}
                disabled={generating}
              >
                <option value="EASY">Fácil (Definições e conceitos diretos)</option>
                <option value="INTERMEDIATE">Intermediário (Compreensão e aplicação)</option>
                <option value="HARD">Difícil (Análise aprofundada e síntese)</option>
              </select>
            </label>
          </div>

          <label>
            Formato das questões
            <select
              value={format}
              onChange={(e) => setFormat(e.target.value as QuestionFormat)}
              disabled={generating}
            >
              <option value="MULTIPLE_CHOICE">Múltipla escolha</option>
              <option value="TRUE_FALSE">Verdadeiro ou falso</option>
              <option value="DISCURSIVE">Discursiva (avaliação semântica por evidência)</option>
            </select>
          </label>

          <label>
            Assunto ou tópico (opcional)
            <select
              value={selectedTopicId}
              onChange={(e) => setSelectedTopicId(e.target.value)}
              disabled={generating || loadingTopics}
            >
              <option value="">Todos os assuntos disponíveis no bloco (Abrangência geral)</option>
              {rootTopics.map((root) => (
                <optgroup
                  key={root.id}
                  label={`${root.title}${root.origin === 'AI_EXTRACTED' ? ' (Mapeado das fontes)' : ''}`}
                >
                  <option value={root.id}>{root.title} (Assunto principal)</option>
                  {subtopicsOf(root.id).map((sub) => (
                    <option key={sub.id} value={sub.id}>
                      ↳ {sub.title}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
          </label>

          {!loadingTopics && topics.length === 0 && (
            <div className="process-status process-processing">
              <div>
                <strong>Nenhum tópico mapeado ainda para este bloco.</strong>
                <p>
                  {activeSourceCount > 0
                    ? 'Você pode gerar questões sobre todo o conteúdo do bloco agora ou extrair automaticamente os assuntos e tópicos das fontes.'
                    : 'Adicione fontes (arquivos, notas ou vídeos do YouTube) ao bloco para mapear tópicos e gerar simulados.'}
                </p>
              </div>
              {activeSourceCount > 0 && (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => void handleExtractTopics()}
                  disabled={extractingTopics || generating}
                >
                  {extractingTopics ? 'Mapeando...' : 'Mapear assuntos das fontes agora'}
                </button>
              )}
            </div>
          )}

          {errorMessage && (
            <div className="process-status process-error" role="alert">
              <div>
                <strong>Não foi possível iniciar o simulado</strong>
                <p>{errorMessage}</p>
              </div>
            </div>
          )}

          <div className="form-actions">
            <button type="submit" className="primary" disabled={generating || activeSourceCount === 0}>
              {generating ? 'Gerando simulado das fontes...' : `Gerar simulado (${quantity} questão(ões))`}
            </button>
            <button type="button" className="secondary" onClick={onClose} disabled={generating}>
              Cancelar
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
