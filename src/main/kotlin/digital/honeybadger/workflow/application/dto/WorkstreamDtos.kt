package digital.honeybadger.workflow.application.dto

import digital.honeybadger.workflow.domain.model.Priority
import digital.honeybadger.workflow.domain.model.WorkstreamStatus
import kotlinx.serialization.Serializable

/**
 * Request body for POST /workstreams.
 *
 * Purpose: carries the client-supplied fields needed to initialise a new workstream.
 * Assumptions: all fields are provided and non-blank; validation is the use case's responsibility.
 * Invariants: [id], [createdAt], and [updatedAt] are intentionally absent — the server assigns them.
 *             Initial status is always NEW and is not accepted from the client.
 */
@Serializable
data class CreateWorkstreamRequest(
    val title: String,
    val description: String,
    val requester: String,
    val priority: Priority
)

/**
 * Request body for PATCH /workstreams/:id.
 *
 * Purpose: partial update carrying only the fields the client may mutate.
 * Assumptions: at least one field is non-null; a fully-null body is a no-op but not an error.
 * Requirements: the target workstream must already exist.
 * Invariants: [id], [createdAt], [requester] are immutable after creation and are not accepted here.
 *             [updatedAt] is always set by the server on any successful mutation.
 */
@Serializable
data class UpdateWorkstreamRequest(
    val status: WorkstreamStatus? = null,
    val title: String? = null,
    val description: String? = null,
    val priority: Priority? = null
)
