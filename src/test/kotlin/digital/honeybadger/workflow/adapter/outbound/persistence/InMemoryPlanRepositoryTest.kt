package digital.honeybadger.workflow.adapter.outbound.persistence

import digital.honeybadger.workflow.domain.model.Phase
import digital.honeybadger.workflow.domain.model.PhaseStatus
import digital.honeybadger.workflow.domain.model.Plan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryPlanRepositoryTest {

    private val repo = InMemoryPlanRepository()

    private fun plan(workstreamId: String, goal: String = "Goal") = Plan(
        workstreamId = workstreamId,
        goal = goal,
        phases = listOf(Phase("p1", "Phase 1", "Do stuff", PhaseStatus.PENDING))
    )

    @Test
    fun `save and findByWorkstreamId returns the saved plan`() {
        val plan = plan("ws-1")
        repo.save(plan)
        assertEquals(plan, repo.findByWorkstreamId("ws-1"))
    }

    @Test
    fun `findByWorkstreamId returns null when no plan exists for that workstream`() {
        assertNull(repo.findByWorkstreamId("missing"))
    }

    @Test
    fun `save replaces an existing plan for the same workstreamId`() {
        repo.save(plan("ws-1", "Original goal"))
        val updated = plan("ws-1", "Updated goal")
        repo.save(updated)
        assertEquals(updated, repo.findByWorkstreamId("ws-1"))
    }
}
