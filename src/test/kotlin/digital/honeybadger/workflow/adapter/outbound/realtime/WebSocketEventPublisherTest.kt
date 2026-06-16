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

    private val sessionRegistry = mockk<WebSocketSessionRegistry>(relaxed = true)
    private val publisher = WebSocketEventPublisher(sessionRegistry, CoroutineScope(Dispatchers.Unconfined))

    private val now = Instant.parse("2024-06-01T12:00:00Z")

    @Test
    fun `publish broadcasts activity-created to the event's workstream room`() = runTest {
        val event = ActivityEvent("e1", "ws-1", "Context Scout", ActivityType.CONTEXT_DISCOVERY, "Found files", now)

        publisher.publish(event)

        coVerify { sessionRegistry.broadcast("ws-1", match { it.contains("activity:created") }) }
    }

    @Test
    fun `publishWorkstreamUpdate broadcasts workstream-updated with active=true to workstream room and workstreams room`() = runTest {
        val ws = Workstream("ws-1", "Title", "Desc", "alice", Priority.HIGH, WorkstreamStatus.PLANNING, now, now)

        publisher.publishWorkstreamUpdate(ws, active = true)

        coVerify { sessionRegistry.broadcast("ws-1", match { it.contains("workstream:updated") && it.contains("\"active\":true") }) }
        coVerify { sessionRegistry.broadcast("__workstreams__", match { it.contains("workstream:updated") }) }
    }

    @Test
    fun `publishWorkstreamUpdate broadcasts workstream-updated with active=false`() = runTest {
        val ws = Workstream("ws-1", "Title", "Desc", "alice", Priority.HIGH, WorkstreamStatus.NEW, now, now)

        publisher.publishWorkstreamUpdate(ws, active = false)

        coVerify { sessionRegistry.broadcast("ws-1", match { it.contains("\"active\":false") }) }
    }

    @Test
    fun `publishPlanUpdate broadcasts plan-updated to the plan's workstream room`() = runTest {
        val plan = Plan(workstreamId = "ws-1", goal = "Goal")

        publisher.publishPlanUpdate(plan)

        coVerify { sessionRegistry.broadcast("ws-1", match { it.contains("plan:updated") }) }
    }
}
