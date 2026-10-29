package app.shbd.data

import android.app.Application
import androidx.datastore.preferences.core.edit
import app.shbd.models.RationMeal
import app.shbd.models.RationPlan
import app.shbd.models.RationPlanDay
import app.shbd.models.RationPlanSlot
import app.shbd.models.RationShoppingItem
import app.shbd.models.RationSlotMode
import app.shbd.models.RationSurveyAnswers
import app.shbd.models.RationSurveyState
import app.shbd.models.ShopAisle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RationPlanStoreTest {
    private lateinit var prefs: PreferencesStore

    private val plan = RationPlan(
        createdAt = 123L,
        startDate = "2026-11-02",
        answersHash = 42,
        calorieGoal = 1_800,
        portionFactor = 1.1,
        days = listOf(
            RationPlanDay(
                "2026-11-02",
                listOf(
                    RationPlanSlot(RationMeal.DINNER, "plov", "Плов с курицей", RationSlotMode.COOK, 2, 640, 30.0, 20.0, 70.0, 95),
                ),
            ),
            RationPlanDay(
                "2026-11-03",
                listOf(RationPlanSlot(RationMeal.DINNER, "plov", "Плов с курицей", RationSlotMode.REHEAT, 0, 640)),
            ),
        ),
        shopping = listOf(RationShoppingItem("chicken", "Курица (бедро)", ShopAisle.PROTEIN, 300, null, 87)),
        pantry = listOf(RationShoppingItem("oil", "Масло растительное", ShopAisle.OTHER, 30)),
        weekCostRub = 87,
        budgetRub = 3_000,
        repairedSlots = 1,
        comment = "Хорошее меню",
    )

    @Before
    fun setUp() {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        runBlocking { prefs.dataStore.edit { it.remove(Keys.RATION_PLAN); it.remove(Keys.RATION_SURVEY) } }
    }

    @Test
    fun empty_isNull() = runBlocking {
        assertNull(prefs.rationPlan.first())
    }

    @Test
    fun writeThenRead() = runBlocking {
        prefs.setRationPlan(plan)
        assertEquals(plan, prefs.rationPlan.first())
    }

    @Test
    fun garbage_isNull() = runBlocking {
        prefs.dataStore.edit { it[Keys.RATION_PLAN] = "{broken" }
        assertNull(prefs.rationPlan.first())
    }

    @Test
    fun writeNull_removesPlan() = runBlocking {
        prefs.setRationPlan(plan)
        prefs.setRationPlan(null)
        assertNull(prefs.rationPlan.first())
    }

    @Test
    fun surveyEdit_keepsPlan() = runBlocking {
        prefs.setRationPlan(plan)
        prefs.setRationSurveyState(RationSurveyState(answers = RationSurveyAnswers(weeklyBudgetRub = 5_000)).finish())
        assertEquals(plan, prefs.rationPlan.first())
    }
}
