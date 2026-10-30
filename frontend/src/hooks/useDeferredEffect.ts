import { useEffect } from 'react'
import type { DependencyList } from 'react'

/**
 * Como `useEffect`, mas executa `callback` numa microtarefa logo após o efeito (ainda antes da pintura da tela),
 * em vez de no corpo síncrono dele. Serve para carregamentos que começam com `setLoading(true)`: evita a cascata de
 * renderizações apontada pela regra `set-state-in-effect` sem mudar quando os dados são buscados. Se as
 * dependências mudarem ou o componente sair da tela antes da microtarefa, a chamada é cancelada.
 */
export function useDeferredEffect(callback: () => unknown, deps: DependencyList): void {
  useEffect(() => {
    let cancelled = false
    queueMicrotask(() => {
      if (!cancelled) void callback()
    })
    return () => {
      cancelled = true
    }
    // As dependências são as do chamador, como num useEffect comum.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
}
