package digital.honeybadger.workflow.application.usecase

import digital.honeybadger.workflow.application.port.inbound.PresenceUseCase
import digital.honeybadger.workflow.application.port.outbound.EventPublisher
import digital.honeybadger.workflow.application.port.outbound.PresenceRegistry
import digital.honeybadger.workflow.application.port.outbound.WorkstreamRepository
import org.slf4j.LoggerFactory

class PresenceService(
    private val workstreamRepository: WorkstreamRepository,
    private val presenceRegistry: PresenceRegistry,
    private val publisher: EventPublisher,
) : PresenceUseCase {

    private val log = LoggerFactory.getLogger(PresenceService::class.java)

    override fun workstreamJoined(workstreamId: String) {
        presenceRegistry.join(workstreamId)
        val workstream = workstreamRepository.findById(workstreamId)
            ?: return log.warn("workstream {} not found for presence broadcast", workstreamId)
        publisher.publishWorkstreamUpdate(workstream, active = true)
    }

    override fun workstreamLeft(workstreamId: String) {
        presenceRegistry.leave(workstreamId)
        val workstream = workstreamRepository.findById(workstreamId)
            ?: return log.warn("workstream {} not found for presence broadcast", workstreamId)
        publisher.publishWorkstreamUpdate(workstream, active = presenceRegistry.isActive(workstreamId))
    }

}
