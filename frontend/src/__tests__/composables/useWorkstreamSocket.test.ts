import { describe, it, expect, vi, beforeEach } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { useWorkstreamSocket, useWorkstreamsSocket } from '../../composables/useWorkstreamSocket'
import type { ActivityEvent, Plan, Workstream } from '../../types/workstream'

// ── Mock the singleton socket module ─────────────────────────────────────────

vi.mock('../../composables/socket', () => ({
  sendWhenReady: vi.fn(),
  addHandler: vi.fn(() => vi.fn()),   // returns a no-op remover by default
  teardownSocket: vi.fn(),
}))

import { sendWhenReady, addHandler } from '../../composables/socket'

// ── Fixtures ──────────────────────────────────────────────────────────────────

const workstream: Workstream = {
  id: 'ws-1', title: 'Test', description: 'Desc', requester: 'alice',
  priority: 'HIGH', status: 'NEW', active: false,
  createdAt: '2025-01-01T00:00:00Z', updatedAt: '2025-01-01T00:00:00Z',
}

const activityEvent: ActivityEvent = {
  id: 'e1', workstreamId: 'ws-1', agentName: 'Bot',
  type: 'PLANNING', message: 'hello', createdAt: '2025-01-01T00:00:00Z',
}

const plan: Plan = {
  workstreamId: 'ws-1', goal: 'Ship it', nonGoals: [], assumptions: [],
  openQuestions: [], phases: [], verificationPlan: [],
}

// Helper: mount a component that calls the composable and capture the registered handler
function captureHandler(): { handler: ((msg: { type: string; data: unknown }) => void) | null } {
  const ref = { handler: null as ((msg: { type: string; data: unknown }) => void) | null }
  vi.mocked(addHandler).mockImplementation((h) => {
    ref.handler = h
    return vi.fn()
  })
  return ref
}

// ── useWorkstreamSocket ───────────────────────────────────────────────────────

describe('useWorkstreamSocket', () => {
  beforeEach(() => {
    vi.mocked(sendWhenReady).mockClear()
    vi.mocked(addHandler).mockClear()
  })

  function mountSocket(handlers: Parameters<typeof useWorkstreamSocket>[1]) {
    return mount(defineComponent({
      setup() { useWorkstreamSocket('ws-1', handlers) },
      template: '<div />',
    }))
  }

  it('sends workstream:join on mount', () => {
    mountSocket({})
    expect(sendWhenReady).toHaveBeenCalledWith({ type: 'workstream:join', workstreamId: 'ws-1' })
  })

  it('sends workstream:leave on unmount', () => {
    const wrapper = mountSocket({})
    wrapper.unmount()
    expect(sendWhenReady).toHaveBeenCalledWith({ type: 'workstream:leave', workstreamId: 'ws-1' })
  })

  it('calls onActivity when activity:created message arrives', async () => {
    const onActivity = vi.fn()
    const ref = captureHandler()
    mountSocket({ onActivity })
    ref.handler?.({ type: 'activity:created', data: activityEvent })
    await nextTick()
    expect(onActivity).toHaveBeenCalledWith(activityEvent)
  })

  it('calls onWorkstreamUpdated when workstream:updated message arrives', async () => {
    const onWorkstreamUpdated = vi.fn()
    const ref = captureHandler()
    mountSocket({ onWorkstreamUpdated })
    ref.handler?.({ type: 'workstream:updated', data: workstream })
    await nextTick()
    expect(onWorkstreamUpdated).toHaveBeenCalledWith(workstream)
  })

  it('calls onPlanUpdated when plan:updated message arrives', async () => {
    const onPlanUpdated = vi.fn()
    const ref = captureHandler()
    mountSocket({ onPlanUpdated })
    ref.handler?.({ type: 'plan:updated', data: plan })
    await nextTick()
    expect(onPlanUpdated).toHaveBeenCalledWith(plan)
  })

  it('ignores unknown message types', async () => {
    const onActivity = vi.fn()
    const ref = captureHandler()
    mountSocket({ onActivity })
    ref.handler?.({ type: 'something:else', data: {} })
    await nextTick()
    expect(onActivity).not.toHaveBeenCalled()
  })

  it('ignores messages with null data', async () => {
    const onActivity = vi.fn()
    const ref = captureHandler()
    mountSocket({ onActivity })
    ref.handler?.({ type: 'activity:created', data: null })
    await nextTick()
    expect(onActivity).not.toHaveBeenCalled()
  })

  it('removes the handler on unmount', () => {
    const mockRemover = vi.fn()
    vi.mocked(addHandler).mockReturnValue(mockRemover)
    const wrapper = mountSocket({})
    wrapper.unmount()
    expect(mockRemover).toHaveBeenCalled()
  })
})

// ── useWorkstreamsSocket ──────────────────────────────────────────────────────

describe('useWorkstreamsSocket', () => {
  beforeEach(() => {
    vi.mocked(sendWhenReady).mockClear()
    vi.mocked(addHandler).mockClear()
  })

  function mountSocket(handlers?: Parameters<typeof useWorkstreamsSocket>[0]) {
    return mount(defineComponent({
      setup() { useWorkstreamsSocket(handlers) },
      template: '<div />',
    }))
  }

  it('sends workstreams:subscribe on mount', () => {
    mountSocket()
    expect(sendWhenReady).toHaveBeenCalledWith({ type: 'workstreams:subscribe' })
  })

  it('calls onWorkstreamUpdated when workstream:updated message arrives', async () => {
    const onWorkstreamUpdated = vi.fn()
    const ref = captureHandler()
    mountSocket({ onWorkstreamUpdated })
    ref.handler?.({ type: 'workstream:updated', data: workstream })
    await nextTick()
    expect(onWorkstreamUpdated).toHaveBeenCalledWith(workstream)
  })

  it('does not call handler for other event types', async () => {
    const onWorkstreamUpdated = vi.fn()
    const ref = captureHandler()
    mountSocket({ onWorkstreamUpdated })
    ref.handler?.({ type: 'activity:created', data: activityEvent })
    await nextTick()
    expect(onWorkstreamUpdated).not.toHaveBeenCalled()
  })

  it('removes the handler on unmount', () => {
    const mockRemover = vi.fn()
    vi.mocked(addHandler).mockReturnValue(mockRemover)
    const wrapper = mountSocket({})
    wrapper.unmount()
    expect(mockRemover).toHaveBeenCalled()
  })
})
