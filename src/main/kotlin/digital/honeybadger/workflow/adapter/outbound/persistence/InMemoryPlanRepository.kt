package digital.honeybadger.workflow.adapter.outbound.persistence

import digital.honeybadger.workflow.application.port.outbound.PlanRepository
import digital.honeybadger.workflow.domain.model.Plan

/**
 * In-memory implementation of [PlanRepository].
 *
 * Purpose: satisfies the plan persistence port without a database.
 * Assumptions: single-process deployment; state is lost on restart.
 * Invariants: at most one plan per workstreamId; [save] silently replaces any prior plan.
 */
class InMemoryPlanRepository : PlanRepository {

    private val store = HashMap<String, Plan>()
    private val lock = Any()

    override fun save(plan: Plan): Plan {
        synchronized(lock) { store[plan.workstreamId] = plan }
        return plan
    }

    override fun findByWorkstreamId(workstreamId: String): Plan? =
        synchronized(lock) { store[workstreamId] }
}
