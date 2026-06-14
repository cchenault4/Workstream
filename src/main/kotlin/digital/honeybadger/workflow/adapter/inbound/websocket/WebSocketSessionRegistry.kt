package digital.honeybadger.workflow.adapter.inbound.websocket

import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages WebSocket session membership in per-workstream rooms.
 *
 * Purpose: provides the join/leave/broadcast primitives used by both the WebSocket
 *          route handler (inbound) and the [WebSocketEventPublisher] (outbound).
 * Assumptions: sessions are identified by object identity; the same session joining
 *              a room twice results in a single registration (set semantics).
 * Invariants: a closed or dead session that throws on [send] is silently evicted so
 *             subsequent broadcasts skip it automatically.
 */
interface WebSocketSessionRegistry {
    fun join(workstreamId: String, session: DefaultWebSocketSession)
    fun leave(workstreamId: String, session: DefaultWebSocketSession)
    /** Number of sessions currently in [workstreamId]'s room. */
    fun roomSize(workstreamId: String): Int
    /** Workstream IDs (excluding internal rooms) that currently have at least one active session. */
    fun activeRooms(): Set<String>
    suspend fun broadcast(workstreamId: String, message: String)
}

/**
 * In-memory implementation of [WebSocketSessionRegistry].
 * Uses a [ConcurrentHashMap] of identity-keyed sets for thread-safe room management.
 */
class DefaultWebSocketSessionRegistry : WebSocketSessionRegistry {

    // ConcurrentHashMap.newKeySet() gives a thread-safe Set backed by a CHM, preserving
    // set semantics (no duplicates) without explicit locking.
    private val rooms = ConcurrentHashMap<String, MutableSet<DefaultWebSocketSession>>()

    override fun join(workstreamId: String, session: DefaultWebSocketSession) {
        rooms.computeIfAbsent(workstreamId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    override fun leave(workstreamId: String, session: DefaultWebSocketSession) {
        rooms[workstreamId]?.remove(session)
    }

    override fun roomSize(workstreamId: String): Int = rooms[workstreamId]?.size ?: 0

    override fun activeRooms(): Set<String> =
        rooms.entries
            .filter { it.value.isNotEmpty() && !it.key.startsWith("__") }
            .map { it.key }
            .toSet()

    override suspend fun broadcast(workstreamId: String, message: String) {
        val sessions = rooms[workstreamId] ?: return
        val dead = mutableSetOf<DefaultWebSocketSession>()
        sessions.forEach { session ->
            if (runCatching { session.send(Frame.Text(message)) }.isFailure) dead.add(session)
        }
        // Evict dead sessions so subsequent broadcasts skip them without retrying.
        sessions.removeAll(dead)
    }
}
