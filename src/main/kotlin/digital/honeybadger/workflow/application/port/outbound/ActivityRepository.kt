package digital.honeybadger.workflow.application.port.outbound

import digital.honeybadger.workflow.domain.model.ActivityEvent

/**
 * Outbound port for activity event persistence.
 *
 * Purpose: abstracts append-only event storage so use cases are decoupled from the store.
 * Invariants: events are immutable once saved; there is no update or delete operation.
 *             [findByWorkstreamId] returns events in insertion (chronological) order.
 */
interface ActivityRepository {

    /** Appends [event] to the store. Returns the saved instance. */
    fun save(event: ActivityEvent): ActivityEvent

    /** Returns all events for [workstreamId] in insertion order. Empty list if none exist. */
    fun findByWorkstreamId(workstreamId: String): List<ActivityEvent>
}
