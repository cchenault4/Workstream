import { onUnmounted, ref } from 'vue'
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

/**
 * Manages a WebSocket connection to a single workstream room.
 * Joins on open, leaves and closes on component unmount.
 * Returns a reactive `connected` flag for UI indicators.
 */
export function useWorkstreamSocket(workstreamId: string, handlers: Handlers) {
  const connected = ref(false)

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const socket = new WebSocket(`${protocol}//${window.location.host}/ws`)

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
