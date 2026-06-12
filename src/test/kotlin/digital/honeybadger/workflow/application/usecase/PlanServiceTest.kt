package digital.honeybadger.workflow.application.usecase

import digital.honeybadger.workflow.application.dto.UpsertPlanRequest
import digital.honeybadger.workflow.application.exception.PlanNotFoundException
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.application.port.outbound.ActivityRepository
import digital.honeybadger.workflow.application.port.outbound.EventPublisher
import digital.honeybadger.workflow.application.port.outbound.PlanRepository
import digital.honeybadger.workflow.application.port.outbound.WorkstreamRepository
import digital.honeybadger.workflow.domain.model.*
import io.mockk.*
import kotlinx.datetime.Instant
import kotlin.test.*

class PlanServiceTest {

    private val workstreamRepository = mockk<WorkstreamRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val activityRepository = mockk<ActivityRepository>()
    private val publisher = mockk<EventPublisher>(relaxed = true)
    private val service = PlanService(workstreamRepository, planRepository, publisher, activityRepository)

    private val now = Instant.parse("2024-06-01T12:00:00Z")

    private val sampleWorkstream = Workstream(
        id = "ws-1", title = "Sample", description = "Desc", requester = "alice",
        priority = Priority.MEDIUM, status = WorkstreamStatus.NEW,
        createdAt = now, updatedAt = now
    )

    private val sampleRequest = UpsertPlanRequest(
        goal = "Build the thing",
        nonGoals = listOf("Deploy to prod"),
        phases = listOf(Phase("p1", "Phase 1", "Do stuff", PhaseStatus.PENDING))
    )

    private val samplePlan = Plan(
        workstreamId = "ws-1",
        goal = "Build the thing",
        nonGoals = listOf("Deploy to prod"),
        phases = listOf(Phase("p1", "Phase 1", "Do stuff", PhaseStatus.PENDING))
    )

    @Test
    fun `upsert saves and returns plan bound to the given workstreamId`() {
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        val slot = slot<Plan>()
        every { planRepository.save(capture(slot)) } answers { slot.captured }

        val result = service.upsert("ws-1", sampleRequest)

        assertEquals("ws-1", result.workstreamId)
        assertEquals("Build the thing", result.goal)
        verify(exactly = 1) { planRepository.save(any()) }
    }

    @Test
    fun `upsert broadcasts plan update after saving`() {
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        every { planRepository.save(any()) } answers { firstArg() }

        service.upsert("ws-1", sampleRequest)

        verify(exactly = 1) { publisher.publishPlanUpdate(any()) }
    }

    @Test
    fun `upsert throws when goal is blank`() {
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        assertFailsWith<IllegalArgumentException> {
            service.upsert("ws-1", sampleRequest.copy(goal = "  "))
        }
    }

    @Test
    fun `upsert throws WorkstreamNotFoundException when workstream does not exist`() {
        every { workstreamRepository.findById("missing") } returns null
        assertFailsWith<WorkstreamNotFoundException> { service.upsert("missing", sampleRequest) }
        verify(exactly = 0) { planRepository.save(any()) }
    }

    @Test
    fun `get returns plan when found`() {
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        every { planRepository.findByWorkstreamId("ws-1") } returns samplePlan
        assertEquals(samplePlan, service.get("ws-1"))
    }

    @Test
    fun `get throws WorkstreamNotFoundException when workstream does not exist`() {
        every { workstreamRepository.findById("missing") } returns null
        assertFailsWith<WorkstreamNotFoundException> { service.get("missing") }
    }

    @Test
    fun `get throws PlanNotFoundException when workstream exists but has no plan`() {
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        every { planRepository.findByWorkstreamId("ws-1") } returns null
        assertFailsWith<PlanNotFoundException> { service.get("ws-1") }
    }

    @Test
    fun `readiness verificationReady is true when all phases complete and no blocking questions`() {
        val completePlan = samplePlan.copy(
            phases = listOf(Phase("p1", "Phase 1", "Do stuff", PhaseStatus.COMPLETE))
        )
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        every { planRepository.findByWorkstreamId("ws-1") } returns completePlan
        every { activityRepository.findByWorkstreamId("ws-1") } returns emptyList()

        val result = service.readiness("ws-1")

        assertTrue(result.allPhasesComplete)
        assertTrue(result.verificationReady)
        assertFalse(result.readyForReview)
        assertFalse(result.readyForPR)
    }

    @Test
    fun `readiness readyForReview is true when verificationReady and a VERIFICATION activity exists`() {
        val completePlan = samplePlan.copy(
            phases = listOf(Phase("p1", "Phase 1", "Do stuff", PhaseStatus.COMPLETE))
        )
        val verificationEvent = ActivityEvent("e1", "ws-1", "Runner", ActivityType.VERIFICATION, "All tests pass", now)
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        every { planRepository.findByWorkstreamId("ws-1") } returns completePlan
        every { activityRepository.findByWorkstreamId("ws-1") } returns listOf(verificationEvent)

        val result = service.readiness("ws-1")

        assertTrue(result.readyForReview)
        assertFalse(result.readyForPR)
    }

    @Test
    fun `readiness readyForPR is true when readyForReview and a REVIEW activity exists`() {
        val completePlan = samplePlan.copy(
            phases = listOf(Phase("p1", "Phase 1", "Do stuff", PhaseStatus.COMPLETE))
        )
        val activities = listOf(
            ActivityEvent("e1", "ws-1", "Runner", ActivityType.VERIFICATION, "Tests pass", now),
            ActivityEvent("e2", "ws-1", "Reviewer", ActivityType.REVIEW, "LGTM", now)
        )
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        every { planRepository.findByWorkstreamId("ws-1") } returns completePlan
        every { activityRepository.findByWorkstreamId("ws-1") } returns activities

        val result = service.readiness("ws-1")

        assertTrue(result.readyForReview)
        assertTrue(result.readyForPR)
    }

    @Test
    fun `readiness throws PlanNotFoundException when no plan attached`() {
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        every { planRepository.findByWorkstreamId("ws-1") } returns null
        assertFailsWith<PlanNotFoundException> { service.readiness("ws-1") }
    }
}
