package digital.honeybadger.workflow.adapter.inbound.websocket

import digital.honeybadger.workflow.appJson
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

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
private const val WORKSTREAMS_ROOM = "__workstreams__"
private val log = LoggerFactory.getLogger("WebSocketRoutes")

fun Application.configureWebSocketRoutes(registry: WebSocketSessionRegistry, scope: CoroutineScope) {
    routing {
        webSocket("/ws") {
            val sessionId = System.identityHashCode(this).toString(16)
            log.info("[{}] connected", sessionId)
            val joinedRooms = mutableSetOf<String>()
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val msg = runCatching {
                        appJson.decodeFromString<WsClientMessage>(frame.readText())
                    }.getOrNull() ?: continue

                    log.info("[{}] received type={} workstreamId={}", sessionId, msg.type, msg.workstreamId.ifBlank { "(none)" })

                    when (msg.type) {
                        "workstreams:subscribe" -> {
                            registry.join(WORKSTREAMS_ROOM, this)
                            joinedRooms.add(WORKSTREAMS_ROOM)
                            log.info("[{}] joined presence room; presenceRoom size={}", sessionId, registry.roomSize(WORKSTREAMS_ROOM))
                            val active = registry.activeRooms()
                            log.info("[{}] catching up: active rooms={}", sessionId, active)
                            active.forEach { wid ->
                                send(Frame.Text(presenceMessage(wid, true)))
                            }
                        }
                        "workstream:join" -> {
                            if (msg.workstreamId.isBlank()) continue
                            registry.join(msg.workstreamId, this)
                            joinedRooms.add(msg.workstreamId)
                            log.info("[{}] joined workstream={}; presenceRoom size={}", sessionId, msg.workstreamId, registry.roomSize(WORKSTREAMS_ROOM))
                            scope.launch {
                                log.info("[{}] broadcasting presence active to presenceRoom (size={})", sessionId, registry.roomSize(WORKSTREAMS_ROOM))
                                registry.broadcast(WORKSTREAMS_ROOM, presenceMessage(msg.workstreamId, true))
                            }
                        }
                        "workstream:leave" -> {
                            if (msg.workstreamId.isBlank()) continue
                            registry.leave(msg.workstreamId, this)
                            joinedRooms.remove(msg.workstreamId)
                            val active = registry.roomSize(msg.workstreamId) > 0
                            log.info("[{}] left workstream={}; still active={}", sessionId, msg.workstreamId, active)
                            scope.launch { registry.broadcast(WORKSTREAMS_ROOM, presenceMessage(msg.workstreamId, active)) }
                        }
                    }
                }
            } finally {
                log.info("[{}] disconnected; cleaning up rooms={}", sessionId, joinedRooms)
                joinedRooms.forEach { wid ->
                    registry.leave(wid, this)
                    if (wid != WORKSTREAMS_ROOM) {
                        val active = registry.roomSize(wid) > 0
                        log.info("[{}] disconnect cleanup: workstream={} still active={}", sessionId, wid, active)
                        scope.launch { registry.broadcast(WORKSTREAMS_ROOM, presenceMessage(wid, active)) }
                    }
                }
            }
        }
    }
}

private fun presenceMessage(workstreamId: String, active: Boolean): String =
    """{"type":"workstream:presence","data":{"workstreamId":"$workstreamId","active":$active}}"""
