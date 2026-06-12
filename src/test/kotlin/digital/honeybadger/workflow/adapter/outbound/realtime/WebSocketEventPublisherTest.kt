package digital.honeybadger.workflow.adapter.outbound.realtime

import digital.honeybadger.workflow.adapter.inbound.websocket.WebSocketSessionRegistry
import digital.honeybadger.workflow.domain.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketEventPublisherTest {

    // relaxed = true avoids MockK bypassing the constructor on a concrete class,
    // which would leave internal fields null and crash on coEvery setup.
    private val registry = mockk<WebSocketSessionRegistry>(relaxed = true)
    private val publisher = WebSocketEventPublisher(registry, Dispatchers.Unconfined)

    private val now = Instant.parse("2024-06-01T12:00:00Z")

    @Test
    fun `publish broadcasts activity-created to the event's workstream room`() = runTest {
        val event = ActivityEvent("e1", "ws-1", "Context Scout", ActivityType.CONTEXT_DISCOVERY, "Found files", now)

        publisher.publish(event)

        coVerify { registry.broadcast("ws-1", match { it.contains("activity:created") }) }
    }

    @Test
    fun `publishWorkstreamUpdate broadcasts workstream-updated to the workstream's room`() = runTest {
        val ws = Workstream("ws-1", "Title", "Desc", "alice", Priority.HIGH, WorkstreamStatus.PLANNING, now, now)

        publisher.publishWorkstreamUpdate(ws)

        coVerify { registry.broadcast("ws-1", match { it.contains("workstream:updated") }) }
    }

    @Test
    fun `publishPlanUpdate broadcasts plan-updated to the plan's workstream room`() = runTest {
        val plan = Plan(workstreamId = "ws-1", goal = "Goal")

        publisher.publishPlanUpdate(plan)

        coVerify { registry.broadcast("ws-1", match { it.contains("plan:updated") }) }
    }
}
