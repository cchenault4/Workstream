package digital.honeybadger.workflow.adapter.inbound.websocket

import digital.honeybadger.workflow.appJson
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

/**
 * Registers the WebSocket endpoint at /ws.
 *
 * Protocol (client → server):
 *   {"type":"workstream:join","workstreamId":"<id>"}  — subscribe to a room
 *   {"type":"workstream:leave","workstreamId":"<id>"} — unsubscribe from a room
 *
 * Unknown message types are silently ignored.
 * Messages with a blank workstreamId are silently ignored.
 * All joined rooms are cleaned up automatically when the session closes.
 */
fun Application.configureWebSocketRoutes(registry: WebSocketSessionRegistry) {
    routing {
        webSocket("/ws") {
            val joinedRooms = mutableSetOf<String>()
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val msg = runCatching {
                        appJson.decodeFromString<WsClientMessage>(frame.readText())
                    }.getOrNull() ?: continue

                    if (msg.workstreamId.isBlank()) continue

                    when (msg.type) {
                        "workstream:join" -> {
                            registry.join(msg.workstreamId, this)
                            joinedRooms.add(msg.workstreamId)
                        }
                        "workstream:leave" -> {
                            registry.leave(msg.workstreamId, this)
                            joinedRooms.remove(msg.workstreamId)
                        }
                    }
                }
            } finally {
                // Ensure the session is removed from every room it joined, regardless of
                // how the connection closed (client disconnect, server error, etc.).
                joinedRooms.forEach { registry.leave(it, this) }
            }
        }
    }
}
