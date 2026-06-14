import { onUnmounted, ref } from 'vue'
import type { Ref } from 'vue'
import type { ActivityEvent, Plan, Workstream } from '../types/workstream'

interface WsMessage {
  type: string
  data: unknown
}

interface Handlers {
  onActivity?: (event: ActivityEvent) => void
  onWorkstreamUpdated?: (workstream: Workstream) => void
  onPlanUpdated?: (plan: Plan) => void
}

/** Creates a WebSocket connection to the /ws endpoint, using wss: on HTTPS. */
export function makeWsSocket(): WebSocket {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return new WebSocket(`${protocol}//${window.location.host}/ws`)
}

/**
 * Manages a WebSocket connection to a single workstream room.
 * Joins on open, leaves and closes on component unmount.
 * Returns a reactive `connected` flag for UI indicators.
 */
export function useWorkstreamSocket(workstreamId: string, handlers: Handlers) {
  const connected = ref(false)

  const socket = makeWsSocket()

  socket.onopen = () => {
    connected.value = true
    socket.send(JSON.stringify({ type: 'workstream:join', workstreamId }))
  }

  socket.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data as string) as WsMessage
      if (typeof msg.type !== 'string' || typeof msg.data !== 'object' || msg.data === null) return
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
    } catch {
      // ignore malformed frames
    }
  }

  socket.onclose = () => { connected.value = false }
  socket.onerror = () => { connected.value = false }

  onUnmounted(() => {
    if (socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'workstream:leave', workstreamId }))
    }
    socket.close()
  })

  return { connected }
}

interface WorkstreamsHandlers {
  onWorkstreamUpdated?: (workstream: Workstream) => void
}

/**
 * Subscribes to the __workstreams__ channel which delivers both presence
 * updates (workstream:presence) and data updates (workstream:updated) for
 * all workstreams. Used by the workstreams list page.
 */
export function useWorkstreamsSocket(handlers?: WorkstreamsHandlers): { activeWorkstreamIds: Ref<string[]> } {
  const activeWorkstreamIds = ref<string[]>([])
  let mounted = true

  const socket = makeWsSocket()

  socket.onopen = () => {
    if (!mounted) return
    socket.send(JSON.stringify({ type: 'workstreams:subscribe' }))
  }

  socket.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data as string) as WsMessage
      if (typeof msg.type !== 'string' || typeof msg.data !== 'object' || msg.data === null) return
      if (msg.type === 'workstream:presence') {
        const { workstreamId, active } = msg.data as { workstreamId: string; active: boolean }
        if (active) {
          if (!activeWorkstreamIds.value.includes(workstreamId)) {
            activeWorkstreamIds.value = [...activeWorkstreamIds.value, workstreamId]
          }
        } else {
          activeWorkstreamIds.value = activeWorkstreamIds.value.filter(id => id !== workstreamId)
        }
      } else if (msg.type === 'workstream:updated') {
        handlers?.onWorkstreamUpdated?.(msg.data as Workstream)
      }
    } catch {
      // ignore malformed frames
    }
  }

  onUnmounted(() => {
    mounted = false
    socket.close()
  })

  return { activeWorkstreamIds }
}
