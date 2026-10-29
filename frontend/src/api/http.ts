// Cliente HTTP compartilhado pelas telas. As chamadas passam pelo proxy do Vite (/api → backend).
export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const isFormData = options?.body instanceof FormData
  const headers: HeadersInit = isFormData
    ? { ...options?.headers }
    : { 'Content-Type': 'application/json', ...options?.headers }

  const response = await fetch(url, {
    ...options,
    headers,
  })

  if (!response.ok) {
    const rawBody = await response.text()
    let message = ''
    try {
      const parsed = JSON.parse(rawBody)
      message = parsed.message || parsed.error || rawBody
    } catch {
      message = rawBody
    }

    if (!message || message.trim() === '') {
      if (response.status === 503) {
        message = 'O serviço de IA está temporariamente indisponível. Verifique as credenciais no backend.'
      } else if (response.status === 429) {
        message = 'Limite de requisições excedido. Por favor, aguarde alguns instantes antes de tentar novamente.'
      } else if (response.status === 502) {
        message = 'O provedor de IA retornou uma resposta inválida ou indisponível.'
      } else if (response.status === 504) {
        message = 'Tempo limite esgotado ao aguardar resposta do provedor de IA.'
      } else {
        message = `Erro na requisição (HTTP ${response.status})`
      }
    }
    throw new ApiError(response.status, message)
  }

  return response.status === 204 ? (undefined as T) : response.json()
}
