package digital.honeybadger.workflow.domain.service

import digital.honeybadger.workflow.domain.model.ActivityEvent
import digital.honeybadger.workflow.domain.model.ActivityType
import digital.honeybadger.workflow.domain.model.PhaseStatus
import digital.honeybadger.workflow.domain.model.Plan
import digital.honeybadger.workflow.domain.model.QuestionType
import digital.honeybadger.workflow.domain.model.ReadinessState

/**
 * Pure function that derives readiness state from a plan and its activity history.
 * Has no side effects — safe to call anywhere.
 *
 * Gate semantics:
 *   verificationReady — plan is structurally complete (all phases done, no blocking questions).
 *   readyForReview    — verification has been run (a VERIFICATION activity exists).
 *   readyForPR        — review has been completed (a REVIEW activity exists).
 */
object ReadinessService {
    fun compute(plan: Plan, activities: List<ActivityEvent> = emptyList()): ReadinessState {
        val blockingQuestionsResolved = plan.openQuestions
            .none { it.type == QuestionType.BLOCKING && it.resolution == null }
        val allPhasesComplete = plan.phases.isNotEmpty() &&
            plan.phases.all { it.status == PhaseStatus.COMPLETE }
        val verificationReady = blockingQuestionsResolved && allPhasesComplete
        val readyForReview = verificationReady && activities.any { it.type == ActivityType.VERIFICATION }
        val readyForPR = readyForReview && activities.any { it.type == ActivityType.REVIEW }
        return ReadinessState(
            blockingQuestionsResolved = blockingQuestionsResolved,
            allPhasesComplete = allPhasesComplete,
            verificationReady = verificationReady,
            readyForReview = readyForReview,
            readyForPR = readyForPR
        )
    }
}
