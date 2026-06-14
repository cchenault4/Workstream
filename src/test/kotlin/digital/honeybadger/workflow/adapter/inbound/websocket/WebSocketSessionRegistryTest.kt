package digital.honeybadger.workflow.adapter.inbound.websocket

import io.ktor.websocket.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
    fun `broadcastAll delivers message to all tracked sessions`() = runTest {
        val s1 = mockSession()
        val s2 = mockSession()
        registry.track(s1)
        registry.track(s2)

        registry.broadcastAll("hello")

        coVerify { s1.send(match { it is Frame.Text && it.readText() == "hello" }) }
        coVerify { s2.send(match { it is Frame.Text && it.readText() == "hello" }) }
    }

    @Test
    fun `broadcastAll skips untracked sessions`() = runTest {
        val tracked = mockSession()
        val untracked = mockSession()
        registry.track(tracked)
        // untracked is never registered

        registry.broadcastAll("hello")

        coVerify(exactly = 1) { tracked.send(any<Frame>()) }
        coVerify(exactly = 0) { untracked.send(any<Frame>()) }
    }

    @Test
    fun `broadcastAll evicts dead sessions`() = runTest {
        val dead = mockk<DefaultWebSocketSession>()
        val alive = mockSession()
        coEvery { dead.send(any<Frame>()) } throws Exception("closed")
        registry.track(dead)
        registry.track(alive)

        registry.broadcastAll("first")   // dead fails and is evicted
        registry.broadcastAll("second")  // only alive receives this

        coVerify(exactly = 2) { alive.send(any<Frame>()) }
        coVerify(exactly = 1) { dead.send(any<Frame>()) }
    }

    @Test
    fun `roomSize reflects join and leave`() = runTest {
        val s1 = mockSession()
        val s2 = mockSession()
        registry.join("ws-1", s1)
        registry.join("ws-1", s2)
        assert(registry.roomSize("ws-1") == 2)
        registry.leave("ws-1", s1)
        assert(registry.roomSize("ws-1") == 1)
        registry.leave("ws-1", s2)
        assert(registry.roomSize("ws-1") == 0)
    }

    @Test
    fun `roomSize returns 0 for unknown workstream`() = runTest {
        assert(registry.roomSize("no-such-room") == 0)
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
