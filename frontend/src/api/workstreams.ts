import type { CreateWorkstreamRequest, Workstream } from '../types/workstream'

const BASE = '/api'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }))
    throw new Error(body.error ?? `HTTP ${res.status}`)
  }
  return res.json()
}

export const workstreamsApi = {
  list: () => request<Workstream[]>('/workstreams'),
  create: (payload: CreateWorkstreamRequest) =>
    request<Workstream>('/workstreams', { method: 'POST', body: JSON.stringify(payload) }),
}
