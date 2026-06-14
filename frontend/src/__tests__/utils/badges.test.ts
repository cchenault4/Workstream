import { describe, it, expect } from 'vitest'
import {
  PRIORITY_CLASS,
  STATUS_CLASS,
  QUESTION_TYPE_CLASS,
  PHASE_CLASS,
  ACTIVITY_CLASS,
} from '../../utils/badges'

describe('PRIORITY_CLASS', () => {
  it('maps every priority to a class', () => {
    expect(PRIORITY_CLASS.LOW).toBe('badge-low')
    expect(PRIORITY_CLASS.MEDIUM).toBe('badge-medium')
    expect(PRIORITY_CLASS.HIGH).toBe('badge-high')
  })
})

describe('STATUS_CLASS', () => {
  it('maps every workstream status to a class', () => {
    expect(PRIORITY_CLASS.LOW).toBeDefined()
    expect(STATUS_CLASS.NEW).toBe('badge-new')
    expect(STATUS_CLASS.PLANNING).toBe('badge-planning')
    expect(STATUS_CLASS.EXECUTING).toBe('badge-executing')
    expect(STATUS_CLASS.REVIEWING).toBe('badge-reviewing')
    expect(STATUS_CLASS.VERIFIED).toBe('badge-verified')
    expect(STATUS_CLASS.BLOCKED).toBe('badge-blocked')
  })
})

describe('QUESTION_TYPE_CLASS', () => {
  it('maps every question type to a class', () => {
    expect(QUESTION_TYPE_CLASS.BLOCKING).toBeDefined()
    expect(QUESTION_TYPE_CLASS.ASSUMABLE).toBeDefined()
    expect(QUESTION_TYPE_CLASS.DEFERRABLE).toBeDefined()
  })
})

describe('PHASE_CLASS', () => {
  it('maps every phase status to a class', () => {
    expect(PHASE_CLASS.PENDING).toBeDefined()
    expect(PHASE_CLASS.IN_PROGRESS).toBeDefined()
    expect(PHASE_CLASS.COMPLETE).toBeDefined()
    expect(PHASE_CLASS.BLOCKED).toBeDefined()
  })
})

describe('ACTIVITY_CLASS', () => {
  it('maps every activity type to a class', () => {
    expect(ACTIVITY_CLASS.CONTEXT_DISCOVERY).toBeDefined()
    expect(ACTIVITY_CLASS.PLANNING).toBeDefined()
    expect(ACTIVITY_CLASS.IMPLEMENTATION).toBeDefined()
    expect(ACTIVITY_CLASS.REVIEW).toBeDefined()
    expect(ACTIVITY_CLASS.VERIFICATION).toBeDefined()
    expect(ACTIVITY_CLASS.HANDOFF).toBeDefined()
  })
})
