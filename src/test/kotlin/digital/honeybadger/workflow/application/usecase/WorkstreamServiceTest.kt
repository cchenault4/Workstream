package digital.honeybadger.workflow.application.usecase

import digital.honeybadger.workflow.application.dto.CreateWorkstreamRequest
import digital.honeybadger.workflow.application.dto.UpdateWorkstreamRequest
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.application.port.outbound.WorkstreamRepository
import digital.honeybadger.workflow.domain.model.*
import io.mockk.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*

class WorkstreamServiceTest {

    private val repository = mockk<WorkstreamRepository>()
    private val clock = mockk<Clock>()
    private val service = WorkstreamService(repository, clock)

    private val now = Instant.parse("2024-06-01T12:00:00Z")
    private val later = Instant.parse("2024-06-01T13:00:00Z")

    private val sample = Workstream(
        id = "ws-1", title = "Sample", description = "Desc", requester = "alice",
        priority = Priority.MEDIUM, status = WorkstreamStatus.NEW,
        createdAt = now, updatedAt = now
    )

    @BeforeTest
    fun setUp() {
        every { clock.now() } returns now
    }

    @Test
    fun `create assigns server-generated id, sets status NEW, and sets equal timestamps`() {
        val slot = slot<Workstream>()
        every { repository.save(capture(slot)) } answers { slot.captured }

        val result = service.create(CreateWorkstreamRequest("Title", "Desc", "alice", Priority.HIGH))

        assertNotNull(result.id)
        assertEquals("Title", result.title)
        assertEquals(WorkstreamStatus.NEW, result.status)
        assertEquals(now, result.createdAt)
        assertEquals(now, result.updatedAt)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `list delegates to repository`() {
        every { repository.findAll() } returns listOf(sample)
        assertEquals(listOf(sample), service.list())
    }

    @Test
    fun `getById returns workstream when found`() {
        every { repository.findById("ws-1") } returns sample
        assertEquals(sample, service.getById("ws-1"))
    }

    @Test
    fun `getById throws WorkstreamNotFoundException when not found`() {
        every { repository.findById("missing") } returns null
        assertFailsWith<WorkstreamNotFoundException> { service.getById("missing") }
    }

    @Test
    fun `update applies non-null fields and refreshes updatedAt`() {
        every { clock.now() } returns later
        every { repository.findById("ws-1") } returns sample
        val slot = slot<Workstream>()
        every { repository.save(capture(slot)) } answers { slot.captured }

        val result = service.update("ws-1", UpdateWorkstreamRequest(status = WorkstreamStatus.PLANNING, title = "New Title"))

        assertEquals(WorkstreamStatus.PLANNING, result.status)
        assertEquals("New Title", result.title)
        assertEquals(sample.description, result.description)
        assertEquals(sample.priority, result.priority)
        assertEquals(now, result.createdAt)
        assertEquals(later, result.updatedAt)
    }

    @Test
    fun `update with all-null request still refreshes updatedAt`() {
        every { clock.now() } returns later
        every { repository.findById("ws-1") } returns sample
        val slot = slot<Workstream>()
        every { repository.save(capture(slot)) } answers { slot.captured }

        val result = service.update("ws-1", UpdateWorkstreamRequest())

        assertEquals(sample.status, result.status)
        assertEquals(sample.title, result.title)
        assertEquals(later, result.updatedAt)
    }

    @Test
    fun `update throws WorkstreamNotFoundException when not found`() {
        every { repository.findById("missing") } returns null
        assertFailsWith<WorkstreamNotFoundException> {
            service.update("missing", UpdateWorkstreamRequest(status = WorkstreamStatus.BLOCKED))
        }
    }
}
