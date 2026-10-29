// Cliente HTTP compartilhado pelas telas. As chamadas passam pelo proxy do Vite (/api → backend).
export async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!response.ok) {
    const body = await response.text()
    throw new Error(body || `Erro ${response.status}`)
  }
  return response.status === 204 ? (undefined as T) : response.json()
}
