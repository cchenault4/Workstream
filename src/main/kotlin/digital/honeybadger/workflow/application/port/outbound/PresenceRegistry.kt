package digital.honeybadger.workflow.application.port.outbound

/**
 * Outbound port for tracking how many sessions are subscribed to each workstream.
 *
 * Purpose: lets the application layer determine presence state without any knowledge
 *          of the WebSocket transport. Counts are incremented on join and decremented
 *          on leave; "active" means at least one subscriber remains.
 */
interface PresenceRegistry {

    /** Increments the subscriber count for [workstreamId]. */
    fun join(workstreamId: String)

    /** Decrements the subscriber count for [workstreamId]. No-op if count is already zero. */
    fun leave(workstreamId: String)

    /** Returns true if at least one session is currently subscribed to [workstreamId]. */
    fun isActive(workstreamId: String): Boolean

    /** Returns the IDs of all workstreams that currently have at least one active subscriber. */
    fun activeWorkstreamIds(): Set<String>
}
