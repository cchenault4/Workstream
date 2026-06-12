package digital.honeybadger.workflow.application.port.inbound

import digital.honeybadger.workflow.application.dto.CreateActivityEventRequest
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.domain.model.ActivityEvent

/**
 * Inbound port for agent activity event operations on a workstream.
 *
 * Purpose: defines the application's activity API as seen by driving adapters.
 *          Activity events are immutable append-only records; there is no update or delete.
 * Assumptions: the workstream must exist before events can be appended to it.
 *              Real-time broadcast to WebSocket subscribers is a side effect of [add],
 *              handled by the implementation via the EventPublisher outbound port.
 */
interface ActivityUseCase {

    /**
     * Appends a new activity event to the workstream identified by [workstreamId].
     *
     * Purpose: records agent activity and triggers real-time broadcast to subscribers.
     * Requirements: the workstream identified by [workstreamId] must exist.
     * Invariants: the returned event has a server-assigned [ActivityEvent.id] and [ActivityEvent.createdAt].
     *             [ActivityEvent.workstreamId] always equals the supplied [workstreamId].
     *             The broadcast to WebSocket subscribers is best-effort; a failed broadcast
     *             does not roll back the persisted event.
     * @throws WorkstreamNotFoundException if no workstream with [workstreamId] exists.
     */
    fun add(workstreamId: String, request: CreateActivityEventRequest): ActivityEvent

    /**
     * Returns all activity events for the workstream identified by [workstreamId],
     * in insertion (chronological) order.
     *
     * Purpose: supports audit trails and activity feed display.
     * Requirements: the workstream identified by [workstreamId] must exist.
     * Invariants: never returns null; returns an empty list if no events have been posted yet.
     * @throws WorkstreamNotFoundException if no workstream with [workstreamId] exists.
     */
    fun list(workstreamId: String): List<ActivityEvent>
}
