package app.shbd.ui.ration

import android.app.Application
import android.os.Looper
import app.shbd.models.RationMeal
import app.shbd.models.RationSurveyState
import app.shbd.models.RationSurveyStep
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** ViewModel on a fake store: every change is saved, finish/edit keep the completed flag right. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RationSurveyViewModelTest {
    private val saved = mutableListOf<RationSurveyState>()

    private fun vm(initial: RationSurveyState = RationSurveyState()) =
        RationSurveyViewModel(flowOf(initial), { saved += it }, flowOf(1_850)).also { idle() }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun loadsSavedStepAndCalories() {
        val vm = vm(RationSurveyState(step = RationSurveyStep.COOKING))
        val ui = vm.state.value
        assertTrue(ui.loaded)
        assertEquals(RationSurveyStep.COOKING, ui.step)
        assertEquals(4, ui.position?.number)
        assertEquals(1_850, ui.dailyCalories)
    }

    @Test
    fun everyChangeIsSavedWithStep() {
        val vm = vm()
        vm.start(); idle()
        vm.update { it.copy(meals = setOf(RationMeal.BREAKFAST, RationMeal.DINNER)) }; idle()
        val last = saved.last()
        assertEquals(RationSurveyStep.MEALS, last.step)
        assertEquals(setOf(RationMeal.BREAKFAST, RationMeal.DINNER), last.answers.meals)
    }

    @Test
    fun nextBlockedWithoutMeals() {
        val vm = vm(RationSurveyState(step = RationSurveyStep.MEALS))
        vm.update { it.copy(meals = emptySet()) }; idle()
        assertFalse(vm.state.value.canProceed)
        vm.next(); idle()
        assertEquals(RationSurveyStep.MEALS, vm.state.value.step)
    }

    @Test
    fun finishSetsCompleted_editAnswersOpensMealsKeepingAnswers() {
        val vm = vm(RationSurveyState(step = RationSurveyStep.DELIVERY))
        assertTrue(vm.state.value.isLastStep)
        vm.update { it.copy(meals = setOf(RationMeal.BREAKFAST, RationMeal.DINNER)) }
        vm.next(); idle()
        assertEquals(RationSurveyStep.SUMMARY, vm.state.value.step)
        assertTrue(saved.last().completed)

        vm.editAnswers(); idle()
        assertEquals(RationSurveyStep.MEALS, vm.state.value.step)
        assertEquals(setOf(RationMeal.BREAKFAST, RationMeal.DINNER), vm.state.value.answers.meals)
        assertTrue(saved.last().completed)
    }

    @Test
    fun backGoesToPreviousShownStep() {
        val vm = vm(RationSurveyState(step = RationSurveyStep.BUDGET))
        vm.back(); idle()
        assertEquals(RationSurveyStep.MEALS, vm.state.value.step)
        vm.back(); idle()
        assertEquals(RationSurveyStep.MEALS, vm.state.value.step)
    }
}
