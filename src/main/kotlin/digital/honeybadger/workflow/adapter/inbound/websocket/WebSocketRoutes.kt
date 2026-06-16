package digital.honeybadger.workflow.adapter.inbound.websocket

import digital.honeybadger.workflow.appJson
import digital.honeybadger.workflow.application.port.inbound.WorkstreamUseCase
import digital.honeybadger.workflow.application.port.outbound.EventPublisher
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
 *   {"type":"workstreams:subscribe"}                  — subscribe to all workstream updates
 *
 * On join and leave the publisher emits workstream:updated (including the current active
 * status) so presence is conveyed through the same event as data updates.
 */
private const val WORKSTREAMS_ROOM = "__workstreams__"
private val log = LoggerFactory.getLogger("WebSocketRoutes")

fun Application.configureWebSocketRoutes(
    registry: WebSocketSessionRegistry,
    scope: CoroutineScope,
    workstreamUseCase: WorkstreamUseCase,
    publisher: EventPublisher,
) {
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
                            log.info("[{}] joined workstreams room", sessionId)
                        }
                        "workstream:join" -> {
                            if (msg.workstreamId.isBlank()) continue
                            registry.join(msg.workstreamId, this)
                            joinedRooms.add(msg.workstreamId)
                            log.info("[{}] joined workstream={}", sessionId, msg.workstreamId)
                            scope.launch { broadcastPresence(msg.workstreamId, workstreamUseCase, publisher) }
                        }
                        "workstream:leave" -> {
                            if (msg.workstreamId.isBlank()) continue
                            registry.leave(msg.workstreamId, this)
                            joinedRooms.remove(msg.workstreamId)
                            log.info("[{}] left workstream={}", sessionId, msg.workstreamId)
                            scope.launch { broadcastPresence(msg.workstreamId, workstreamUseCase, publisher) }
                        }
                    }
                }
            } finally {
                log.info("[{}] disconnected; cleaning up rooms={}", sessionId, joinedRooms)
                joinedRooms.forEach { wid ->
                    registry.leave(wid, this)
                    if (wid != WORKSTREAMS_ROOM) {
                        scope.launch { broadcastPresence(wid, workstreamUseCase, publisher) }
                    }
                }
            }
        }
    }
}

private fun broadcastPresence(workstreamId: String, useCase: WorkstreamUseCase, publisher: EventPublisher) {
    runCatching { useCase.getById(workstreamId) }
        .onSuccess { publisher.publishWorkstreamUpdate(it) }
        .onFailure { log.warn("workstream {} not found for presence broadcast", workstreamId) }
}
