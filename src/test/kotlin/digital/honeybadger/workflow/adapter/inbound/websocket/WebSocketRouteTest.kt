package digital.honeybadger.workflow.adapter.inbound.websocket

import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import digital.honeybadger.workflow.configurePlugins
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

class WebSocketRouteTest {

    @Test
    fun `websocket endpoint accepts connections`() = testApplication {
        val registry = DefaultWebSocketSessionRegistry()
        application {
            configurePlugins()
            configureWebSocketRoutes(registry)
        }
        val client = createClient { install(WebSockets) }
        client.webSocket("/ws") {
            // connection established without error
        }
    }

    @Test
    fun `join and leave messages are processed without error`() = testApplication {
        val registry = DefaultWebSocketSessionRegistry()
        application {
            configurePlugins()
            configureWebSocketRoutes(registry)
        }
        val client = createClient { install(WebSockets) }
        client.webSocket("/ws") {
            send(Frame.Text("""{"type":"workstream:join","workstreamId":"ws-1"}"""))
            send(Frame.Text("""{"type":"workstream:leave","workstreamId":"ws-1"}"""))
            // no exception = success
        }
    }

    @Test
    fun `unknown message type is silently ignored`() = testApplication {
        val registry = DefaultWebSocketSessionRegistry()
        application {
            configurePlugins()
            configureWebSocketRoutes(registry)
        }
        val client = createClient { install(WebSockets) }
        client.webSocket("/ws") {
            send(Frame.Text("""{"type":"unknown:event","workstreamId":"ws-1"}"""))
            // no exception — connection remains open
        }
    }
}
