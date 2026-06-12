package digital.honeybadger.workflow.adapter.inbound.http

import digital.honeybadger.workflow.adapter.outbound.persistence.InMemoryActivityRepository
import digital.honeybadger.workflow.adapter.outbound.persistence.InMemoryPlanRepository
import digital.honeybadger.workflow.adapter.outbound.persistence.InMemoryWorkstreamRepository
import digital.honeybadger.workflow.application.dto.*
import digital.honeybadger.workflow.application.port.outbound.EventPublisher
import digital.honeybadger.workflow.application.usecase.ActivityService
import digital.honeybadger.workflow.application.usecase.PlanService
import digital.honeybadger.workflow.application.usecase.WorkstreamService
import digital.honeybadger.workflow.configurePlugins
import digital.honeybadger.workflow.domain.model.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** No-op publisher so HTTP route tests focus on HTTP concerns, not broadcast behaviour. */
private class NoOpEventPublisher : EventPublisher {
    override fun publish(event: ActivityEvent) {}
    override fun publishWorkstreamUpdate(workstream: Workstream) {}
    override fun publishPlanUpdate(plan: Plan) {}
}

class HttpRoutesTest {

    private fun ApplicationTestBuilder.setup() {
        val workstreamRepo = InMemoryWorkstreamRepository()
        val planRepo = InMemoryPlanRepository()
        val activityRepo = InMemoryActivityRepository()
        val publisher = NoOpEventPublisher()
        val workstreamService = WorkstreamService(workstreamRepo, publisher)
        val planService = PlanService(workstreamRepo, planRepo, publisher, activityRepo)
        val activityService = ActivityService(workstreamRepo, activityRepo, publisher)
        application {
            configurePlugins()
            configureHttpRoutes(workstreamService, planService, activityService)
        }
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    // ── Workstreams ──────────────────────────────────────────────────────────

    @Test
    fun `POST workstreams returns 201 with server-assigned id and NEW status`() = testApplication {
        setup()
        val response = jsonClient().post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("My feature", "Desc", "alice", Priority.HIGH))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<Workstream>()
        assertNotNull(body.id)
        assertEquals("My feature", body.title)
        assertEquals(WorkstreamStatus.NEW, body.status)
    }

