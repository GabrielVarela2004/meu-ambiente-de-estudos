import { useState } from 'react'
import type { FormEvent } from 'react'
import { request } from '../../api/http'

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
  // Área teórica: só questões abertas (o backend também força o formato).
  const [generating, setGenerating] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (!isOpen) return null

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
          format: 'DISCURSIVE' satisfies QuestionFormat,
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

          <p className="form-hint">
            As questões teóricas são sempre <strong>abertas</strong>: você responde com suas palavras e a IA corrige
            com rigor, comparando com a resposta-modelo e os conceitos essenciais do material.
          </p>

          <p className="form-hint">As questões cobrem todo o conteúdo das fontes ativas do bloco.</p>

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
