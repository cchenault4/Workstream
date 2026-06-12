package digital.honeybadger.workflow.application.usecase

import digital.honeybadger.workflow.application.dto.UpsertPlanRequest
import digital.honeybadger.workflow.application.exception.PlanNotFoundException
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.application.port.outbound.PlanRepository
import digital.honeybadger.workflow.application.port.outbound.WorkstreamRepository
import digital.honeybadger.workflow.domain.model.*
import io.mockk.*
import kotlinx.datetime.Instant
import kotlin.test.*

class PlanServiceTest {

    private val workstreamRepository = mockk<WorkstreamRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val service = PlanService(workstreamRepository, planRepository)

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
    fun `readiness computes state from current plan`() {
        val completePlan = samplePlan.copy(
            phases = listOf(Phase("p1", "Phase 1", "Do stuff", PhaseStatus.COMPLETE))
        )
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        every { planRepository.findByWorkstreamId("ws-1") } returns completePlan

        val result = service.readiness("ws-1")

        assertTrue(result.allPhasesComplete)
        assertTrue(result.verificationReady)
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
