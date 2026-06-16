package digital.honeybadger.workflow.application.port.inbound

/**
 * Inbound port for workstream presence operations.
 *
 * Purpose: encapsulates the business rules around client join/leave lifecycle —
 *          updating the subscriber count and broadcasting presence changes to
 *          list-page subscribers.
 * Contract: the session must be registered in the broadcast registry before
 *           [workstreamJoined] is called, and removed before [workstreamLeft]
 *           is called, so that any active-status query reflects the new state.
 */
interface PresenceUseCase {

    /**
     * Records that a client has joined [workstreamId] and broadcasts active=true.
     *
     * Purpose: updates the "Active" badge on the list page when a viewer opens a workstream.
     */
    fun workstreamJoined(workstreamId: String)

    /**
     * Records that a client has left [workstreamId] and broadcasts the updated presence state.
     *
     * Purpose: updates the "Active" badge — active=true if other subscribers remain,
     *          active=false if the last viewer has gone.
     */
    fun workstreamLeft(workstreamId: String)

}
