package digital.honeybadger.workflow.adapter.outbound.realtime

import digital.honeybadger.workflow.adapter.inbound.websocket.WebSocketSessionRegistry
import digital.honeybadger.workflow.application.port.outbound.EventPublisher
import digital.honeybadger.workflow.domain.model.ActivityEvent
import digital.honeybadger.workflow.domain.model.Plan
import digital.honeybadger.workflow.domain.model.Workstream
import digital.honeybadger.workflow.appJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private const val WORKSTREAMS_ROOM = "__workstreams__"

class WebSocketEventPublisher(
    private val sessionRegistry: WebSocketSessionRegistry,
    private val scope: CoroutineScope,
    private val json: Json = appJson,
) : EventPublisher {

    override fun publish(event: ActivityEvent) {
        val message = json.encodeToString(WsServerMessage("activity:created", json.encodeToJsonElement(event)))
        scope.launch { sessionRegistry.broadcast(event.workstreamId, message) }
    }

    override fun publishWorkstreamUpdate(workstream: Workstream, active: Boolean) {
        val wsJson = json.encodeToJsonElement(workstream).jsonObject
        val payload = buildJsonObject {
            wsJson.entries.forEach { entry -> put(entry.key, entry.value) }
            put("active", active)
        }
        val message = json.encodeToString(WsServerMessage("workstream:updated", payload))
        scope.launch {
            sessionRegistry.broadcast(workstream.id, message)
            sessionRegistry.broadcast(WORKSTREAMS_ROOM, message)
        }
    }

    override fun publishPlanUpdate(plan: Plan) {
        val message = json.encodeToString(WsServerMessage("plan:updated", json.encodeToJsonElement(plan)))
        scope.launch { sessionRegistry.broadcast(plan.workstreamId, message) }
    }
}
