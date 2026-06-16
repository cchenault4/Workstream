package digital.honeybadger.workflow.adapter.inbound.websocket

import digital.honeybadger.workflow.appJson
import digital.honeybadger.workflow.application.port.inbound.PresenceUseCase
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.slf4j.LoggerFactory

/**
 * Registers the WebSocket endpoint at /ws.
 *
 * Protocol (client → server):
 *   {"type":"workstream:join","workstreamId":"<id>"}  — subscribe to a room
 *   {"type":"workstream:leave","workstreamId":"<id>"} — unsubscribe from a room
 *   {"type":"workstreams:subscribe"}                  — subscribe to all workstream updates
 *
 * Join and leave are delegated to [PresenceUseCase], which owns the subscriber
 * count and broadcasts presence changes. On disconnect, [WebSocketSessionRegistry.leaveAll]
 * returns the rooms this session occupied so the use case can decrement each count.
 */
private const val WORKSTREAMS_ROOM = "__workstreams__"
private val log = LoggerFactory.getLogger("WebSocketRoutes")

fun Application.configureWebSocketRoutes(
    sessionRegistry: WebSocketSessionRegistry,
    presenceUseCase: PresenceUseCase,
) {
    routing {
        webSocket("/ws") {
            val sessionId = System.identityHashCode(this).toString(16)
            log.info("[{}] connected", sessionId)
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val msg = runCatching {
                        appJson.decodeFromString<WsClientMessage>(frame.readText())
                    }.getOrNull() ?: continue

                    log.info("[{}] received type={} workstreamId={}", sessionId, msg.type, msg.workstreamId.ifBlank { "(none)" })

                    when (msg.type) {
                        "workstreams:subscribe" -> {
                            sessionRegistry.join(WORKSTREAMS_ROOM, this)
                            log.info("[{}] joined workstreams room", sessionId)
                        }
                        "workstream:join" -> {
                            if (msg.workstreamId.isBlank()) continue
                            sessionRegistry.join(msg.workstreamId, this)
                            presenceUseCase.workstreamJoined(msg.workstreamId)
                            log.info("[{}] joined workstream={}", sessionId, msg.workstreamId)
                        }
                        "workstream:leave" -> {
                            if (msg.workstreamId.isBlank()) continue
                            sessionRegistry.leave(msg.workstreamId, this)
                            presenceUseCase.workstreamLeft(msg.workstreamId)
                            log.info("[{}] left workstream={}", sessionId, msg.workstreamId)
                        }
                    }
                }
            } finally {
                log.info("[{}] disconnected", sessionId)
                sessionRegistry.leaveAll(this).forEach { wid ->
                    if (wid != WORKSTREAMS_ROOM) presenceUseCase.workstreamLeft(wid)
                }
            }
        }
    }
}
