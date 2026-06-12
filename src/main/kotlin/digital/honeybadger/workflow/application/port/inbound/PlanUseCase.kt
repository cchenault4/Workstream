package digital.honeybadger.workflow.application.port.inbound

import digital.honeybadger.workflow.application.dto.UpsertPlanRequest
import digital.honeybadger.workflow.application.exception.PlanNotFoundException
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.domain.model.Plan
import digital.honeybadger.workflow.domain.model.ReadinessState

/**
 * Inbound port for implementation plan operations on a workstream.
 *
 * Purpose: defines the application's plan API as seen by driving adapters.
 *          Each workstream holds at most one plan; upsert replaces it entirely (PUT semantics).
 * Assumptions: plan operations are only valid on existing workstreams.
 *              Readiness is computed on demand and never cached.
 */
interface PlanUseCase {

    /**
     * Creates or fully replaces the plan attached to the workstream identified by [workstreamId].
     *
     * Purpose: PUT semantics — idempotent full replacement. Calling twice with the same body
     *          produces the same result, with no trace of the previous plan.
     * Requirements: the workstream identified by [workstreamId] must exist.
     * Invariants: the returned plan's [Plan.workstreamId] always equals the supplied [workstreamId].
     * @throws WorkstreamNotFoundException if no workstream with [workstreamId] exists.
     */
    fun upsert(workstreamId: String, request: UpsertPlanRequest): Plan

    /**
     * Returns the plan currently attached to the workstream identified by [workstreamId].
     *
     * Purpose: supports plan display and downstream readiness checks.
     * Requirements: a plan must have been attached via [upsert] before this is called.
     * @throws WorkstreamNotFoundException if no workstream with [workstreamId] exists.
     * @throws PlanNotFoundException if the workstream exists but has no plan attached.
     */
    fun get(workstreamId: String): Plan

    /**
     * Computes and returns the derived readiness state for the workstream's current plan.
     *
     * Purpose: allows clients to check gate conditions without re-implementing readiness logic.
     * Assumptions: readiness is a pure function of the current plan snapshot; it is not stored.
     * Requirements: a plan must exist for the given workstream.
     * Invariants: the result is always consistent with a fresh call to [ReadinessService.compute]
     *             on the plan returned by [get] for the same workstream at the same instant.
     * @throws WorkstreamNotFoundException if no workstream with [workstreamId] exists.
     * @throws PlanNotFoundException if the workstream exists but has no plan attached.
     */
    fun readiness(workstreamId: String): ReadinessState
}
