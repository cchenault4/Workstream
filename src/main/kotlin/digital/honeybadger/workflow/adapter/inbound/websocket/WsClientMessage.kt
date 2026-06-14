package digital.honeybadger.workflow.adapter.inbound.websocket

import kotlinx.serialization.Serializable

/** Message sent from a WebSocket client to the server. */
@Serializable
data class WsClientMessage(
    val type: String,
    val workstreamId: String = ""
)
