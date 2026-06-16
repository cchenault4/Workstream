package digital.honeybadger.workflow.adapter.inbound.websocket

import digital.honeybadger.workflow.application.port.inbound.PresenceUseCase
import digital.honeybadger.workflow.configurePlugins
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import io.mockk.*
import kotlin.test.Test

class WebSocketRouteTest {

    private fun mockPresence(): PresenceUseCase = mockk(relaxed = true)

    @Test
    fun `websocket endpoint accepts connections`() = testApplication {
        val registry = DefaultWebSocketSessionRegistry()
        application {
            configurePlugins()
            configureWebSocketRoutes(registry, mockPresence())
        }
        createClient { install(WebSockets) }.webSocket("/ws") {
            // connection established without error
        }
    }

    @Test
    fun `join and leave messages are processed without error`() = testApplication {
        val presenceUseCase = mockPresence()
        val registry = DefaultWebSocketSessionRegistry()
        application {
            configurePlugins()
            configureWebSocketRoutes(registry, presenceUseCase)
        }
        createClient { install(WebSockets) }.webSocket("/ws") {
            send(Frame.Text("""{"type":"workstream:join","workstreamId":"ws-1"}"""))
            send(Frame.Text("""{"type":"workstream:leave","workstreamId":"ws-1"}"""))
        }
        verify { presenceUseCase.workstreamJoined("ws-1") }
        verify { presenceUseCase.workstreamLeft("ws-1") }
    }

    @Test
    fun `unknown message type is silently ignored`() = testApplication {
        val registry = DefaultWebSocketSessionRegistry()
        application {
            configurePlugins()
            configureWebSocketRoutes(registry, mockPresence())
        }
        createClient { install(WebSockets) }.webSocket("/ws") {
            send(Frame.Text("""{"type":"unknown:event","workstreamId":"ws-1"}``"""))
        }
    }
}
