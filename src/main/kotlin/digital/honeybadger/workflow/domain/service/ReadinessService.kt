package digital.honeybadger.workflow.domain.service

import digital.honeybadger.workflow.domain.model.PhaseStatus
import digital.honeybadger.workflow.domain.model.Plan
import digital.honeybadger.workflow.domain.model.QuestionType
import digital.honeybadger.workflow.domain.model.ReadinessState

/**
 * Pure function that derives readiness state from a plan snapshot.
 * Has no dependencies and no side effects — safe to call anywhere.
 */
object ReadinessService {
    fun compute(plan: Plan): ReadinessState {
        val blockingQuestionsResolved = plan.openQuestions
            .none { it.type == QuestionType.BLOCKING && it.resolution == null }
        val allPhasesComplete = plan.phases.isNotEmpty() &&
            plan.phases.all { it.status == PhaseStatus.COMPLETE }
        val verificationReady = blockingQuestionsResolved && allPhasesComplete
        return ReadinessState(
            blockingQuestionsResolved = blockingQuestionsResolved,
            allPhasesComplete = allPhasesComplete,
            verificationReady = verificationReady,
            readyForReview = verificationReady,
            readyForPR = verificationReady
        )
    }
}
