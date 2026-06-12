package digital.honeybadger.workflow.domain.service

import digital.honeybadger.workflow.domain.model.*
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadinessServiceTest {

    private fun phase(status: PhaseStatus) =
        Phase(id = "p1", name = "Phase", objective = "Do thing", status = status)

    private fun question(type: QuestionType, resolution: String? = null) =
        OpenQuestion(id = "q1", question = "Why?", type = type, resolution = resolution)

    private fun plan(
        phases: List<Phase> = emptyList(),
        questions: List<OpenQuestion> = emptyList()
    ) = Plan(
        workstreamId = "ws1",
        goal = "Goal",
        phases = phases,
        openQuestions = questions
    )

    // --- blockingQuestionsResolved ---

    @Test
    fun `no questions means blocking questions are resolved`() {
        val state = ReadinessService.compute(plan())
        assertTrue(state.blockingQuestionsResolved)
    }

    @Test
    fun `unresolved blocking question means not resolved`() {
        val state = ReadinessService.compute(plan(questions = listOf(question(QuestionType.BLOCKING))))
        assertFalse(state.blockingQuestionsResolved)
    }

    @Test
    fun `resolved blocking question means resolved`() {
        val state = ReadinessService.compute(
            plan(questions = listOf(question(QuestionType.BLOCKING, resolution = "We decided X")))
        )
        assertTrue(state.blockingQuestionsResolved)
    }

    @Test
    fun `unresolved assumable question does not affect blockingQuestionsResolved`() {
        val state = ReadinessService.compute(plan(questions = listOf(question(QuestionType.ASSUMABLE))))
        assertTrue(state.blockingQuestionsResolved)
    }

    @Test
    fun `unresolved deferrable question does not affect blockingQuestionsResolved`() {
        val state = ReadinessService.compute(plan(questions = listOf(question(QuestionType.DEFERRABLE))))
        assertTrue(state.blockingQuestionsResolved)
    }

    // --- allPhasesComplete ---

    @Test
    fun `no phases means allPhasesComplete is false`() {
        val state = ReadinessService.compute(plan())
        assertFalse(state.allPhasesComplete)
    }

    @Test
    fun `all phases complete means allPhasesComplete is true`() {
        val state = ReadinessService.compute(plan(phases = listOf(phase(PhaseStatus.COMPLETE))))
        assertTrue(state.allPhasesComplete)
    }

    @Test
    fun `any phase not complete means allPhasesComplete is false`() {
        val state = ReadinessService.compute(
            plan(phases = listOf(phase(PhaseStatus.COMPLETE), phase(PhaseStatus.IN_PROGRESS)))
        )
        assertFalse(state.allPhasesComplete)
    }

    // --- cascading readiness ---

    @Test
    fun `verificationReady requires both blocking resolved and all phases complete`() {
        val allGood = plan(
            phases = listOf(phase(PhaseStatus.COMPLETE)),
            questions = listOf(question(QuestionType.BLOCKING, resolution = "Resolved"))
        )
        assertTrue(ReadinessService.compute(allGood).verificationReady)

        val missingPhases = plan(questions = listOf(question(QuestionType.BLOCKING, resolution = "Resolved")))
        assertFalse(ReadinessService.compute(missingPhases).verificationReady)

        val unresolvedQuestion = plan(
            phases = listOf(phase(PhaseStatus.COMPLETE)),
            questions = listOf(question(QuestionType.BLOCKING))
        )
        assertFalse(ReadinessService.compute(unresolvedQuestion).verificationReady)
    }

    @Test
    fun `readyForReview is false when verificationReady but no VERIFICATION activity exists`() {
        val readyPlan = plan(phases = listOf(phase(PhaseStatus.COMPLETE)))
        val state = ReadinessService.compute(readyPlan, emptyList())
        assertTrue(state.verificationReady)
        assertFalse(state.readyForReview)
        assertFalse(state.readyForPR)
    }

    @Test
    fun `readyForReview is true when verificationReady and a VERIFICATION activity exists`() {
        val now = Instant.parse("2024-06-01T12:00:00Z")
        val readyPlan = plan(phases = listOf(phase(PhaseStatus.COMPLETE)))
        val activities = listOf(ActivityEvent("e1", "ws1", "Runner", ActivityType.VERIFICATION, "All pass", now))
        val state = ReadinessService.compute(readyPlan, activities)
        assertTrue(state.readyForReview)
        assertFalse(state.readyForPR)
    }

    @Test
    fun `readyForPR is true when readyForReview and a REVIEW activity exists`() {
        val now = Instant.parse("2024-06-01T12:00:00Z")
        val readyPlan = plan(phases = listOf(phase(PhaseStatus.COMPLETE)))
        val activities = listOf(
            ActivityEvent("e1", "ws1", "Runner", ActivityType.VERIFICATION, "All pass", now),
            ActivityEvent("e2", "ws1", "Reviewer", ActivityType.REVIEW, "LGTM", now)
        )
        val state = ReadinessService.compute(readyPlan, activities)
        assertTrue(state.readyForReview)
        assertTrue(state.readyForPR)
    }

    @Test
    fun `not verificationReady means readyForReview and readyForPR are also false`() {
        val state = ReadinessService.compute(plan())
        assertFalse(state.verificationReady)
        assertFalse(state.readyForReview)
        assertFalse(state.readyForPR)
    }
}
