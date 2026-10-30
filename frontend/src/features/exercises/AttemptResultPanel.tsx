import { CheckCircleIcon } from '../../components/icons'

type ReportTest = { name: string; passed: boolean; mandatory: boolean; message?: string }
type ReportCheck = { type: string; description: string; passed: boolean; detail?: string }
type ReportIssue = { rule: string; severity: 'MAJOR' | 'MINOR'; message: string; line: number }

export type AttemptResult = {
  selfRating: number
  scorePercent: number
  autonomyPercent: number | null
  hintsUsedCount: number
  solutionRevealed: boolean
  passed: boolean
  evaluationStatus: string
  correctnessPercent: number | null
  conceptPercent: number | null
  qualityPercent: number | null
  testsTotal: number
  testsPassed: number
  report: {
    status?: string
    message?: string
    compileErrors?: string[]
    tests?: ReportTest[]
    checks?: ReportCheck[]
    quality?: ReportIssue[]
  } | null
}

const statusLabels: Record<string, string> = {
  PASSED: 'Aprovado',
  PARTIAL: 'Parcial',
  FAILED: 'Reprovado',
  COMPILATION_ERROR: 'Erro de compilação',
  RUNTIME_ERROR: 'Erro de execução',
  TIMEOUT: 'Tempo esgotado',
  SELF_RATED: 'Autoavaliação',
  UNGRADABLE: 'Não avaliável',
}

function ScoreBar({ label, value, weight }: { label: string; value: number | null; weight: string }) {
  return (
    <div className="attempt-score-row">
      <span className="text-xs">
        {label} <span className="text-muted">({weight})</span>
      </span>
      <div className="attempt-score-track" aria-hidden="true">
        <span style={{ width: `${value ?? 0}%` }} />
      </div>
      <span className="text-xs attempt-score-value">{value === null ? '—' : `${value}%`}</span>
    </div>
  )
}

export function AttemptResultPanel({ result }: { result: AttemptResult }) {
  const automatic = result.evaluationStatus !== 'SELF_RATED'
  const report = result.report
  const hasConcept = result.conceptPercent !== null

  return (
    <div className={`exercise-feedback-panel ${result.passed ? 'passed' : 'needs-review'}`}>
      <div className="flex-between mb-2">
        <div className="flex-gap-center">
          <CheckCircleIcon className="w-5 h-5 text-emerald-400" />
          <strong>{automatic ? 'Correção automática' : 'Tentativa registrada'}</strong>
          <span className="badge badge-subtle">{statusLabels[result.evaluationStatus] ?? result.evaluationStatus}</span>
        </div>
        <span className={`badge ${result.passed ? 'badge-emerald' : 'badge-amber'}`}>Nota: {result.scorePercent}%</span>
      </div>

      {automatic ? (
        <div className="attempt-score-grid">
          <ScoreBar label="Corretude (testes)" value={result.correctnessPercent} weight={hasConcept ? '50%' : '75%'} />
          {hasConcept && <ScoreBar label="Conceito" value={result.conceptPercent} weight="25%" />}
          <ScoreBar label="Boas práticas" value={result.qualityPercent} weight="25%" />
          <ScoreBar label="Autonomia" value={result.autonomyPercent ?? 100} weight="multiplica a nota" />
        </div>
      ) : (
        <p className="text-sm">
          Autoavaliação {result.selfRating}/5 × autonomia {result.autonomyPercent ?? 100}%. Este exercício não teve
          correção automática (assunto sem testes ou ambiente de execução indisponível).
        </p>
      )}

      {result.hintsUsedCount > 0 && (
        <p className="text-sm">{result.hintsUsedCount} ajuda(s) da tutora entraram no cálculo da autonomia.</p>
      )}
      {result.solutionRevealed && (
        <p className="text-sm">A solução foi revelada: esta tentativa não conta para o domínio do tópico.</p>
      )}

      {report?.compileErrors && report.compileErrors.length > 0 && (
        <details open className="attempt-details">
          <summary>Erros de compilação</summary>
          <ul>
            {report.compileErrors.map((e, i) => (
              <li key={i} className="font-mono text-xs">{e}</li>
            ))}
          </ul>
        </details>
      )}
      {result.evaluationStatus === 'TIMEOUT' && (
        <p className="text-sm">O código não terminou dentro do tempo limite. Verifique laços infinitos.</p>
      )}

      {report?.tests && report.tests.length > 0 && (
        <details open className="attempt-details">
          <summary>
            Testes: {result.testsPassed} de {result.testsTotal} passaram
          </summary>
          <ul>
            {report.tests.map((t, i) => (
              <li key={i} className={t.passed ? 'attempt-ok' : 'attempt-fail'}>
                {t.passed ? '✓' : '✗'} {t.name}
                {t.mandatory && <span className="badge badge-subtle">obrigatório</span>}
                {!t.passed && t.message && <div className="text-xs text-muted">{t.message}</div>}
              </li>
            ))}
          </ul>
        </details>
      )}

      {report?.checks && report.checks.length > 0 && (
        <details open className="attempt-details">
          <summary>Aplicação do conceito</summary>
          <ul>
            {report.checks.map((c, i) => (
              <li key={i} className={c.passed ? 'attempt-ok' : 'attempt-fail'}>
                {c.passed ? '✓' : '✗'} {c.description}
                {!c.passed && c.detail && <div className="text-xs text-muted">{c.detail}</div>}
              </li>
            ))}
          </ul>
        </details>
      )}

      {report?.quality && (
        <details open={report.quality.length > 0} className="attempt-details">
          <summary>
            Boas práticas: {report.quality.length === 0 ? 'nenhum problema encontrado' : `${report.quality.length} ponto(s) a melhorar`}
          </summary>
          <ul>
            {report.quality.map((q, i) => (
              <li key={i} className="attempt-fail">
                <span className={`badge ${q.severity === 'MAJOR' ? 'badge-danger' : 'badge-warning'}`}>
                  {q.severity === 'MAJOR' ? 'grave' : 'leve'}
                </span>{' '}
                {q.line > 0 && <span className="text-xs text-muted">linha {q.line} · </span>}
                {q.message}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  )
}
