package digital.honeybadger.workflow.application.usecase

import digital.honeybadger.workflow.application.dto.UpsertPlanRequest
import digital.honeybadger.workflow.application.exception.PlanNotFoundException
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.application.port.inbound.PlanUseCase
import digital.honeybadger.workflow.application.port.outbound.PlanRepository
import digital.honeybadger.workflow.application.port.outbound.WorkstreamRepository
import digital.honeybadger.workflow.domain.model.Plan
import digital.honeybadger.workflow.domain.model.ReadinessState
import digital.honeybadger.workflow.domain.service.ReadinessService

/** Concrete implementation of [PlanUseCase]. Verifies workstream existence before every operation. */
class PlanService(
    private val workstreamRepository: WorkstreamRepository,
    private val planRepository: PlanRepository
) : PlanUseCase {

    override fun upsert(workstreamId: String, request: UpsertPlanRequest): Plan {
        workstreamRepository.findById(workstreamId) ?: throw WorkstreamNotFoundException(workstreamId)
        return planRepository.save(
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
    }

    override fun get(workstreamId: String): Plan {
        workstreamRepository.findById(workstreamId) ?: throw WorkstreamNotFoundException(workstreamId)
        return planRepository.findByWorkstreamId(workstreamId) ?: throw PlanNotFoundException(workstreamId)
    }

    override fun readiness(workstreamId: String): ReadinessState =
        // Delegates to get() to avoid duplicating workstream/plan existence checks.
        ReadinessService.compute(get(workstreamId))
}
