package app.shbd.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RationSurveyStepsTest {
    private val cooks = RationSurveyAnswers()
    private val noCook = RationSurveyAnswers(cookingTime = CookingTime.NONE)

    @Test
    fun sevenStepsByDefault() {
        assertEquals(7, RationSurveySteps.visibleSteps(cooks).size)
        assertEquals(RationStepPosition(1, 7), RationSurveySteps.position(RationSurveyStep.MEALS, cooks))
    }

    @Test
    fun noCooking_skipsKitchen() {
        val steps = RationSurveySteps.visibleSteps(noCook)
        assertEquals(6, steps.size)
        assertFalse(RationSurveyStep.KITCHEN in steps)
        assertEquals(RationSurveyStep.CUISINES, RationSurveySteps.next(RationSurveyStep.COOKING, noCook))
        assertEquals(RationStepPosition(5, 6), RationSurveySteps.position(RationSurveyStep.CUISINES, noCook))
        assertEquals(RationSurveyStep.COOKING, RationSurveySteps.previous(RationSurveyStep.CUISINES, noCook))
    }

    @Test
    fun cooking_goesThroughKitchen() {
        assertEquals(RationSurveyStep.KITCHEN, RationSurveySteps.next(RationSurveyStep.COOKING, cooks))
        assertEquals(RationSurveyStep.KITCHEN, RationSurveySteps.previous(RationSurveyStep.CUISINES, cooks))
    }

    @Test
    fun canProceed_needsAMeal() {
        assertFalse(RationSurveySteps.canProceed(RationSurveyStep.MEALS, cooks.copy(meals = emptySet())))
        assertTrue(RationSurveySteps.canProceed(RationSurveyStep.MEALS, cooks))
        assertTrue(RationSurveySteps.canProceed(RationSurveyStep.KITCHEN, cooks.copy(kitchen = emptySet())))
        assertTrue(RationSurveySteps.canProceed(RationSurveyStep.RESTRICTIONS, cooks))
    }

    @Test
    fun previous_fromFirstStepIsNull() {
        assertNull(RationSurveySteps.previous(RationSurveyStep.MEALS, cooks))
        assertNull(RationSurveySteps.previous(RationSurveyStep.INTRO, cooks))
        assertNull(RationSurveySteps.previous(RationSurveyStep.SUMMARY, cooks))
    }

    @Test
    fun deliveryIsLast_andLeadsToSummary() {
        assertTrue(RationSurveySteps.isLast(RationSurveyStep.DELIVERY, cooks))
        assertEquals(RationSurveyStep.SUMMARY, RationSurveySteps.next(RationSurveyStep.DELIVERY, cooks))
        assertNull(RationSurveySteps.position(RationSurveyStep.SUMMARY, cooks))
    }

    @Test
    fun state_startNextFinishEdit() {
        var s = RationSurveyState().start()
        assertEquals(RationSurveyStep.MEALS, s.step)
        s = s.copy(answers = s.answers.copy(meals = emptySet())).next()
        assertEquals(RationSurveyStep.MEALS, s.step)
        s = s.copy(step = RationSurveyStep.DELIVERY, answers = RationSurveyAnswers()).next()
        assertEquals(RationSurveyStep.SUMMARY, s.step)
        assertTrue(s.completed)
        s = s.editAnswers()
        assertEquals(RationSurveyStep.MEALS, s.step)
        assertTrue(s.completed)
    }

    @Test
    fun state_normalizedFixesInconsistentStep() {
        assertEquals(RationSurveyStep.INTRO, RationSurveyState(step = RationSurveyStep.SUMMARY).normalized().step)
        assertEquals(RationSurveyStep.SUMMARY, RationSurveyState(completed = true).normalized().step)
    }
}
