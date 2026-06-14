import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import WorkstreamDetailView from '../../views/WorkstreamDetailView.vue'
import type { ActivityEvent, Plan, ReadinessState, Workstream } from '../../types/workstream'

// ── Mocks ─────────────────────────────────────────────────────────────────────

const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'ws-1' } }),
  useRouter: () => ({ push: mockPush }),
}))

// Capture WS handlers so tests can simulate real-time events
type DetailHandlers = {
  onActivity?: (e: ActivityEvent) => void
  onWorkstreamUpdated?: (ws: Workstream) => void
  onPlanUpdated?: (p: Plan) => void
}
let capturedHandlers: DetailHandlers = {}

vi.mock('../../composables/useWorkstreamSocket', () => ({
  useWorkstreamSocket: vi.fn((_id: string, handlers: DetailHandlers) => {
    capturedHandlers = handlers
    return { connected: { value: true } }
  }),
  useWorkstreamsSocket: vi.fn(() => ({ activeWorkstreamIds: { value: [] } })),
}))

vi.mock('../../api/workstreams', () => ({
  workstreamsApi: {
    getWorkstream: vi.fn(),
    getPlan: vi.fn(),
    getReadiness: vi.fn(),
    getActivity: vi.fn(),
    updateWorkstream: vi.fn(),
    upsertPlan: vi.fn(),
    addActivity: vi.fn(),
  },
}))

// ── Fixtures ──────────────────────────────────────────────────────────────────

import { workstreamsApi } from '../../api/workstreams'

const workstream: Workstream = {
  id: 'ws-1', title: 'Auth service', description: 'Add JWT auth', requester: 'alice',
  priority: 'HIGH', status: 'EXECUTING',
  createdAt: '2025-03-01T10:00:00Z', updatedAt: '2025-03-01T10:00:00Z',
}

const plan: Plan = {
  workstreamId: 'ws-1',
  goal: 'Ship JWT authentication',
  nonGoals: ['No SSO'],
  assumptions: ['Postgres available'],
  openQuestions: [],
  phases: [{ id: 'p1', name: 'Implementation', objective: 'Write the code', status: 'COMPLETE' }],
  verificationPlan: ['Run integration tests'],
}

const readiness: ReadinessState = {
  blockingQuestionsResolved: true,
  allPhasesComplete: true,
  verificationReady: true,
  readyForReview: false,
  readyForPR: false,
}

const activity1: ActivityEvent = {
  id: 'e1', workstreamId: 'ws-1', agentName: 'Context Scout',
  type: 'CONTEXT_DISCOVERY', message: 'Found 42 files',
  createdAt: '2025-03-01T11:00:00Z',
}

const activity2: ActivityEvent = {
  id: 'e2', workstreamId: 'ws-1', agentName: 'Plan Critic',
  type: 'PLANNING', message: 'Plan looks good',
  createdAt: '2025-03-01T12:00:00Z',
}

