package digital.honeybadger.workflow.domain.model

import kotlinx.serialization.Serializable

/**
 * Governs how an open question affects plan readiness.
 * Only BLOCKING questions gate progress; ASSUMABLE and DEFERRABLE are tracked
 * for visibility but do not affect readiness computation.
 */
enum class QuestionType { BLOCKING, ASSUMABLE, DEFERRABLE }

/** Execution state of a single phase within a plan. */
enum class PhaseStatus { PENDING, IN_PROGRESS, COMPLETE, BLOCKED }

/** A question that must be tracked (and potentially resolved) before the plan can progress. */
@Serializable
data class OpenQuestion(
    val id: String,
    val question: String,
    val type: QuestionType,
    val resolution: String? = null
)

/** A discrete unit of work within an implementation plan, executed sequentially by agents. */
@Serializable
data class Phase(
    val id: String,
    val name: String,
    val objective: String,
    val status: PhaseStatus
)

/**
 * Implementation plan attached to a workstream (at most one per workstream).
 * Captures intent (goal, nonGoals, assumptions), structure (phases), and
 * outstanding questions. Readiness is derived — see ReadinessService.
 */
@Serializable
data class Plan(
    val workstreamId: String,
    val goal: String,
    val nonGoals: List<String> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val openQuestions: List<OpenQuestion> = emptyList(),
    val phases: List<Phase> = emptyList(),
    val verificationPlan: List<String> = emptyList()
)
