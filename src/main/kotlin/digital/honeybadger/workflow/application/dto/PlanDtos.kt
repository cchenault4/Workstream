package digital.honeybadger.workflow.application.dto

import digital.honeybadger.workflow.domain.model.OpenQuestion
import digital.honeybadger.workflow.domain.model.Phase
import kotlinx.serialization.Serializable

/**
 * Request body for PUT /workstreams/:id/plan.
 *
 * Purpose: carries the full plan content supplied by the client for a create-or-replace upsert.
 * Assumptions: PUT semantics — this replaces the entire plan if one already exists.
 *              Partial updates to individual phases or questions are not supported via this endpoint.
 * Requirements: the target workstream must already exist before a plan can be attached.
 * Invariants: [workstreamId] is derived from the URL path parameter, not the request body.
 *             [OpenQuestion.id] and [Phase.id] within the lists are client-supplied; uniqueness
 *             within the request is the caller's responsibility.
 */
@Serializable
data class UpsertPlanRequest(
    val goal: String,
    val nonGoals: List<String> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val openQuestions: List<OpenQuestion> = emptyList(),
    val phases: List<Phase> = emptyList(),
    val verificationPlan: List<String> = emptyList()
)
