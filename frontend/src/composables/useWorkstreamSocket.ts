import { onUnmounted } from 'vue'
import { sendWhenReady, addHandler } from './socket'
import type { ActivityEvent, Plan, Workstream, WorkstreamSummary } from '../types/workstream'

interface Handlers {
  onActivity?: (event: ActivityEvent) => void
  onWorkstreamUpdated?: (workstream: Workstream) => void
  onPlanUpdated?: (plan: Plan) => void
}

interface WorkstreamsHandlers {
  onWorkstreamUpdated?: (workstream: WorkstreamSummary) => void
}

/**
 * Subscribes to a single workstream room on the shared socket.
 * Sends workstream:join on mount and workstream:leave on unmount.
 */
export function useWorkstreamSocket(workstreamId: string, handlers: Handlers): void {
  const remove = addHandler((msg) => {
    if (msg.data === null || typeof msg.data !== 'object') return
    switch (msg.type) {
      case 'activity:created':
        handlers.onActivity?.(msg.data as ActivityEvent)
        break
      case 'workstream:updated':
        handlers.onWorkstreamUpdated?.(msg.data as Workstream)
        break
      case 'plan:updated':
        handlers.onPlanUpdated?.(msg.data as Plan)
        break
    }
  })

  sendWhenReady({ type: 'workstream:join', workstreamId })

  onUnmounted(() => {
    remove()
    sendWhenReady({ type: 'workstream:leave', workstreamId })
  })
}

/**
 * Subscribes to all workstream updates on the shared socket (list page).
 * workstream:updated now includes the active field, so no separate presence
 * tracking is needed — the onWorkstreamUpdated handler receives full state.
 */
export function useWorkstreamsSocket(handlers?: WorkstreamsHandlers): void {
  const remove = addHandler((msg) => {
    if (msg.type === 'workstream:updated' && msg.data !== null && typeof msg.data === 'object') {
      handlers?.onWorkstreamUpdated?.(msg.data as WorkstreamSummary)
    }
  })

  sendWhenReady({ type: 'workstreams:subscribe' })

  onUnmounted(() => {
    remove()
  })
}
