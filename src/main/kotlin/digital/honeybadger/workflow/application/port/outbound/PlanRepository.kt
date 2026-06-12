package digital.honeybadger.workflow.application.port.outbound

import digital.honeybadger.workflow.domain.model.Plan

/**
 * Outbound port for implementation plan persistence.
 *
 * Purpose: abstracts plan storage so use cases are independent of persistence details.
 * Invariants: at most one plan exists per workstream; [save] replaces any prior plan for the
 *             same [Plan.workstreamId] (upsert by workstreamId).
 */
interface PlanRepository {

    /** Persists [plan], replacing any existing plan for the same workstreamId. Returns the saved instance. */
    fun save(plan: Plan): Plan

    /** Returns the plan for the given [workstreamId], or null if none has been attached. */
    fun findByWorkstreamId(workstreamId: String): Plan?
}
