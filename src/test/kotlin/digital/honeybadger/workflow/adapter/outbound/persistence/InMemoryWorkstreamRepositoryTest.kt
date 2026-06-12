package digital.honeybadger.workflow.adapter.outbound.persistence

import digital.honeybadger.workflow.domain.model.*
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryWorkstreamRepositoryTest {

    private val repo = InMemoryWorkstreamRepository()

    private val now = Instant.parse("2024-06-01T12:00:00Z")

    private fun workstream(id: String, title: String = "Title") = Workstream(
        id = id, title = title, description = "Desc", requester = "alice",
        priority = Priority.MEDIUM, status = WorkstreamStatus.NEW,
        createdAt = now, updatedAt = now
    )

    @Test
    fun `save and findById returns the saved workstream`() {
        val ws = workstream("ws-1")
        repo.save(ws)
        assertEquals(ws, repo.findById("ws-1"))
    }

    @Test
    fun `findById returns null when no workstream with that id exists`() {
        assertNull(repo.findById("missing"))
    }

    @Test
    fun `findAll returns all workstreams in insertion order`() {
        val ws1 = workstream("ws-1", "First")
        val ws2 = workstream("ws-2", "Second")
        repo.save(ws1)
        repo.save(ws2)
        assertEquals(listOf(ws1, ws2), repo.findAll())
    }

    @Test
    fun `save overwrites an existing workstream with the same id`() {
        repo.save(workstream("ws-1", "Original"))
        val updated = workstream("ws-1", "Updated")
        repo.save(updated)
        assertEquals(updated, repo.findById("ws-1"))
        assertEquals(1, repo.findAll().size)
    }
}
