import { useEffect, useState } from 'react'
import {
  getEvaluationMonitoring,
  runConsistencyCheck,
  type ConsistencyReport,
  type EvaluationMonitoringStats,
} from '../../api/questions'
import '../../styles/pages/open-questions.css'

const criteriaLabels: Record<string, string> = {
  conceptualPrecision: 'Precisão conceitual e lógica',
  practicalBehavior: 'Comportamento prático e casos de borda',
  technicalVocabulary: 'Vocabulário técnico',
  // Critérios do modelo anterior (médias das tentativas antigas).
  conceptualAccuracy: 'Exatidão conceitual',
  essentialCoverage: 'Conceitos essenciais',
  depth: 'Profundidade',
  terminology: 'Terminologia do material',
  noErrors: 'Sem erros ou contradições',
}

function percent(part: number, total: number): number {
  return total === 0 ? 0 : Math.round((part * 100) / total)
}

/**
 * Configurações › Monitoramento da correção: distribuição das classificações, notas médias e verificação de
 * consistência (reavalia tentativas recentes com o provedor atual, sem gravar nada).
 */
export function EvaluationMonitoringPanel() {
  const [stats, setStats] = useState<EvaluationMonitoringStats | null>(null)
  const [report, setReport] = useState<ConsistencyReport | null>(null)
  const [checking, setChecking] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    getEvaluationMonitoring()
      .then((data) => {
        if (active) setStats(data)
      })
      .catch(() => {
        if (active) setMessage('Não foi possível carregar as estatísticas da correção.')
      })
    return () => {
      active = false
    }
  }, [])

  const handleConsistencyCheck = async () => {
    setChecking(true)
    setMessage(null)
    setReport(null)
    try {
      setReport(await runConsistencyCheck(5))
    } catch (err) {
      setMessage(err instanceof Error && err.message ? err.message : 'Não foi possível verificar a consistência.')
    } finally {
      setChecking(false)
    }
  }

  return (
    <div className="card mb-6">
      <h2 className="text-lg font-bold mb-2">Monitoramento da correção</h2>
      <p className="text-sm text-secondary mb-4">
        Acompanhe se a correção está consistente: distribuição das classificações, notas médias por critério e uma
        verificação que reavalia respostas recentes com o provedor atual (nada é gravado — é só medição, útil ao
        trocar de modelo ou provedor).
      </p>

      {stats === null ? (
        <p className="text-sm text-muted">Carregando estatísticas…</p>
      ) : stats.graded === 0 ? (
        <p className="text-sm text-secondary">
          Ainda não há respostas corrigidas pela IA. Responda uma questão aberta para começar.
        </p>
      ) : (
        <>
          <dl className="open-feedback-criteria mb-2">
            <div>
              <dt>Respostas corrigidas</dt>
              <dd>{stats.graded}</dd>
            </div>
            <div>
              <dt>Nota média</dt>
              <dd>{stats.averageScore.toFixed(1)}/100</dd>
            </div>
            {stats.pending > 0 && (
              <div>
                <dt>Pendentes de avaliação</dt>
                <dd>{stats.pending}</dd>
              </div>
            )}
          </dl>
          <ul className="text-sm mb-4">
            {( [
              { label: 'Corretas', value: stats.correct },
              { label: 'Parciais', value: stats.partial },
              { label: 'Incorretas', value: stats.incorrect },
            ] ).map((row) => (
              <li key={row.label}>
                {row.label}: {row.value} ({percent(row.value, stats.graded)}%)
              </li>
            ))}
          </ul>
          {Object.keys(stats.averageCriteria).length > 0 && (
            <>
              <h3 className="text-sm font-bold mb-1">Média por critério</h3>
              <dl className="open-feedback-criteria mb-4">
                {Object.entries(stats.averageCriteria).map(([key, value]) => (
                  <div key={key}>
                    <dt>{criteriaLabels[key] ?? key}</dt>
                    <dd>{value.toFixed(1)}</dd>
                  </div>
                ))}
              </dl>
            </>
          )}
          {stats.blocks.length > 0 && (
            <>
              <h3 className="text-sm font-bold mb-1">Por bloco</h3>
              <ul className="text-sm mb-4">
                {stats.blocks.map((block) => (
                  <li key={block.blockTitle}>
                    {block.blockTitle}: {block.correct}/{block.attempts} corretas — média {block.averageScore.toFixed(1)}
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}

      <div className="open-feedback-actions">
        <button
          type="button"
          className="btn btn-secondary btn-sm"
          onClick={() => void handleConsistencyCheck()}
          disabled={checking || stats === null || stats.graded === 0}
        >
          {checking ? 'Verificando…' : 'Verificar consistência (5 recentes)'}
        </button>
      </div>
      <p className="form-hint mt-2">
        A verificação reavalia as 5 respostas corrigidas mais recentes com o provedor atual e compara com a
        classificação gravada. Consome chamadas de IA.
      </p>

      {report && (
        <div className="mt-4">
          <p className="text-sm mb-2">
            <strong>Concordância: {report.agreementPercent.toFixed(1)}%</strong> ({report.agreements} de{' '}
            {report.checked}
            {report.checked === 0 ? ' — nenhuma tentativa pôde ser reavaliada).' : ')'}
          </p>
          {report.items.filter((item) => !item.sameClassification).length > 0 && (
            <ul className="text-sm">
              {report.items
                .filter((item) => !item.sameClassification)
                .map((item) => (
                  <li key={item.prompt}>
                    “{item.prompt}” — era {item.storedClassification} ({item.storedScore}) e agora daria{' '}
                    {item.newClassification} ({item.newScore})
                  </li>
                ))}
            </ul>
          )}
          {report.checked > 0 && report.agreements === report.checked && (
            <p className="text-sm text-secondary">A correção atual está de acordo com todo o histórico amostrado.</p>
          )}
        </div>
      )}
      {message && <p className="form-hint mt-2">{message}</p>}
    </div>
  )
}
