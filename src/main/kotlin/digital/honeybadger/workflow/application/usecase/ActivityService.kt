package digital.honeybadger.workflow.application.usecase

import digital.honeybadger.workflow.application.dto.CreateActivityEventRequest
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.application.port.inbound.ActivityUseCase
import digital.honeybadger.workflow.application.port.outbound.ActivityRepository
import digital.honeybadger.workflow.application.port.outbound.EventPublisher
import digital.honeybadger.workflow.application.port.outbound.WorkstreamRepository
import digital.honeybadger.workflow.domain.model.ActivityEvent
import kotlinx.datetime.Clock
import java.util.UUID

/** Concrete implementation of [ActivityUseCase]. Saves the event before publishing so a
 *  failed broadcast never prevents the event from being persisted. */
class ActivityService(
    private val workstreamRepository: WorkstreamRepository,
    private val activityRepository: ActivityRepository,
    private val publisher: EventPublisher,
    private val clock: Clock = Clock.System
) : ActivityUseCase {

    override fun add(workstreamId: String, request: CreateActivityEventRequest): ActivityEvent {
        workstreamRepository.findById(workstreamId) ?: throw WorkstreamNotFoundException(workstreamId)
        val saved = activityRepository.save(
            ActivityEvent(
                id = UUID.randomUUID().toString(),
                workstreamId = workstreamId,
                agentName = request.agentName,
                type = request.type,
                message = request.message,
                createdAt = clock.now()
            )
        )
        publisher.publish(saved)
        return saved
    }

    override fun list(workstreamId: String): List<ActivityEvent> {
        workstreamRepository.findById(workstreamId) ?: throw WorkstreamNotFoundException(workstreamId)
        return activityRepository.findByWorkstreamId(workstreamId)
    }
}
