import type { StudyView } from '../../api/questions'
import '../../styles/pages/open-questions.css'

function ListSection({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null
  return (
    <section className="open-feedback-section">
      <h5>{title}</h5>
      <ul>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  )
}

/** Modo estudo: resposta correta, explicação e erros comuns, sem avaliação e sem contar na pontuação. */
export function StudyViewCard({ view, onClose }: { view: StudyView; onClose: () => void }) {
  return (
    <div className="study-view-card" role="region" aria-label="Modo estudo">
      <div className="open-feedback-header">
        <span className="badge badge-subtle">Modo estudo · não conta na pontuação</span>
      </div>

      {view.answer && (
        <section className="open-feedback-section">
          <h5>Resposta correta</h5>
          <p className="open-feedback-model">{view.answer}</p>
        </section>
      )}

      {view.explanation && (
        <section className="open-feedback-section">
          <h5>Explicação detalhada</h5>
          <p>{view.explanation}</p>
        </section>
      )}

      <ListSection title="Conceitos essenciais" items={view.essentialConcepts} />
      <ListSection title="Erros comuns sobre este tema" items={view.commonMistakes} />

      {view.sourceExcerpt && (
        <blockquote className="excerpt-blockquote">
          {view.sourceMaterialTitle && <strong>Fonte: {view.sourceMaterialTitle}</strong>}
          <p>
            {view.sourceExcerpt}
            {view.sourceTimestamp && ` (${view.sourceTimestamp})`}
          </p>
        </blockquote>
      )}

      <div className="open-feedback-actions">
        <button type="button" className="btn btn-ghost btn-sm" onClick={onClose}>
          Ocultar resposta
        </button>
      </div>
    </div>
  )
}
