export type Priority = 'LOW' | 'MEDIUM' | 'HIGH'

export type WorkstreamStatus =
  | 'NEW'
  | 'PLANNING'
  | 'EXECUTING'
  | 'REVIEWING'
  | 'VERIFIED'
  | 'BLOCKED'

export interface Workstream {
  id: string
  title: string
  description: string
  requester: string
  priority: Priority
  status: WorkstreamStatus
  createdAt: string
  updatedAt: string
}

export interface CreateWorkstreamRequest {
  title: string
  description: string
  requester: string
  priority: Priority
}
