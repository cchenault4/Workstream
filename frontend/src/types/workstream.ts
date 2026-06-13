export type Priority = 'LOW' | 'MEDIUM' | 'HIGH'

export type WorkstreamStatus =
  | 'NEW'
  | 'PLANNING'
  | 'EXECUTING'
  | 'REVIEWING'
  | 'VERIFIED'
  | 'BLOCKED'

export type PhaseStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETE' | 'BLOCKED'

export type QuestionType = 'BLOCKING' | 'ASSUMABLE' | 'DEFERRABLE'

export type ActivityType =
  | 'CONTEXT_DISCOVERY'
  | 'PLANNING'
  | 'IMPLEMENTATION'
  | 'REVIEW'
  | 'VERIFICATION'
  | 'HANDOFF'

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

export interface Phase {
  id: string
  name: string
  objective: string
  status: PhaseStatus
}

export interface OpenQuestion {
  id: string
  question: string
  type: QuestionType
  resolution?: string
}

export interface Plan {
  workstreamId: string
  goal: string
  nonGoals: string[]
  assumptions: string[]
  openQuestions: OpenQuestion[]
  phases: Phase[]
  verificationPlan: string[]
}

export interface ReadinessState {
  blockingQuestionsResolved: boolean
  allPhasesComplete: boolean
  verificationReady: boolean
  readyForReview: boolean
  readyForPR: boolean
}

export interface ActivityEvent {
  id: string
  workstreamId: string
  agentName: string
  type: ActivityType
  message: string
  createdAt: string
}

export interface CreateWorkstreamRequest {
  title: string
  description: string
  requester: string
  priority: Priority
}

export interface UpdateWorkstreamRequest {
  status?: WorkstreamStatus
  title?: string
  description?: string
  priority?: Priority
}

export interface UpsertPlanRequest {
  goal: string
  nonGoals: string[]
  assumptions: string[]
  openQuestions: OpenQuestion[]
  phases: Phase[]
  verificationPlan: string[]
}

export interface CreateActivityEventRequest {
  agentName: string
  type: ActivityType
  message: string
}
