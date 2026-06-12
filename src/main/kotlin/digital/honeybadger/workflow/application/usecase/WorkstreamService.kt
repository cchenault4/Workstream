package digital.honeybadger.workflow.application.usecase

import digital.honeybadger.workflow.application.dto.CreateWorkstreamRequest
import digital.honeybadger.workflow.application.dto.UpdateWorkstreamRequest
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.application.port.inbound.WorkstreamUseCase
import digital.honeybadger.workflow.application.port.outbound.WorkstreamRepository
import digital.honeybadger.workflow.domain.model.Workstream
import digital.honeybadger.workflow.domain.model.WorkstreamStatus
import kotlinx.datetime.Clock
import java.util.UUID

/** Concrete implementation of [WorkstreamUseCase]. Depends only on outbound ports and the clock. */
class WorkstreamService(
    private val repository: WorkstreamRepository,
    private val clock: Clock = Clock.System
) : WorkstreamUseCase {

    override fun create(request: CreateWorkstreamRequest): Workstream {
        // Capture now() once so createdAt and updatedAt are guaranteed equal on creation.
        val now = clock.now()
        return repository.save(
            Workstream(
                id = UUID.randomUUID().toString(),
                title = request.title,
                description = request.description,
                requester = request.requester,
                priority = request.priority,
                status = WorkstreamStatus.NEW,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override fun list(): List<Workstream> = repository.findAll()

    override fun getById(id: String): Workstream =
        repository.findById(id) ?: throw WorkstreamNotFoundException(id)

    override fun update(id: String, request: UpdateWorkstreamRequest): Workstream {
        val existing = repository.findById(id) ?: throw WorkstreamNotFoundException(id)
        return repository.save(
            existing.copy(
                status = request.status ?: existing.status,
                title = request.title ?: existing.title,
                description = request.description ?: existing.description,
                priority = request.priority ?: existing.priority,
                updatedAt = clock.now()
            )
        )
    }
}
