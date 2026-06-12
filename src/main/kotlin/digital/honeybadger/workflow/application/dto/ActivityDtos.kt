package digital.honeybadger.workflow.application.dto

import digital.honeybadger.workflow.domain.model.ActivityType
import kotlinx.serialization.Serializable

/**
 * Request body for POST /workstreams/:id/activity.
 *
 * Purpose: carries the agent-supplied fields for a new activity event.
 * Assumptions: [agentName] is a free-form string identifying the agent (e.g. "Context Scout").
 * Requirements: the target workstream must already exist.
 * Invariants: [id], [workstreamId], and [createdAt] are absent — the server assigns them.
 *             Events are immutable once created; there is no update or delete operation.
 */
@Serializable
data class CreateActivityEventRequest(
    val agentName: String,
    val type: ActivityType,
    val message: String
)
