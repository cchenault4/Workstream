package digital.honeybadger.workflow.domain.model

import kotlinx.serialization.Serializable

/**
 * Derived view of a plan's progress, computed by ReadinessService.
 * Not stored — recalculated on demand from the current plan state.
 */
@Serializable
data class ReadinessState(
    val blockingQuestionsResolved: Boolean,
    val allPhasesComplete: Boolean,
    val verificationReady: Boolean,
    val readyForReview: Boolean,
    val readyForPR: Boolean
)
