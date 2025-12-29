import type { ApiErrorResponse, SearchFilters, WorkDetail, WorkSummary } from './types'

const apiBase = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '')

const withBase = (path: string) => {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${apiBase}${normalized}`
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(withBase(path), {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...(init?.headers ?? {}),
    },
  })

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body: ApiErrorResponse = await response.json()
      if (body.message) message = body.message
      else if (body.error) message = body.error
    } catch (_) {
      // keep default message
    }
    throw new Error(message)
  }

  try {
    return (await response.json()) as T
  } catch (error) {
    throw new Error('Invalid JSON response from server')
  }
}

export async function searchWorks(filters: SearchFilters = {}): Promise<WorkSummary[]> {
  const params = new URLSearchParams()
  if (filters.workId?.trim()) params.set('workId', filters.workId.trim())
  if (filters.model?.trim()) params.set('model', filters.model.trim())
  if (filters.serial?.trim()) params.set('serial', filters.serial.trim())
  if (filters.process?.trim()) params.set('process', filters.process.trim())
  if (filters.from?.trim()) params.set('from', filters.from.trim())
  if (filters.to?.trim()) params.set('to', filters.to.trim())

  const query = params.toString()
  const path = query ? `/api/works?${query}` : '/api/works'
  return request<WorkSummary[]>(path)
}

export async function getWork(workId: string): Promise<WorkDetail> {
  const encoded = encodeURIComponent(workId)
  return request<WorkDetail>(`/api/works/${encoded}`)
}

export const segmentStreamUrl = (segmentId: string) => withBase(`/api/segments/${segmentId}/stream`)
export const segmentDownloadUrl = (segmentId: string) => withBase(`/api/segments/${segmentId}/download`)