function mountView() {
  return mount(WorkstreamDetailView, {
    global: { mocks: { $router: { push: mockPush } } },
  })
}

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.mocked(workstreamsApi.getWorkstream).mockResolvedValue(workstream)
  vi.mocked(workstreamsApi.getPlan).mockResolvedValue(plan)
  vi.mocked(workstreamsApi.getReadiness).mockResolvedValue(readiness)
  vi.mocked(workstreamsApi.getActivity).mockResolvedValue([activity1, activity2])
  vi.mocked(workstreamsApi.updateWorkstream).mockResolvedValue(workstream)
  vi.mocked(workstreamsApi.upsertPlan).mockResolvedValue(plan)
  vi.mocked(workstreamsApi.addActivity).mockResolvedValue(activity1)
  capturedHandlers = {}
  mockPush.mockClear()
})

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('WorkstreamDetailView', () => {
  // Loading state
  it('shows loading state before data arrives', () => {
    vi.mocked(workstreamsApi.getWorkstream).mockReturnValue(new Promise(() => {}))
    const wrapper = mountView()
    expect(wrapper.text()).toContain('Loading')
  })

  it('renders workstream title after load', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Auth service')
  })

  it('shows 404 state when workstream does not exist', async () => {
    vi.mocked(workstreamsApi.getWorkstream).mockRejectedValue(new Error('Not found'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('not found')
  })

  // Tab navigation
  it('defaults to the Implementation Plan tab', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('.tab-btn.active').text()).toBe('Implementation Plan')
    expect(wrapper.text()).toContain('Ship JWT authentication')
  })

  it('switches to the Readiness tab on click', async () => {
    const wrapper = mountView()
    await flushPromises()
    const tabs = wrapper.findAll('.tab-btn')
    await tabs.find(t => t.text() === 'Readiness')!.trigger('click')
    expect(wrapper.find('.tab-btn.active').text()).toBe('Readiness')
    expect(wrapper.text()).toContain('All phases complete')
    expect(wrapper.text()).toContain('Blocking questions resolved')
  })

  it('switches to the Activity tab on click', async () => {
    const wrapper = mountView()
    await flushPromises()
    const tabs = wrapper.findAll('.tab-btn')
    await tabs.find(t => t.text() === 'Activity')!.trigger('click')
    expect(wrapper.find('.tab-btn.active').text()).toBe('Activity')
    expect(wrapper.text()).toContain('Context Scout')
    expect(wrapper.text()).toContain('Plan Critic')
  })

  // Plan tab content
  it('displays plan goal in Implementation Plan tab', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Ship JWT authentication')
  })

  it('displays non-goals in plan', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('No SSO')
  })

  it('displays phase name and objective', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Implementation')
    expect(wrapper.text()).toContain('Write the code')
  })

  it('shows empty plan state when no plan exists', async () => {
    vi.mocked(workstreamsApi.getPlan).mockRejectedValue(new Error('Not found'))
    vi.mocked(workstreamsApi.getReadiness).mockRejectedValue(new Error('Not found'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('No implementation plan yet')
  })

  // Readiness tab content
  it('shows ✓ for met gates and ○ for unmet gates', async () => {
    const wrapper = mountView()
    await flushPromises()
    const tabs = wrapper.findAll('.tab-btn')
    await tabs.find(t => t.text() === 'Readiness')!.trigger('click')

    // blockingQuestionsResolved=true → ✓
    const okGates = wrapper.findAll('.gate-icon.ok')
    expect(okGates.length).toBeGreaterThanOrEqual(3)

    // readyForReview=false, readyForPR=false → ○
    const pendingGates = wrapper.findAll('.gate-icon.pending')
    expect(pendingGates.length).toBe(2)
  })

  it('shows unavailable message when there is no plan', async () => {
    vi.mocked(workstreamsApi.getPlan).mockRejectedValue(new Error('Not found'))
    vi.mocked(workstreamsApi.getReadiness).mockRejectedValue(new Error('Not found'))
    const wrapper = mountView()
    await flushPromises()
    const tabs = wrapper.findAll('.tab-btn')
    await tabs.find(t => t.text() === 'Readiness')!.trigger('click')
    expect(wrapper.text()).toContain('unavailable')
  })

  // Activity tab content
  it('renders activity events in reverse chronological order (newest first)', async () => {
    const wrapper = mountView()
    await flushPromises()
    const tabs = wrapper.findAll('.tab-btn')
    await tabs.find(t => t.text() === 'Activity')!.trigger('click')

    const text = wrapper.text()
    expect(text.indexOf('Plan Critic')).toBeLessThan(text.indexOf('Context Scout'))
  })

  it('adds a new activity event via the form', async () => {
    const newEvent: ActivityEvent = {
      id: 'e3', workstreamId: 'ws-1', agentName: 'Test Bot',
      type: 'IMPLEMENTATION', message: 'Done', createdAt: new Date().toISOString(),
    }
    vi.mocked(workstreamsApi.addActivity).mockResolvedValue(newEvent)

    const wrapper = mountView()
    await flushPromises()

    const tabs = wrapper.findAll('.tab-btn')
    await tabs.find(t => t.text() === 'Activity')!.trigger('click')
    await wrapper.find('.section-header .btn-secondary').trigger('click')
    await wrapper.find('input[placeholder*="Context Scout"]').setValue('Test Bot')
    await wrapper.find('textarea').setValue('Done')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Test Bot')
  })

  it('deduplicates activity arriving via WS while POST is in flight', async () => {
    const newEvent: ActivityEvent = {
      id: 'e3', workstreamId: 'ws-1', agentName: 'Race Bot',
      type: 'PLANNING', message: 'Racing', createdAt: new Date().toISOString(),
    }
    let resolveAdd!: (e: ActivityEvent) => void
    vi.mocked(workstreamsApi.addActivity).mockReturnValue(
      new Promise(resolve => { resolveAdd = resolve }),
    )

    const wrapper = mountView()
    await flushPromises()

    const tabs = wrapper.findAll('.tab-btn')
    await tabs.find(t => t.text() === 'Activity')!.trigger('click')
    await wrapper.find('.section-header .btn-secondary').trigger('click')
    await wrapper.find('input[placeholder*="Context Scout"]').setValue('Race Bot')
    await wrapper.find('textarea').setValue('Racing')
    await wrapper.find('form').trigger('submit')

    // WS broadcast arrives first
    capturedHandlers.onActivity?.(newEvent)
    await flushPromises()

    // Then POST resolves with same event
    resolveAdd(newEvent)
    await flushPromises()

    // Event should appear exactly once
    const feed = wrapper.findAll('.activity-item')
    const matches = feed.filter(f => f.text().includes('Race Bot'))
    expect(matches).toHaveLength(1)
  })

  // Real-time updates
  it('updates workstream title when WS workstream:updated arrives', async () => {
    const wrapper = mountView()
    await flushPromises()

    capturedHandlers.onWorkstreamUpdated?.({ ...workstream, title: 'Renamed Service' })
    await flushPromises()

    expect(wrapper.text()).toContain('Renamed Service')
    expect(wrapper.text()).not.toContain('Auth service')
  })

  it('updates plan when WS plan:updated arrives', async () => {
    const wrapper = mountView()
    await flushPromises()

    capturedHandlers.onPlanUpdated?.({ ...plan, goal: 'Updated goal' })
    await flushPromises()

    expect(wrapper.text()).toContain('Updated goal')
  })

  // Editing
  it('opens edit form and saves title change', async () => {
    vi.mocked(workstreamsApi.updateWorkstream).mockResolvedValue({ ...workstream, title: 'Renamed' })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('.btn-secondary.btn-sm').trigger('click') // Edit button
    await wrapper.find('.edit-title').setValue('Renamed')
    await wrapper.find('.btn-primary.btn-sm').trigger('click') // Save button
    await flushPromises()

    expect(wrapper.text()).toContain('Renamed')
  })

  it('cancels edit without saving', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('.btn-secondary.btn-sm').trigger('click')
    await wrapper.find('.edit-title').setValue('Should not save')
    await wrapper.find('.btn-ghost.btn-sm').trigger('click') // Cancel
    await flushPromises()

    expect(wrapper.text()).not.toContain('Should not save')
    expect(wrapper.text()).toContain('Auth service')
  })

  // Back navigation
  it('navigates back to workstreams list on ← Workstreams click', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('.btn-back').trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/workstreams')
  })
})
