/**
 * Singleton WebSocket connection shared across all views.
 *
 * Views and composables call sendWhenReady / addHandler rather than creating their own
 * connections. The socket is created lazily on first use and reused until the app unmounts.
 */

interface WsMessage {
  type: string
  data: unknown
}

type MessageHandler = (msg: WsMessage) => void

let ws: WebSocket | null = null
const handlers = new Set<MessageHandler>()
const sendQueue: string[] = []

function connect(): WebSocket {
  if (ws && ws.readyState !== WebSocket.CLOSED && ws.readyState !== WebSocket.CLOSING) {
    return ws
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${window.location.host}/ws`)

  ws.onopen = () => {
    sendQueue.splice(0).forEach(msg => ws!.send(msg))
  }

  ws.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data as string) as WsMessage
      if (typeof msg.type !== 'string') return
      handlers.forEach(h => h(msg))
    } catch { /* ignore malformed frames */ }
  }

  return ws
}

/** Send a JSON message, queuing it if the socket is not yet open. */
export function sendWhenReady(data: object): void {
  const socket = connect()
  const json = JSON.stringify(data)
  if (socket.readyState === WebSocket.OPEN) {
    socket.send(json)
  } else {
    sendQueue.push(json)
  }
}

/** Register a message handler. Returns an unsubscribe function. */
export function addHandler(handler: MessageHandler): () => void {
  handlers.add(handler)
  return () => handlers.delete(handler)
}

/** Close the socket and reset all state. Call from App.vue on unmount. */
export function teardownSocket(): void {
  ws?.close()
  ws = null
  handlers.clear()
  sendQueue.length = 0
}
