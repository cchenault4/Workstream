package digital.honeybadger.workflow.adapter.outbound.persistence

import digital.honeybadger.workflow.domain.model.ActivityEvent
import digital.honeybadger.workflow.domain.model.ActivityType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryActivityRepositoryTest {

    private val repo = InMemoryActivityRepository()

    private val now = Instant.parse("2024-06-01T12:00:00Z")

    private fun event(id: String, workstreamId: String, type: ActivityType = ActivityType.PLANNING) =
        ActivityEvent(id = id, workstreamId = workstreamId, agentName = "Agent", type = type, message = "msg", createdAt = now)

    @Test
    fun `save and findByWorkstreamId returns the saved event`() {
        val event = event("e1", "ws-1")
        repo.save(event)
        assertEquals(listOf(event), repo.findByWorkstreamId("ws-1"))
    }

    @Test
    fun `findByWorkstreamId returns empty list when no events exist for that workstream`() {
        assertTrue(repo.findByWorkstreamId("missing").isEmpty())
    }

    @Test
    fun `findByWorkstreamId returns events in insertion order`() {
        val e1 = event("e1", "ws-1", ActivityType.PLANNING)
        val e2 = event("e2", "ws-1", ActivityType.REVIEW)
        val e3 = event("e3", "ws-1", ActivityType.VERIFICATION)
        repo.save(e1)
        repo.save(e2)
        repo.save(e3)
        assertEquals(listOf(e1, e2, e3), repo.findByWorkstreamId("ws-1"))
    }

    @Test
    fun `events for different workstreams are stored independently`() {
        repo.save(event("e1", "ws-1"))
        repo.save(event("e2", "ws-2"))
        assertEquals(1, repo.findByWorkstreamId("ws-1").size)
        assertEquals(1, repo.findByWorkstreamId("ws-2").size)
    }
}
