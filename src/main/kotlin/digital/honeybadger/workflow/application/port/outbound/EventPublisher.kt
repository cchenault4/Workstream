package digital.honeybadger.workflow.application.port.outbound

import digital.honeybadger.workflow.domain.model.ActivityEvent
import digital.honeybadger.workflow.domain.model.Plan
import digital.honeybadger.workflow.domain.model.Workstream

/**
 * Outbound port for real-time event broadcasting.
 *
 * Purpose: decouples use cases from the WebSocket transport layer.
 *          The WebSocket adapter implements this port by broadcasting to all sessions
 *          subscribed to the event's workstream room.
 * Invariants: broadcast is best-effort — a failed publish must not prevent the caller
 *             from completing its own operation or throw to the caller.
 *             Implementations are responsible for swallowing or logging publish failures.
 */
interface EventPublisher {

    /** Broadcasts [event] to all real-time subscribers of its workstream. */
    fun publish(event: ActivityEvent)

    /** Broadcasts a workstream mutation to all real-time subscribers of that workstream.
     *  The [active] status is derived from the session registry at broadcast time. */
    fun publishWorkstreamUpdate(workstream: Workstream)

    /** Broadcasts a presence change for a workstream with an explicitly-known [active] value.
     *  Use this from the WebSocket route handler where active is already determined by the
     *  join/leave action, avoiding any registry timing ambiguity. */
    fun publishPresenceUpdate(workstream: Workstream, active: Boolean) = publishWorkstreamUpdate(workstream)

    /** Broadcasts a plan mutation to all real-time subscribers of the plan's workstream. */
    fun publishPlanUpdate(plan: Plan)
}
