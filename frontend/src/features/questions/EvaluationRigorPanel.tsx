import { useEffect, useState } from 'react'
import { getEvaluationSettings, updateEvaluationSettings } from '../../api/questions'
import '../../styles/pages/open-questions.css'
import type { EvaluationRigor } from '../../api/questions'

const options: { value: EvaluationRigor; title: string; description: string }[] = [
  {
    value: 'RIGOROSO',
    title: 'Rigoroso (padrão)',
    description:
      'CORRETA só com nota geral de 80 ou mais, todos os conceitos essenciais explicados e vocabulário tecnicamente ' +
      'equivalente ao do material (sinônimos contam).',
  },
  {
    value: 'MODERADO',
    title: 'Moderado',
    description:
      'CORRETA com nota geral de 80 ou mais e todos os conceitos essenciais. Explicar com outras palavras, sem os ' +
      'termos do material, não limita a PARCIAL.',
  },
]

/** Configurações › Correção das questões abertas: nível de rigor da IA. */
export function EvaluationRigorPanel() {
  const [rigor, setRigor] = useState<EvaluationRigor | null>(null)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    getEvaluationSettings()
      .then((settings) => {
        if (active) setRigor(settings.rigorLevel)
      })
      .catch(() => {
        if (active) setMessage('Não foi possível carregar o nível de rigor.')
      })
    return () => {
      active = false
    }
  }, [])

  const handleChange = async (value: EvaluationRigor) => {
    setSaving(true)
    setMessage(null)
    try {
      const saved = await updateEvaluationSettings(value)
      setRigor(saved.rigorLevel)
      setMessage('Nível de rigor salvo. Vale para as próximas respostas.')
    } catch (err) {
      setMessage(err instanceof Error && err.message ? err.message : 'Não foi possível salvar o nível de rigor.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="card mb-6">
      <h2 className="text-lg font-bold mb-2">Correção das questões abertas</h2>
      <p className="text-sm text-secondary mb-4">
        A IA compara sua resposta com a resposta-modelo e a rubrica do material, com foco na compreensão semântica e
        funcional (sinônimos equivalentes valem). A nota geral combina os critérios: 50% precisão conceitual e lógica,
        30% comportamento prático e casos de borda e 20% vocabulário técnico. A classificação (CORRETA ≥ 80, PARCIAL
        50–79, INCORRETA &lt; 50) é calculada pelo sistema conforme o nível escolhido. Sem provedor de IA, a resposta
        fica pendente e pode ser reavaliada depois.
      </p>
      <fieldset className="rigor-options" disabled={saving || rigor === null}>
        <legend>Nível de rigor</legend>
        {options.map((option) => (
          <label key={option.value} className={`rigor-option ${rigor === option.value ? 'is-selected' : ''}`}>
            <input
              type="radio"
              name="evaluation-rigor"
              value={option.value}
              checked={rigor === option.value}
              onChange={() => void handleChange(option.value)}
            />
            <span>
              <strong>{option.title}</strong>
              <span className="rigor-option-description">{option.description}</span>
            </span>
          </label>
        ))}
      </fieldset>
      {message && <p className="form-hint mt-2">{message}</p>}
    </div>
  )
}
