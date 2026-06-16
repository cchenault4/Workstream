package digital.honeybadger.workflow.adapter.inbound.websocket

import io.ktor.websocket.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages WebSocket session membership in per-workstream rooms.
 *
 * Purpose: provides the join/leave/broadcast primitives used by both the WebSocket
 *          route handler (inbound) and the [WebSocketEventPublisher] (outbound).
 *          Presence state (active workstreams, subscriber counts) is owned by
 *          [digital.honeybadger.workflow.application.port.outbound.PresenceRegistry].
 * Assumptions: sessions are identified by object identity; the same session joining
 *              a room twice results in a single registration (set semantics).
 * Invariants: a closed or dead session that throws on [send] is silently evicted so
 *             subsequent broadcasts skip it automatically.
 */
interface WebSocketSessionRegistry {
    fun join(workstreamId: String, session: DefaultWebSocketSession)
    fun leave(workstreamId: String, session: DefaultWebSocketSession)
    /** Removes [session] from all rooms it has joined and returns the affected room IDs. */
    fun leaveAll(session: DefaultWebSocketSession): Set<String>
    suspend fun broadcast(workstreamId: String, message: String)
}

/**
 * In-memory implementation of [WebSocketSessionRegistry].
 * Uses a [ConcurrentHashMap] of identity-keyed sets for thread-safe room management.
 */
class DefaultWebSocketSessionRegistry : WebSocketSessionRegistry {

    private val log = LoggerFactory.getLogger(DefaultWebSocketSessionRegistry::class.java)

    // ConcurrentHashMap.newKeySet() gives a thread-safe Set backed by a CHM, preserving
    // set semantics (no duplicates) without explicit locking.
    private val rooms = ConcurrentHashMap<String, MutableSet<DefaultWebSocketSession>>()

    override fun join(workstreamId: String, session: DefaultWebSocketSession) {
        rooms.computeIfAbsent(workstreamId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    override fun leave(workstreamId: String, session: DefaultWebSocketSession) {
        rooms[workstreamId]?.remove(session)
    }

    override fun leaveAll(session: DefaultWebSocketSession): Set<String> {
        val affected = mutableSetOf<String>()
        rooms.forEach { (wid, sessions) ->
            if (sessions.remove(session)) affected.add(wid)
        }
        return affected
    }

    override suspend fun broadcast(workstreamId: String, message: String) {
        val sessions = rooms[workstreamId]
        log.debug("broadcast room={} sessionCount={} message={}", workstreamId, sessions?.size ?: 0, message)
        if (sessions == null) return
        val dead = mutableSetOf<DefaultWebSocketSession>()
        sessions.forEach { session ->
            val result = runCatching { session.send(Frame.Text(message)) }
            if (result.isFailure) {
                log.warn("broadcast room={} send failed: {}", workstreamId, result.exceptionOrNull()?.message)
                dead.add(session)
            }
        }
        // Evict dead sessions so subsequent broadcasts skip them without retrying.
        sessions.removeAll(dead)
    }
}
