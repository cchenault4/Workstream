package digital.honeybadger.workflow.adapter.outbound.realtime

import digital.honeybadger.workflow.adapter.inbound.websocket.WebSocketSessionRegistry
import digital.honeybadger.workflow.domain.model.*
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketEventPublisherTest {

    private val registry = mockk<WebSocketSessionRegistry>(relaxed = true)
    private val publisher = WebSocketEventPublisher(registry, CoroutineScope(Dispatchers.Unconfined))

    private val now = Instant.parse("2024-06-01T12:00:00Z")

    @Test
    fun `publish broadcasts activity-created to the event's workstream room`() = runTest {
        val event = ActivityEvent("e1", "ws-1", "Context Scout", ActivityType.CONTEXT_DISCOVERY, "Found files", now)

        publisher.publish(event)

        coVerify { registry.broadcast("ws-1", match { it.contains("activity:created") }) }
    }

    @Test
    fun `publishWorkstreamUpdate broadcasts workstream-updated to workstream room and workstreams room`() = runTest {
        val ws = Workstream("ws-1", "Title", "Desc", "alice", Priority.HIGH, WorkstreamStatus.PLANNING, now, now)
        every { registry.activeRooms() } returns setOf("ws-1")

        publisher.publishWorkstreamUpdate(ws)

        coVerify { registry.broadcast("ws-1", match { it.contains("workstream:updated") && it.contains("\"active\":true") }) }
        coVerify { registry.broadcast("__workstreams__", match { it.contains("workstream:updated") }) }
    }

    @Test
    fun `publishWorkstreamUpdate sets active false when workstream has no active sessions`() = runTest {
        val ws = Workstream("ws-1", "Title", "Desc", "alice", Priority.HIGH, WorkstreamStatus.NEW, now, now)
        every { registry.activeRooms() } returns emptySet()

        publisher.publishWorkstreamUpdate(ws)

        coVerify { registry.broadcast("ws-1", match { it.contains("\"active\":false") }) }
    }

    @Test
    fun `publishPresenceUpdate broadcasts workstream-updated with explicitly provided active value`() = runTest {
        val ws = Workstream("ws-1", "Title", "Desc", "alice", Priority.HIGH, WorkstreamStatus.NEW, now, now)

        publisher.publishPresenceUpdate(ws, active = true)

        coVerify { registry.broadcast("ws-1", match { it.contains("\"active\":true") }) }
        coVerify { registry.broadcast("__workstreams__", match { it.contains("\"active\":true") }) }
    }

    @Test
    fun `publishPresenceUpdate does not call registry activeRooms`() = runTest {
        val ws = Workstream("ws-1", "Title", "Desc", "alice", Priority.HIGH, WorkstreamStatus.NEW, now, now)

        publisher.publishPresenceUpdate(ws, active = true)

        verify(exactly = 0) { registry.activeRooms() }
    }

    @Test
    fun `publishPlanUpdate broadcasts plan-updated to the plan's workstream room`() = runTest {
        val plan = Plan(workstreamId = "ws-1", goal = "Goal")

        publisher.publishPlanUpdate(plan)

        coVerify { registry.broadcast("ws-1", match { it.contains("plan:updated") }) }
    }
}
