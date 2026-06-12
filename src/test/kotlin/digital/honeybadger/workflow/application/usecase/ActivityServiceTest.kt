package digital.honeybadger.workflow.application.usecase

import digital.honeybadger.workflow.application.dto.CreateActivityEventRequest
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.application.port.outbound.ActivityRepository
import digital.honeybadger.workflow.application.port.outbound.EventPublisher
import digital.honeybadger.workflow.application.port.outbound.WorkstreamRepository
import digital.honeybadger.workflow.domain.model.*
import io.mockk.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*

class ActivityServiceTest {

    private val workstreamRepository = mockk<WorkstreamRepository>()
    private val activityRepository = mockk<ActivityRepository>()
    private val publisher = mockk<EventPublisher>(relaxed = true)
    private val clock = mockk<Clock>()
    private val service = ActivityService(workstreamRepository, activityRepository, publisher, clock)

    private val now = Instant.parse("2024-06-01T12:00:00Z")

    private val sampleWorkstream = Workstream(
        id = "ws-1", title = "Sample", description = "Desc", requester = "alice",
        priority = Priority.MEDIUM, status = WorkstreamStatus.NEW,
        createdAt = now, updatedAt = now
    )

    @BeforeTest
    fun setUp() {
        every { clock.now() } returns now
    }

    @Test
    fun `add saves event with server-assigned id, correct workstreamId, and timestamp`() {
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        val slot = slot<ActivityEvent>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        val result = service.add("ws-1", CreateActivityEventRequest("Context Scout", ActivityType.CONTEXT_DISCOVERY, "Found files"))

        assertNotNull(result.id)
        assertEquals("ws-1", result.workstreamId)
        assertEquals("Context Scout", result.agentName)
        assertEquals(ActivityType.CONTEXT_DISCOVERY, result.type)
        assertEquals(now, result.createdAt)
        verify(exactly = 1) { activityRepository.save(any()) }
        verify(exactly = 1) { publisher.publish(any()) }
    }

    @Test
    fun `add publishes the saved event`() {
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        every { activityRepository.save(any()) } answers { firstArg() }
        val publishedSlot = slot<ActivityEvent>()
        every { publisher.publish(capture(publishedSlot)) } just runs

        service.add("ws-1", CreateActivityEventRequest("Agent", ActivityType.PLANNING, "msg"))

        assertEquals("ws-1", publishedSlot.captured.workstreamId)
    }

    @Test
    fun `add throws WorkstreamNotFoundException when workstream does not exist`() {
        every { workstreamRepository.findById("missing") } returns null
        assertFailsWith<WorkstreamNotFoundException> {
            service.add("missing", CreateActivityEventRequest("Agent", ActivityType.PLANNING, "msg"))
        }
        verify(exactly = 0) { activityRepository.save(any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun `list returns events for workstream in insertion order`() {
        val events = listOf(
            ActivityEvent("e1", "ws-1", "Agent", ActivityType.PLANNING, "msg1", now),
            ActivityEvent("e2", "ws-1", "Agent", ActivityType.REVIEW, "msg2", now)
        )
        every { workstreamRepository.findById("ws-1") } returns sampleWorkstream
        every { activityRepository.findByWorkstreamId("ws-1") } returns events

        assertEquals(events, service.list("ws-1"))
    }

    @Test
    fun `list throws WorkstreamNotFoundException when workstream does not exist`() {
        every { workstreamRepository.findById("missing") } returns null
        assertFailsWith<WorkstreamNotFoundException> { service.list("missing") }
    }
}
