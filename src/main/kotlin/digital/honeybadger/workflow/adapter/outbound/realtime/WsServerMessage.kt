package digital.honeybadger.workflow.adapter.outbound.realtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Message broadcast from the server to subscribed WebSocket clients.
 *
 * [type] is one of: "activity:created", "workstream:updated", "plan:updated".
 * [data] contains the full serialized domain object for that event type.
 */
@Serializable
data class WsServerMessage(
    val type: String,
    val data: JsonElement
)
