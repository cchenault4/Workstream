package digital.honeybadger.workflow.adapter.outbound.persistence

import digital.honeybadger.workflow.application.port.outbound.ActivityRepository
import digital.honeybadger.workflow.domain.model.ActivityEvent

/**
 * In-memory implementation of [ActivityRepository].
 *
 * Purpose: satisfies the activity persistence port without a database.
 * Assumptions: single-process deployment; state is lost on restart.
 * Invariants: events are append-only; insertion order is preserved per workstream.
 *             Events for different workstreams are stored in independent lists.
 */
class InMemoryActivityRepository : ActivityRepository {

    private val store = HashMap<String, MutableList<ActivityEvent>>()
    private val lock = Any()

    override fun save(event: ActivityEvent): ActivityEvent {
        synchronized(lock) {
            store.getOrPut(event.workstreamId) { mutableListOf() }.add(event)
        }
        return event
    }

    override fun findByWorkstreamId(workstreamId: String): List<ActivityEvent> =
        synchronized(lock) { store[workstreamId]?.toList() ?: emptyList() }
}
