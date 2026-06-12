package digital.honeybadger.workflow.adapter.inbound.websocket

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

/**
 * Registers the WebSocket endpoint at /ws.
 *
 * Protocol (client → server):
 *   {"type":"workstream:join","workstreamId":"<id>"}  — subscribe to a room
 *   {"type":"workstream:leave","workstreamId":"<id>"} — unsubscribe from a room
 *
 * Unknown message types are silently ignored.
 * All joined rooms are cleaned up automatically when the session closes.
 */
fun Application.configureWebSocketRoutes(registry: WebSocketSessionRegistry) {
    val json = Json { ignoreUnknownKeys = true }

    routing {
        webSocket("/ws") {
            val joinedRooms = mutableSetOf<String>()
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val msg = runCatching {
                        json.decodeFromString<WsClientMessage>(frame.readText())
                    }.getOrNull() ?: continue

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
