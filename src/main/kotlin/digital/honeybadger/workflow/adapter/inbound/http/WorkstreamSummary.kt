package digital.honeybadger.workflow.adapter.inbound.http

import digital.honeybadger.workflow.domain.model.Priority
import digital.honeybadger.workflow.domain.model.Workstream
import digital.honeybadger.workflow.domain.model.WorkstreamStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** List-endpoint projection of a [Workstream] that includes real-time presence state. */
@Serializable
data class WorkstreamSummary(
    val id: String,
    val title: String,
    val description: String,
    val requester: String,
    val priority: Priority,
    val status: WorkstreamStatus,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Workstream.toSummary(active: Boolean) = WorkstreamSummary(
    id = id, title = title, description = description, requester = requester,
    priority = priority, status = status, active = active,
    createdAt = createdAt, updatedAt = updatedAt,
)
