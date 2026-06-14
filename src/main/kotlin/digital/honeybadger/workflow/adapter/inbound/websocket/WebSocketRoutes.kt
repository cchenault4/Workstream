package digital.honeybadger.workflow.adapter.inbound.websocket

import digital.honeybadger.workflow.appJson
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
private const val PRESENCE_ROOM = "__presence__"

fun Application.configureWebSocketRoutes(registry: WebSocketSessionRegistry, scope: CoroutineScope) {
    routing {
        webSocket("/ws") {
            val joinedRooms = mutableSetOf<String>()
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val msg = runCatching {
                        appJson.decodeFromString<WsClientMessage>(frame.readText())
                    }.getOrNull() ?: continue

                    when (msg.type) {
                        // List-page clients subscribe to presence updates for all workstreams.
                        "presence:subscribe" -> {
                            registry.join(PRESENCE_ROOM, this)
                            joinedRooms.add(PRESENCE_ROOM)
                            // Catch up: send current active state for workstreams already being viewed.
                            registry.activeRooms().forEach { wid ->
                                send(Frame.Text(presenceMessage(wid, true)))
                            }
                        }
                        "workstream:join" -> {
                            if (msg.workstreamId.isBlank()) continue
                            registry.join(msg.workstreamId, this)
                            joinedRooms.add(msg.workstreamId)
                            scope.launch { registry.broadcast(PRESENCE_ROOM, presenceMessage(msg.workstreamId, true)) }
                        }
                        "workstream:leave" -> {
                            if (msg.workstreamId.isBlank()) continue
                            registry.leave(msg.workstreamId, this)
                            joinedRooms.remove(msg.workstreamId)
                            scope.launch { registry.broadcast(PRESENCE_ROOM, presenceMessage(msg.workstreamId, registry.roomSize(msg.workstreamId) > 0)) }
                        }
                    }
                }
            } finally {
                // Clean up all joined rooms; broadcast presence change for any workstream rooms.
                joinedRooms.forEach { wid ->
                    registry.leave(wid, this)
                    if (wid != PRESENCE_ROOM) {
                        scope.launch { registry.broadcast(PRESENCE_ROOM, presenceMessage(wid, registry.roomSize(wid) > 0)) }
                    }
                }
            }
        }
    }
}

private fun presenceMessage(workstreamId: String, active: Boolean): String =
    """{"type":"workstream:presence","data":{"workstreamId":"$workstreamId","active":$active}}"""
