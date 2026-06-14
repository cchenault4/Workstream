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
fun Application.configureWebSocketRoutes(registry: WebSocketSessionRegistry, scope: CoroutineScope) {
    routing {
        webSocket("/ws") {
            registry.track(this)
            // Catch up the new client on current presence state so it doesn't miss joins
            // that happened before it connected.
            registry.activeRooms().forEach { wid -> send(Frame.Text(presenceMessage(wid, true))) }
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
                            scope.launch { registry.broadcastAll(presenceMessage(msg.workstreamId, true)) }
                        }
                        "workstream:leave" -> {
                            registry.leave(msg.workstreamId, this)
                            joinedRooms.remove(msg.workstreamId)
                            scope.launch { registry.broadcastAll(presenceMessage(msg.workstreamId, registry.roomSize(msg.workstreamId) > 0)) }
                        }
                    }
                }
            } finally {
                // Untrack before broadcasting so the closed session isn't a delivery target.
                registry.untrack(this)
                joinedRooms.forEach { wid ->
                    registry.leave(wid, this)
                    // Use application scope: the session coroutine may be cancelling,
                    // and suspend calls in a cancelled context throw immediately.
                    scope.launch { registry.broadcastAll(presenceMessage(wid, registry.roomSize(wid) > 0)) }
                }
            }
        }
    }
}

private fun presenceMessage(workstreamId: String, active: Boolean): String =
    """{"type":"workstream:presence","data":{"workstreamId":"$workstreamId","active":$active}}"""
