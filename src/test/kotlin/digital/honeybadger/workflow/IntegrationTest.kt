package digital.honeybadger.workflow

import digital.honeybadger.workflow.application.dto.WorkstreamSummary
import digital.honeybadger.workflow.application.dto.*
import digital.honeybadger.workflow.domain.model.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntegrationTest {

    private fun ApplicationTestBuilder.setup() {
        application { module() }
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    @Test
    fun `full workstream lifecycle - create, plan, activity, readiness`() = testApplication {
        setup()
        val client = jsonClient()

        // Create workstream
        val ws = client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Implement auth", "Add JWT support", "alice", Priority.HIGH))
        }.body<Workstream>()
        assertNotNull(ws.id)
        assertEquals(WorkstreamStatus.NEW, ws.status)

        // Attach plan
        val plan = client.put("/workstreams/${ws.id}/plan") {
            contentType(ContentType.Application.Json)
            setBody(UpsertPlanRequest(
                goal = "Add JWT authentication",
                phases = listOf(Phase("p1", "Implementation", "Write the code", PhaseStatus.COMPLETE))
            ))
        }.body<Plan>()
        assertEquals("Add JWT authentication", plan.goal)

        // verificationReady — phases done, no blocking questions; review gates still need activities
        val readiness = client.get("/workstreams/${ws.id}/readiness").body<ReadinessState>()
        assertTrue(readiness.allPhasesComplete)
        assertTrue(readiness.verificationReady)
        assertFalse(readiness.readyForReview) // no VERIFICATION activity yet
        assertFalse(readiness.readyForPR)     // no REVIEW activity yet

        // Posting a VERIFICATION activity unlocks readyForReview
        client.post("/workstreams/${ws.id}/activity") {
            contentType(ContentType.Application.Json)
            setBody(CreateActivityEventRequest("Verification Runner", ActivityType.VERIFICATION, "All tests pass"))
        }
        val afterVerification = client.get("/workstreams/${ws.id}/readiness").body<ReadinessState>()
        assertTrue(afterVerification.readyForReview)
        assertFalse(afterVerification.readyForPR)

        // Posting a REVIEW activity unlocks readyForPR
        client.post("/workstreams/${ws.id}/activity") {
            contentType(ContentType.Application.Json)
            setBody(CreateActivityEventRequest("Diff Prosecutor", ActivityType.REVIEW, "LGTM, no scope drift"))
        }
        val afterReview = client.get("/workstreams/${ws.id}/readiness").body<ReadinessState>()
        assertTrue(afterReview.readyForPR)

        // Update status
        val updated = client.patch("/workstreams/${ws.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateWorkstreamRequest(status = WorkstreamStatus.EXECUTING))
        }.body<Workstream>()
        assertEquals(WorkstreamStatus.EXECUTING, updated.status)

        // Post activity events
        client.post("/workstreams/${ws.id}/activity") {
            contentType(ContentType.Application.Json)
            setBody(CreateActivityEventRequest("Context Scout", ActivityType.CONTEXT_DISCOVERY, "Found 42 files"))
        }
        client.post("/workstreams/${ws.id}/activity") {
            contentType(ContentType.Application.Json)
            setBody(CreateActivityEventRequest("Implementation Agent", ActivityType.IMPLEMENTATION, "Completed phase 1"))
        }

        // Verify activity list — 4 events total: verification, review, context scout, implementation
        val events = client.get("/workstreams/${ws.id}/activity").body<List<ActivityEvent>>()
        assertEquals(4, events.size)
        assertEquals("Context Scout", events[2].agentName)
        assertEquals("Implementation Agent", events[3].agentName)

        // Workstream appears in list
        val all = client.get("/workstreams").body<List<WorkstreamSummary>>()
        assertTrue(all.any { it.id == ws.id })
    }

    @Test
    fun `WebSocket client receives activity-created broadcast after joining room`() = testApplication {
        setup()
        val httpClient = jsonClient()
        val wsClient = createClient { install(WebSockets) }

        // Create workstream
        val ws = httpClient.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("WS test", "Desc", "bob", Priority.LOW))
        }.body<Workstream>()

        wsClient.webSocket("/ws") {
            // Join the workstream room
            send(Frame.Text("""{"type":"workstream:join","workstreamId":"${ws.id}"}"""))

            // Small delay to let the server handler process the join before we broadcast
            delay(50)

            // Post activity from a parallel coroutine so this handler can read the incoming frame
            launch {
                httpClient.post("/workstreams/${ws.id}/activity") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateActivityEventRequest("Plan Critic", ActivityType.PLANNING, "Blocking question raised"))
                }
            }

            // Drain frames until we find activity:created (join triggers a presence broadcast first)
            var activityText: String? = null
            for (f in incoming) {
                if (f !is Frame.Text) continue
                val text = f.readText()
                if (text.contains("activity:created")) { activityText = text; break }
            }
            assertNotNull(activityText, "Never received activity:created frame")
            assertTrue(activityText!!.contains("Plan Critic"), "Expected agent name in: $activityText")
        }
    }
}
