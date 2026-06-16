package digital.honeybadger.workflow.adapter.inbound.websocket

import io.ktor.websocket.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSocketSessionRegistryTest {

    private val registry = DefaultWebSocketSessionRegistry()

    private fun mockSession(): DefaultWebSocketSession {
        val session = mockk<DefaultWebSocketSession>()
        coEvery { session.send(any<Frame>()) } just runs
        return session
    }

    @Test
    fun `broadcast delivers message to all sessions in the room`() = runTest {
        val s1 = mockSession()
        val s2 = mockSession()
        registry.join("ws-1", s1)
        registry.join("ws-1", s2)

        registry.broadcast("ws-1", "hello")

        coVerify { s1.send(match { it is Frame.Text && it.readText() == "hello" }) }
        coVerify { s2.send(match { it is Frame.Text && it.readText() == "hello" }) }
    }

    @Test
    fun `broadcast only reaches sessions in the target room`() = runTest {
        val s1 = mockSession()
        val s2 = mockSession()
        registry.join("ws-1", s1)
        registry.join("ws-2", s2)

        registry.broadcast("ws-1", "hello")

        coVerify(exactly = 1) { s1.send(any<Frame>()) }
        coVerify(exactly = 0) { s2.send(any<Frame>()) }
    }

    @Test
    fun `leave removes session so it no longer receives broadcasts`() = runTest {
        val session = mockSession()
        registry.join("ws-1", session)
        registry.leave("ws-1", session)

        registry.broadcast("ws-1", "hello")

        coVerify(exactly = 0) { session.send(any()) }
    }

    @Test
    fun `broadcast to a room with no sessions is a no-op`() = runTest {
        registry.broadcast("ws-999", "hello") // must not throw
    }

    @Test
    fun `joining the same room twice does not cause duplicate delivery`() = runTest {
        val session = mockSession()
        registry.join("ws-1", session)
        registry.join("ws-1", session)

        registry.broadcast("ws-1", "hello")

        coVerify(exactly = 1) { session.send(any()) }
    }

    @Test
    fun `leaveAll removes session from all joined rooms and returns their ids`() = runTest {
        val session = mockSession()
        registry.join("ws-1", session)
        registry.join("ws-2", session)
        registry.join("__workstreams__", session)

        val rooms = registry.leaveAll(session)

        assertEquals(setOf("ws-1", "ws-2", "__workstreams__"), rooms)
        registry.broadcast("ws-1", "hello")
        registry.broadcast("ws-2", "hello")
        coVerify(exactly = 0) { session.send(any()) }
    }

    @Test
    fun `leaveAll returns empty set when session was not in any room`() = runTest {
        val session = mockSession()
        assertTrue(registry.leaveAll(session).isEmpty())
    }

    @Test
    fun `leaveAll only removes the target session leaving others intact`() = runTest {
        val s1 = mockSession()
        val s2 = mockSession()
        registry.join("ws-1", s1)
        registry.join("ws-1", s2)

        registry.leaveAll(s1)
        registry.broadcast("ws-1", "hello")

        coVerify(exactly = 0) { s1.send(any()) }
        coVerify(exactly = 1) { s2.send(any()) }
    }

    @Test
    fun `dead session is evicted after a failed send and skipped on subsequent broadcasts`() = runTest {
        val dead = mockk<DefaultWebSocketSession>()
        val alive = mockSession()
        coEvery { dead.send(any<Frame>()) } throws Exception("connection closed")

        registry.join("ws-1", dead)
        registry.join("ws-1", alive)

        registry.broadcast("ws-1", "first")  // dead session fails and is evicted
        registry.broadcast("ws-1", "second") // only alive session receives this one

        coVerify(exactly = 2) { alive.send(any<Frame>()) }
        coVerify(exactly = 1) { dead.send(any<Frame>()) } // attempted only once, then evicted
    }
}
