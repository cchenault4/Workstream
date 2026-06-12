package digital.honeybadger.workflow.adapter.outbound.persistence

import digital.honeybadger.workflow.application.port.outbound.WorkstreamRepository
import digital.honeybadger.workflow.domain.model.Workstream
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of [WorkstreamRepository].
 *
 * Purpose: satisfies the persistence port without a database for this exercise.
 * Assumptions: single-process deployment; state is lost on restart.
 * Invariants: insertion order is preserved via a [LinkedHashMap] protected by a lock,
 *             so [findAll] always reflects the order workstreams were first saved.
 */
class InMemoryWorkstreamRepository : WorkstreamRepository {

    // LinkedHashMap preserves insertion order; external synchronisation guards concurrent access.
    private val store = LinkedHashMap<String, Workstream>()
    private val lock = Any()

    override fun save(workstream: Workstream): Workstream {
        synchronized(lock) { store[workstream.id] = workstream }
        return workstream
    }

    override fun findById(id: String): Workstream? =
        synchronized(lock) { store[id] }

    override fun findAll(): List<Workstream> =
        synchronized(lock) { store.values.toList() }
}
