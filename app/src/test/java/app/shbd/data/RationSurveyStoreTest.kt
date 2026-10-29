package app.shbd.data

import android.app.Application
import androidx.datastore.preferences.core.edit
import app.shbd.models.Allergen
import app.shbd.models.CookingTime
import app.shbd.models.Cuisine
import app.shbd.models.RationMeal
import app.shbd.models.RationSurveyAnswers
import app.shbd.models.RationSurveyState
import app.shbd.models.RationSurveyStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RationSurveyStoreTest {
    private lateinit var prefs: PreferencesStore

    @Before
    fun setUp() {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        runBlocking { prefs.dataStore.edit { it.remove(Keys.RATION_SURVEY) } }
    }

    private fun writeRaw(raw: String) {
        runBlocking { prefs.dataStore.edit { it[Keys.RATION_SURVEY] = raw } }
    }

    @Test
    fun emptyStore_isFreshSurvey() = runBlocking {
        val state = prefs.rationSurveyState.first()
        assertEquals(RationSurveyState(), state)
        assertEquals(RationSurveyStep.INTRO, state.step)
        assertFalse(state.completed)
    }

    @Test
    fun writeThenRead() = runBlocking {
        val saved = RationSurveyState(
            answers = RationSurveyAnswers(
                meals = setOf(RationMeal.BREAKFAST, RationMeal.DINNER),
                weeklyBudgetRub = 2_000,
                budgetUnlimited = true,
                allergens = setOf(Allergen.NUTS),
                stopFoods = listOf("Сельдерей"),
                cookingTime = CookingTime.NONE,
                cuisines = setOf(Cuisine.ITALIAN),
                deliveryWanted = true,
            ),
            step = RationSurveyStep.COOKING,
        )
        prefs.setRationSurveyState(saved)
        assertEquals(saved, prefs.rationSurveyState.first())
    }

    @Test
    fun garbage_fallsBackToDefaults() = runBlocking {
        writeRaw("{not json")
        assertEquals(RationSurveyState(), prefs.rationSurveyState.first())
    }

    @Test
    fun unknownField_isIgnored() = runBlocking {
        writeRaw("""{"answers":{"weeklyBudgetRub":2500,"familySize":4},"step":"BUDGET","future":1}""")
        val state = prefs.rationSurveyState.first()
        assertEquals(2_500, state.answers.weeklyBudgetRub)
        assertEquals(RationSurveyStep.BUDGET, state.step)
    }

    @Test
    fun unknownEnumInSet_isDroppedKeepingOtherAnswers() = runBlocking {
        writeRaw(
            """{"answers":{"allergens":["NUTS","FOO"],"cuisines":["ITALIAN","KLINGON"],""" +
                """"cookingTime":"MICROWAVE_ONLY","weeklyBudgetRub":1800},"step":"RESTRICTIONS"}""",
        )
        val state = prefs.rationSurveyState.first()
        assertEquals(setOf(Allergen.NUTS), state.answers.allergens)
        assertEquals(setOf(Cuisine.ITALIAN), state.answers.cuisines)
        assertEquals(CookingTime.NORMAL, state.answers.cookingTime)
        assertEquals(1_800, state.answers.weeklyBudgetRub)
        assertEquals(RationSurveyStep.RESTRICTIONS, state.step)
    }
}