    @Test
    fun `GET workstreams returns 200 with empty list when none exist`() = testApplication {
        setup()
        val response = jsonClient().get("/workstreams")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList(), response.body<List<Workstream>>())
    }

    @Test
    fun `GET workstreams returns 200 with all created workstreams`() = testApplication {
        setup()
        val client = jsonClient()
        client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("First", "Desc", "alice", Priority.LOW))
        }
        client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Second", "Desc", "bob", Priority.HIGH))
        }
        val body = client.get("/workstreams").body<List<Workstream>>()
        assertEquals(2, body.size)
    }

    @Test
    fun `GET workstreams by id returns 200 when found`() = testApplication {
        setup()
        val client = jsonClient()
        val created = client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Title", "Desc", "alice", Priority.MEDIUM))
        }.body<Workstream>()

        val response = client.get("/workstreams/${created.id}")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(created.id, response.body<Workstream>().id)
    }

    @Test
    fun `GET workstreams by id returns 404 when not found`() = testApplication {
        setup()
        val response = jsonClient().get("/workstreams/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PATCH workstreams updates status and returns 200`() = testApplication {
        setup()
        val client = jsonClient()
        val created = client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Title", "Desc", "alice", Priority.MEDIUM))
        }.body<Workstream>()

        val response = client.patch("/workstreams/${created.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateWorkstreamRequest(status = WorkstreamStatus.PLANNING))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(WorkstreamStatus.PLANNING, response.body<Workstream>().status)
    }

    @Test
    fun `PATCH workstreams returns 404 when not found`() = testApplication {
        setup()
        val response = jsonClient().patch("/workstreams/does-not-exist") {
            contentType(ContentType.Application.Json)
            setBody(UpdateWorkstreamRequest(status = WorkstreamStatus.BLOCKED))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ── Plans ────────────────────────────────────────────────────────────────

    @Test
    fun `PUT plan returns 200 with saved plan`() = testApplication {
        setup()
        val client = jsonClient()
        val ws = client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Title", "Desc", "alice", Priority.MEDIUM))
        }.body<Workstream>()

        val response = client.put("/workstreams/${ws.id}/plan") {
            contentType(ContentType.Application.Json)
            setBody(UpsertPlanRequest(goal = "Ship the feature"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Ship the feature", response.body<Plan>().goal)
    }

    @Test
    fun `PUT plan returns 404 when workstream not found`() = testApplication {
        setup()
        val response = jsonClient().put("/workstreams/no-such-ws/plan") {
            contentType(ContentType.Application.Json)
            setBody(UpsertPlanRequest(goal = "Goal"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET plan returns 200 with plan after upsert`() = testApplication {
        setup()
        val client = jsonClient()
        val ws = client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Title", "Desc", "alice", Priority.MEDIUM))
        }.body<Workstream>()
        client.put("/workstreams/${ws.id}/plan") {
            contentType(ContentType.Application.Json)
            setBody(UpsertPlanRequest(goal = "The goal"))
        }

        val response = client.get("/workstreams/${ws.id}/plan")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("The goal", response.body<Plan>().goal)
    }

    @Test
    fun `GET plan returns 404 when no plan attached`() = testApplication {
        setup()
        val client = jsonClient()
        val ws = client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Title", "Desc", "alice", Priority.MEDIUM))
        }.body<Workstream>()

        val response = client.get("/workstreams/${ws.id}/plan")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET readiness returns 200 with computed state`() = testApplication {
        setup()
        val client = jsonClient()
        val ws = client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Title", "Desc", "alice", Priority.MEDIUM))
        }.body<Workstream>()
        client.put("/workstreams/${ws.id}/plan") {
            contentType(ContentType.Application.Json)
            setBody(UpsertPlanRequest(
                goal = "Goal",
                phases = listOf(Phase("p1", "Phase 1", "Do it", PhaseStatus.COMPLETE))
            ))
        }

        val response = client.get("/workstreams/${ws.id}/readiness")
        assertEquals(HttpStatusCode.OK, response.status)
        val readiness = response.body<ReadinessState>()
        assertEquals(true, readiness.allPhasesComplete)
        assertEquals(true, readiness.verificationReady)
    }

    // ── Activity ─────────────────────────────────────────────────────────────

    @Test
    fun `POST activity returns 201 with server-assigned id`() = testApplication {
        setup()
        val client = jsonClient()
        val ws = client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Title", "Desc", "alice", Priority.MEDIUM))
        }.body<Workstream>()

        val response = client.post("/workstreams/${ws.id}/activity") {
            contentType(ContentType.Application.Json)
            setBody(CreateActivityEventRequest("Context Scout", ActivityType.CONTEXT_DISCOVERY, "Found 12 files"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val event = response.body<ActivityEvent>()
        assertNotNull(event.id)
        assertEquals(ws.id, event.workstreamId)
        assertEquals("Context Scout", event.agentName)
    }

    @Test
    fun `POST activity returns 404 when workstream not found`() = testApplication {
        setup()
        val response = jsonClient().post("/workstreams/no-such-ws/activity") {
            contentType(ContentType.Application.Json)
            setBody(CreateActivityEventRequest("Agent", ActivityType.PLANNING, "msg"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET activity returns 200 with events in insertion order`() = testApplication {
        setup()
        val client = jsonClient()
        val ws = client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Title", "Desc", "alice", Priority.MEDIUM))
        }.body<Workstream>()
        client.post("/workstreams/${ws.id}/activity") {
            contentType(ContentType.Application.Json)
            setBody(CreateActivityEventRequest("Agent", ActivityType.PLANNING, "first"))
        }
        client.post("/workstreams/${ws.id}/activity") {
            contentType(ContentType.Application.Json)
            setBody(CreateActivityEventRequest("Agent", ActivityType.REVIEW, "second"))
        }

        val events = client.get("/workstreams/${ws.id}/activity").body<List<ActivityEvent>>()
        assertEquals(2, events.size)
        assertEquals("first", events[0].message)
        assertEquals("second", events[1].message)
    }

    // ── Validation (400s) ────────────────────────────────────────────────────

    @Test
    fun `POST workstreams returns 400 when title is blank`() = testApplication {
        setup()
        val response = jsonClient().post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("", "Desc", "alice", Priority.HIGH))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT plan returns 400 when goal is blank`() = testApplication {
        setup()
        val client = jsonClient()
        val ws = client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Title", "Desc", "alice", Priority.MEDIUM))
        }.body<Workstream>()

        val response = client.put("/workstreams/${ws.id}/plan") {
            contentType(ContentType.Application.Json)
            setBody(UpsertPlanRequest(goal = ""))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST activity returns 400 when agentName is blank`() = testApplication {
        setup()
        val client = jsonClient()
        val ws = client.post("/workstreams") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkstreamRequest("Title", "Desc", "alice", Priority.MEDIUM))
        }.body<Workstream>()

        val response = client.post("/workstreams/${ws.id}/activity") {
            contentType(ContentType.Application.Json)
            setBody(CreateActivityEventRequest("", ActivityType.PLANNING, "msg"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
