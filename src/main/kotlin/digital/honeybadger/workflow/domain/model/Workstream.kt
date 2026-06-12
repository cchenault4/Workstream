package digital.honeybadger.workflow.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Relative importance of a workstream, used for prioritisation across the backlog. */
enum class Priority { LOW, MEDIUM, HIGH }

/**
 * Lifecycle state of a workstream. Transitions are not enforced by the domain —
 * callers set status explicitly via PATCH. BLOCKED may occur at any stage.
 */
enum class WorkstreamStatus { NEW, PLANNING, EXECUTING, REVIEWING, VERIFIED, BLOCKED }

/** Top-level unit of engineering work tracked through the agentic workflow. */
@Serializable
data class Workstream(
    val id: String,
    val title: String,
    val description: String,
    val requester: String,
    val priority: Priority,
    val status: WorkstreamStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)
