import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import WorkstreamsView from '../../views/WorkstreamsView.vue'
import type { Workstream } from '../../types/workstream'

// ── Mocks ─────────────────────────────────────────────────────────────────────

const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ params: {} }),
}))

// Capture the onWorkstreamUpdated handler so tests can simulate WS messages.
let capturedOnWorkstreamUpdated: ((ws: Workstream) => void) | undefined

vi.mock('../../composables/useWorkstreamSocket', () => ({
  useWorkstreamsSocket: vi.fn((handlers?: { onWorkstreamUpdated?: (ws: Workstream) => void }) => {
    capturedOnWorkstreamUpdated = handlers?.onWorkstreamUpdated
  }),
  useWorkstreamSocket: vi.fn(),
}))

vi.mock('../../api/workstreams', () => ({
  workstreamsApi: {
    listWorkstreams: vi.fn(),
    createWorkstream: vi.fn(),
  },
}))

// ── Fixtures ─────────────────────────────────────────────────────────────────

import { workstreamsApi } from '../../api/workstreams'

const ws1: Workstream = {
  id: 'ws-1', title: 'Auth service', description: 'Add JWT', requester: 'alice',
  priority: 'HIGH', status: 'NEW', active: false,
  createdAt: '2025-03-01T10:00:00Z', updatedAt: '2025-03-01T10:00:00Z',
}

const ws2: Workstream = {
  id: 'ws-2', title: 'Search feature', description: 'Full-text search', requester: 'bob',
  priority: 'MEDIUM', status: 'PLANNING', active: true,
  createdAt: '2025-03-02T10:00:00Z', updatedAt: '2025-03-02T10:00:00Z',
}

function mountView() {
  return mount(WorkstreamsView, {
    global: { mocks: { $router: { push: mockPush } } },
  })
}

// ── Tests ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.mocked(workstreamsApi.listWorkstreams).mockResolvedValue([ws1, ws2])
  vi.mocked(workstreamsApi.createWorkstream).mockResolvedValue({
    ...ws1, id: 'ws-new', title: 'New WS',
  })
  capturedOnWorkstreamUpdated = undefined
  mockPush.mockClear()
})

describe('WorkstreamsView', () => {
  it('shows loading state before data arrives', () => {
    vi.mocked(workstreamsApi.listWorkstreams).mockReturnValue(new Promise(() => {}))
    const wrapper = mountView()
    expect(wrapper.text()).toContain('Loading')
  })

  it('renders workstream rows after load', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Auth service')
    expect(wrapper.text()).toContain('Search feature')
  })

  it('shows Active badge only for workstreams with active:true', async () => {
    // ws2 has active:true, ws1 has active:false
    const wrapper = mountView()
    await flushPromises()
    const activeCount = (wrapper.text().match(/● Active/g) ?? []).length
    expect(activeCount).toBe(1)
  })

  it('shows empty state when no workstreams exist', async () => {
    vi.mocked(workstreamsApi.listWorkstreams).mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('No workstreams yet')
  })

  it('shows error state when load fails', async () => {
    vi.mocked(workstreamsApi.listWorkstreams).mockRejectedValue(new Error('Network error'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Network error')
  })

  it('opens the create form when + New Workstream is clicked', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('button.btn-primary').trigger('click')
    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('adds a new workstream to the list on successful create', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.btn-primary').trigger('click')
    await wrapper.find('#title').setValue('New WS')
    await wrapper.find('#description').setValue('Desc')
    await wrapper.find('#requester').setValue('carol')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('New WS')
  })

  it('deduplicates when WS message adds workstream before POST resolves', async () => {
    let resolveCreate!: (ws: Workstream) => void
    const newWs: Workstream = { ...ws1, id: 'ws-new', title: 'New WS' }
    vi.mocked(workstreamsApi.createWorkstream).mockReturnValue(
      new Promise(resolve => { resolveCreate = resolve }),
    )

    const wrapper = mountView()
    await flushPromises()

    // Open form and submit
    await wrapper.find('button.btn-primary').trigger('click')
    await wrapper.find('#title').setValue('New WS')
    await wrapper.find('#description').setValue('Desc')
    await wrapper.find('#requester').setValue('carol')
    await wrapper.find('form').trigger('submit')

    // WS broadcast arrives first
    capturedOnWorkstreamUpdated?.(newWs)
    await flushPromises()

    // Then POST resolves
    resolveCreate(newWs)
    await flushPromises()

    // Should appear only once in the table
    const rows = wrapper.findAll('tbody tr')
    const newWsRows = rows.filter(r => r.text().includes('New WS'))
    expect(newWsRows).toHaveLength(1)
  })

  it('updates existing workstream in place via WS message', async () => {
    const wrapper = mountView()
    await flushPromises()

    capturedOnWorkstreamUpdated?.({ ...ws1, title: 'Updated Title' })
    await flushPromises()

    expect(wrapper.text()).toContain('Updated Title')
    expect(wrapper.text()).not.toContain('Auth service')
  })

  it('navigates to detail page on row click', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('tbody tr').trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/workstreams/ws-1')
  })
})
