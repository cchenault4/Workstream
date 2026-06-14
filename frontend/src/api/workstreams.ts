import type {
  ActivityEvent,
  CreateActivityEventRequest,
  CreateWorkstreamRequest,
  Plan,
  ReadinessState,
  UpdateWorkstreamRequest,
  UpsertPlanRequest,
  Workstream,
} from '../types/workstream'

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
  listWorkstreams: () =>
    request<Workstream[]>('/workstreams'),

  createWorkstream: (payload: CreateWorkstreamRequest) =>
    request<Workstream>('/workstreams', { method: 'POST', body: JSON.stringify(payload) }),

  getWorkstream: (id: string) =>
    request<Workstream>(`/workstreams/${id}`),

  updateWorkstream: (id: string, payload: UpdateWorkstreamRequest) =>
    request<Workstream>(`/workstreams/${id}`, { method: 'PATCH', body: JSON.stringify(payload) }),

  getPlan: (id: string) =>
    request<Plan>(`/workstreams/${id}/plan`),

  upsertPlan: (id: string, payload: UpsertPlanRequest) =>
    request<Plan>(`/workstreams/${id}/plan`, { method: 'PUT', body: JSON.stringify(payload) }),

  getReadiness: (id: string) =>
    request<ReadinessState>(`/workstreams/${id}/readiness`),

  getActivity: (id: string) =>
    request<ActivityEvent[]>(`/workstreams/${id}/activity`),

  addActivity: (id: string, payload: CreateActivityEventRequest) =>
    request<ActivityEvent>(`/workstreams/${id}/activity`, { method: 'POST', body: JSON.stringify(payload) }),
}
