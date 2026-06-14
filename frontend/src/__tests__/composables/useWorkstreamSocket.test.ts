import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { MockWebSocket } from '../mocks/websocket'
import { useWorkstreamSocket, useWorkstreamsSocket } from '../../composables/useWorkstreamSocket'
import type { ActivityEvent, Plan, Workstream } from '../../types/workstream'

vi.stubGlobal('WebSocket', MockWebSocket)

beforeEach(() => MockWebSocket.reset())
afterEach(() => vi.restoreAllMocks())

// Mount a component that calls the composable so onUnmounted lifecycle works
function mountWorkstreamSocket(workstreamId: string, handlers: Parameters<typeof useWorkstreamSocket>[1]) {
  return mount(defineComponent({
    setup() { return useWorkstreamSocket(workstreamId, handlers) },
    template: '<div />',
  }))
}

function mountWorkstreamsSocket(handlers?: Parameters<typeof useWorkstreamsSocket>[0]) {
  let result: ReturnType<typeof useWorkstreamsSocket>
  const wrapper = mount(defineComponent({
    setup() {
      result = useWorkstreamsSocket(handlers)
      return result
    },
    template: '<div />',
  }))
  return { wrapper, get result() { return result } }
}

// ── useWorkstreamSocket ───────────────────────────────────────────────────────

describe('useWorkstreamSocket', () => {
  it('sends workstream:join on open', () => {
    mountWorkstreamSocket('ws-1', {})
    const ws = MockWebSocket.latest()
    ws.simulateOpen()
    expect(ws.sent).toHaveLength(1)
    expect(JSON.parse(ws.sent[0])).toEqual({ type: 'workstream:join', workstreamId: 'ws-1' })
  })

  it('sends workstream:leave and closes on unmount', () => {
    const wrapper = mountWorkstreamSocket('ws-1', {})
    const ws = MockWebSocket.latest()
    ws.simulateOpen()
    wrapper.unmount()
    const messages = ws.sent.map(s => JSON.parse(s))
    expect(messages.some(m => m.type === 'workstream:leave')).toBe(true)
    expect(ws.readyState).toBe(MockWebSocket.CLOSED)
  })

  it('dispatches activity:created to onActivity handler', async () => {
    const onActivity = vi.fn()
    mountWorkstreamSocket('ws-1', { onActivity })
    const ws = MockWebSocket.latest()
    ws.simulateOpen()

    const event: ActivityEvent = {
      id: 'e1', workstreamId: 'ws-1', agentName: 'Bot',
      type: 'PLANNING', message: 'hello', createdAt: new Date().toISOString(),
    }
    ws.simulateMessage({ type: 'activity:created', data: event })
    await nextTick()
    expect(onActivity).toHaveBeenCalledWith(event)
  })

  it('dispatches workstream:updated to onWorkstreamUpdated handler', async () => {
    const onWorkstreamUpdated = vi.fn()
    mountWorkstreamSocket('ws-1', { onWorkstreamUpdated })
    const ws = MockWebSocket.latest()
    ws.simulateOpen()

    const workstream = { id: 'ws-1', title: 'Updated' } as Workstream
    ws.simulateMessage({ type: 'workstream:updated', data: workstream })
    await nextTick()
    expect(onWorkstreamUpdated).toHaveBeenCalledWith(workstream)
  })

  it('dispatches plan:updated to onPlanUpdated handler', async () => {
    const onPlanUpdated = vi.fn()
    mountWorkstreamSocket('ws-1', { onPlanUpdated })
    const ws = MockWebSocket.latest()
    ws.simulateOpen()

    const plan = { workstreamId: 'ws-1', goal: 'Ship it' } as Plan
    ws.simulateMessage({ type: 'plan:updated', data: plan })
    await nextTick()
    expect(onPlanUpdated).toHaveBeenCalledWith(plan)
  })

  it('silently ignores malformed JSON frames', () => {
    mountWorkstreamSocket('ws-1', {})
    const ws = MockWebSocket.latest()
    ws.simulateOpen()
    expect(() => {
      ws.onmessage?.(new MessageEvent('message', { data: 'not-json' }))
    }).not.toThrow()
  })

  it('ignores frames with missing type or data fields', async () => {
    const onActivity = vi.fn()
    mountWorkstreamSocket('ws-1', { onActivity })
    const ws = MockWebSocket.latest()
    ws.simulateOpen()
    ws.simulateMessage({ type: 'activity:created' }) // missing data
    ws.simulateMessage({ data: { id: 'e1' } })       // missing type
    await nextTick()
    expect(onActivity).not.toHaveBeenCalled()
  })
})

// ── useWorkstreamsSocket ──────────────────────────────────────────────────────

describe('useWorkstreamsSocket', () => {
  it('sends workstreams:subscribe on open', () => {
    mountWorkstreamsSocket()
    const ws = MockWebSocket.latest()
    ws.simulateOpen()
    expect(JSON.parse(ws.sent[0])).toEqual({ type: 'workstreams:subscribe' })
  })

  it('adds workstream id to activeWorkstreamIds on active presence message', async () => {
    const { result } = mountWorkstreamsSocket()
    const ws = MockWebSocket.latest()
    ws.simulateOpen()
    ws.simulateMessage({ type: 'workstream:presence', data: { workstreamId: 'ws-1', active: true } })
    await nextTick()
    expect(result.activeWorkstreamIds.value).toContain('ws-1')
  })

  it('removes workstream id from activeWorkstreamIds on inactive presence message', async () => {
    const { result } = mountWorkstreamsSocket()
    const ws = MockWebSocket.latest()
    ws.simulateOpen()
    ws.simulateMessage({ type: 'workstream:presence', data: { workstreamId: 'ws-1', active: true } })
    await nextTick()
    ws.simulateMessage({ type: 'workstream:presence', data: { workstreamId: 'ws-1', active: false } })
    await nextTick()
    expect(result.activeWorkstreamIds.value).not.toContain('ws-1')
  })

  it('does not add duplicate ids to activeWorkstreamIds', async () => {
    const { result } = mountWorkstreamsSocket()
    const ws = MockWebSocket.latest()
    ws.simulateOpen()
    ws.simulateMessage({ type: 'workstream:presence', data: { workstreamId: 'ws-1', active: true } })
    ws.simulateMessage({ type: 'workstream:presence', data: { workstreamId: 'ws-1', active: true } })
    await nextTick()
    expect(result.activeWorkstreamIds.value.filter(id => id === 'ws-1')).toHaveLength(1)
  })

  it('calls onWorkstreamUpdated handler on workstream:updated message', async () => {
    const onWorkstreamUpdated = vi.fn()
    mountWorkstreamsSocket({ onWorkstreamUpdated })
    const ws = MockWebSocket.latest()
    ws.simulateOpen()
    const workstream = { id: 'ws-1', title: 'New Title' } as Workstream
    ws.simulateMessage({ type: 'workstream:updated', data: workstream })
    await nextTick()
    expect(onWorkstreamUpdated).toHaveBeenCalledWith(workstream)
  })

  it('closes socket on unmount', () => {
    const { wrapper } = mountWorkstreamsSocket()
    const ws = MockWebSocket.latest()
    ws.simulateOpen()
    wrapper.unmount()
    expect(ws.readyState).toBe(MockWebSocket.CLOSED)
  })
})
