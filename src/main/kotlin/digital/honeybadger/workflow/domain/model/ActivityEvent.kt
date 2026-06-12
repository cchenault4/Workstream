package digital.honeybadger.workflow.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Categorises the kind of work an agent performed when emitting an activity event. */
enum class ActivityType {
    CONTEXT_DISCOVERY, PLANNING, IMPLEMENTATION, REVIEW, VERIFICATION, HANDOFF
}

/**
 * An immutable record of work performed by a named agent on a workstream.
 * New events are broadcast in real time to WebSocket subscribers.
 */
@Serializable
data class ActivityEvent(
    val id: String,
    val workstreamId: String,
    val agentName: String,
    val type: ActivityType,
    val message: String,
    val createdAt: Instant
)
