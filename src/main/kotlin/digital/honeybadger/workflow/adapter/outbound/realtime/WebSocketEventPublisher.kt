package digital.honeybadger.workflow.adapter.outbound.realtime

import digital.honeybadger.workflow.adapter.inbound.websocket.WebSocketSessionRegistry
import digital.honeybadger.workflow.application.port.outbound.EventPublisher
import digital.honeybadger.workflow.domain.model.ActivityEvent
import digital.honeybadger.workflow.domain.model.Plan
import digital.honeybadger.workflow.domain.model.Workstream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Outbound adapter implementing [EventPublisher] via [WebSocketSessionRegistry].
 *
 * Purpose: bridges the synchronous use-case layer to the async WebSocket transport.
 *          Each publish call launches a fire-and-forget coroutine in [dispatcher] so
 *          the calling thread is never blocked by I/O.
 * Assumptions: broadcast failures (closed sessions) are swallowed inside the registry;
 *              this class does not observe or retry them.
 * Invariants: the publish methods return immediately; delivery is best-effort and async.
 */
class WebSocketEventPublisher(
    private val registry: WebSocketSessionRegistry,
    private val dispatcher: CoroutineDispatcher,
    private val json: Json = Json
) : EventPublisher {

    override fun publish(event: ActivityEvent) =
        broadcast(event.workstreamId, "activity:created", json.encodeToJsonElement(event))

    override fun publishWorkstreamUpdate(workstream: Workstream) =
        broadcast(workstream.id, "workstream:updated", json.encodeToJsonElement(workstream))

    override fun publishPlanUpdate(plan: Plan) =
        broadcast(plan.workstreamId, "plan:updated", json.encodeToJsonElement(plan))

    private fun broadcast(workstreamId: String, type: String, data: kotlinx.serialization.json.JsonElement) {
        val message = json.encodeToString(WsServerMessage(type, data))
        CoroutineScope(dispatcher).launch { registry.broadcast(workstreamId, message) }
    }
}
