import type { ActivityType, PhaseStatus, Priority, QuestionType, WorkstreamStatus } from '../types/workstream'

export const PRIORITY_CLASS: Record<Priority, string> = {
  LOW: 'badge-low', MEDIUM: 'badge-medium', HIGH: 'badge-high',
}

export const STATUS_CLASS: Record<WorkstreamStatus, string> = {
  NEW: 'badge-new', PLANNING: 'badge-planning', EXECUTING: 'badge-executing',
  REVIEWING: 'badge-reviewing', VERIFIED: 'badge-verified', BLOCKED: 'badge-blocked',
}

export const QUESTION_TYPE_CLASS: Record<QuestionType, string> = {
  BLOCKING: 'badge-blocked', ASSUMABLE: 'badge-planning', DEFERRABLE: 'badge-low',
}

export const PHASE_CLASS: Record<PhaseStatus, string> = {
  PENDING: 'badge-low', IN_PROGRESS: 'badge-executing', COMPLETE: 'badge-verified', BLOCKED: 'badge-blocked',
}

export const ACTIVITY_CLASS: Record<ActivityType, string> = {
  CONTEXT_DISCOVERY: 'badge-new', PLANNING: 'badge-planning', IMPLEMENTATION: 'badge-executing',
  REVIEW: 'badge-reviewing', VERIFICATION: 'badge-verified', HANDOFF: 'badge-low',
}
