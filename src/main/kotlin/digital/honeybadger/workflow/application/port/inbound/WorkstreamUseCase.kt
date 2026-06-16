package digital.honeybadger.workflow.application.port.inbound

import digital.honeybadger.workflow.application.dto.CreateWorkstreamRequest
import digital.honeybadger.workflow.application.dto.UpdateWorkstreamRequest
import digital.honeybadger.workflow.application.dto.WorkstreamSummary
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.domain.model.Workstream

/**
 * Inbound port for all workstream lifecycle operations.
 *
 * Purpose: defines the application's workstream API as seen by driving adapters (HTTP, tests).
 *          Implementations coordinate persistence via outbound ports; no framework code here.
 * Assumptions: IDs are opaque strings. Callers must not assume any particular ID format.
 */
interface WorkstreamUseCase {

    /**
     * Creates a new workstream from the supplied request.
     *
     * Purpose: initialises the workstream with status NEW and server-assigned id/timestamps.
     * Requirements: [request] fields must be non-blank; enforcement is the implementation's responsibility.
     * Invariants: the returned workstream has status NEW, and createdAt == updatedAt.
     */
    fun create(request: CreateWorkstreamRequest): Workstream

    /**
     * Returns all workstreams in the system as summaries, in insertion order.
     *
     * Purpose: supports list views; each summary includes the current real-time presence
     *          state so callers do not need to query presence separately.
     * Invariants: never returns null; returns an empty list when no workstreams exist.
     */
    fun list(): List<WorkstreamSummary>

    /**
     * Returns the workstream with the given [id].
     *
     * Purpose: supports fetching a single workstream for display or downstream operations.
     * Requirements: [id] must identify an existing workstream.
     * @throws WorkstreamNotFoundException if no workstream with [id] exists.
     */
    fun getById(id: String): Workstream

    /**
     * Applies a partial update to the workstream identified by [id].
     *
     * Purpose: allows callers to mutate mutable fields (status, title, description, priority).
     *          Null fields in [request] are treated as "no change".
     * Requirements: [id] must identify an existing workstream.
     * Invariants: [updatedAt] is always refreshed on any successful call, even if no field value changed.
     * @throws WorkstreamNotFoundException if no workstream with [id] exists.
     */
    fun update(id: String, request: UpdateWorkstreamRequest): Workstream
}
