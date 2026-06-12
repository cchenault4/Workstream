package digital.honeybadger.workflow.application.port.outbound

import digital.honeybadger.workflow.domain.model.Workstream

/**
 * Outbound port for workstream persistence.
 *
 * Purpose: abstracts the storage mechanism so use cases remain independent of any
 *          specific persistence technology. The in-memory adapter is the current implementation.
 * Invariants: [save] is an upsert — it creates if absent, replaces if present (matched by id).
 */
interface WorkstreamRepository {

    /** Persists [workstream], overwriting any existing entry with the same id. Returns the saved instance. */
    fun save(workstream: Workstream): Workstream

    /** Returns the workstream with the given [id], or null if none exists. */
    fun findById(id: String): Workstream?

    /** Returns all workstreams in insertion order. Empty list if none exist. */
    fun findAll(): List<Workstream>
}
