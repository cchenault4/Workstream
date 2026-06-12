package digital.honeybadger.workflow.application.usecase

import digital.honeybadger.workflow.application.dto.UpsertPlanRequest
import digital.honeybadger.workflow.application.exception.PlanNotFoundException
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.application.port.inbound.PlanUseCase
import digital.honeybadger.workflow.application.port.outbound.ActivityRepository
import digital.honeybadger.workflow.application.port.outbound.EventPublisher
import digital.honeybadger.workflow.application.port.outbound.PlanRepository
import digital.honeybadger.workflow.application.port.outbound.WorkstreamRepository
import digital.honeybadger.workflow.domain.model.Plan
import digital.honeybadger.workflow.domain.model.ReadinessState
import digital.honeybadger.workflow.domain.service.ReadinessService

/** Concrete implementation of [PlanUseCase]. Verifies workstream existence before every operation. */
class PlanService(
    private val workstreamRepository: WorkstreamRepository,
    private val planRepository: PlanRepository,
    private val publisher: EventPublisher,
    private val activityRepository: ActivityRepository
) : PlanUseCase {

    override fun upsert(workstreamId: String, request: UpsertPlanRequest): Plan {
        workstreamRepository.findById(workstreamId) ?: throw WorkstreamNotFoundException(workstreamId)
        require(request.goal.isNotBlank()) { "goal must not be blank" }
        val saved = planRepository.save(
            Plan(
                workstreamId = workstreamId,
                goal = request.goal,
                nonGoals = request.nonGoals,
                assumptions = request.assumptions,
                openQuestions = request.openQuestions,
                phases = request.phases,
                verificationPlan = request.verificationPlan
            )
        )
        publisher.publishPlanUpdate(saved)
        return saved
    }

    override fun get(workstreamId: String): Plan {
        workstreamRepository.findById(workstreamId) ?: throw WorkstreamNotFoundException(workstreamId)
        return planRepository.findByWorkstreamId(workstreamId) ?: throw PlanNotFoundException(workstreamId)
    }

    override fun readiness(workstreamId: String): ReadinessState {
        val plan = get(workstreamId)
        val activities = activityRepository.findByWorkstreamId(workstreamId)
        return ReadinessService.compute(plan, activities)
    }
}
