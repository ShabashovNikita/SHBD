package app.shbd.models

import app.shbd.models.RationSurveyAnswers.Companion.addStopFood
import app.shbd.models.RationSurveyAnswers.Companion.budgetLevel
import app.shbd.models.RationSurveyAnswers.Companion.toggleCuisine
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RationSurveyAnswersTest {

    @Test
    fun defaults_matchSpec() {
        val a = RationSurveyAnswers()
        assertEquals(setOf(RationMeal.BREAKFAST, RationMeal.LUNCH, RationMeal.DINNER), a.meals)
        assertEquals(3_000, a.weeklyBudgetRub)
        assertFalse(a.budgetUnlimited)
        assertEquals(CookingTime.NORMAL, a.cookingTime)
        assertEquals(2, a.repeatLimit)
        assertEquals(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY), a.cookDays)
        assertEquals(setOf(KitchenTool.STOVE, KitchenTool.OVEN, KitchenTool.MICROWAVE), a.kitchen)
        assertEquals(setOf(Cuisine.ANY), a.cuisines)
        assertFalse(a.deliveryWanted)
    }

    @Test
    fun budgetLevel_thresholds() {
        assertEquals(BudgetLevel.VERY_ECONOMY, budgetLevel(1_200))
        assertEquals(BudgetLevel.VERY_ECONOMY, budgetLevel(1_400))
        assertEquals(BudgetLevel.ECONOMY, budgetLevel(1_500))
        assertEquals(BudgetLevel.ECONOMY, budgetLevel(2_000))
        assertEquals(BudgetLevel.BALANCED, budgetLevel(2_600))
        assertEquals(BudgetLevel.BALANCED, budgetLevel(3_000))
        assertEquals(BudgetLevel.COMFORT, budgetLevel(4_000))
        assertEquals(BudgetLevel.FREE, budgetLevel(6_000))
        assertEquals(BudgetLevel.FREE, budgetLevel(15_000))
    }

    @Test
    fun unlimitedBudget_keepsSliderAmount() {
        val set = RationSurveyAnswers(weeklyBudgetRub = 2_000)
        val unlimited = set.copy(budgetUnlimited = true).normalized()
        assertEquals(2_000, unlimited.weeklyBudgetRub)
        assertEquals(2_000, unlimited.copy(budgetUnlimited = false).weeklyBudgetRub)
    }

    @Test
    fun stopFood_trimsAndAdds() {
        assertEquals(listOf("Сельдерей"), addStopFood(emptyList(), " Сельдерей "))
    }

    @Test
    fun stopFood_blankIsNotAdded() {
        assertEquals(emptyList<String>(), addStopFood(emptyList(), "   "))
    }

    @Test
    fun stopFood_caseInsensitiveRepeatIsNotAdded() {
        assertEquals(listOf("Сельдерей"), addStopFood(listOf("Сельдерей"), "сельдерей"))
    }

    @Test
    fun stopFood_cappedAtFortyChars() {
        val added = addStopFood(emptyList(), "а".repeat(55)).single()
        assertEquals(40, added.length)
    }

    @Test
    fun cuisine_concreteDropsAny() {
        assertEquals(setOf(Cuisine.ITALIAN), toggleCuisine(setOf(Cuisine.ANY), Cuisine.ITALIAN))
    }

    @Test
    fun cuisine_clearingLastSelectsAny() {
        assertEquals(setOf(Cuisine.ANY), toggleCuisine(setOf(Cuisine.ITALIAN), Cuisine.ITALIAN))
    }

    @Test
    fun cuisine_anyDropsConcrete() {
        assertEquals(setOf(Cuisine.ANY), toggleCuisine(setOf(Cuisine.ITALIAN, Cuisine.JAPANESE), Cuisine.ANY))
    }

    @Test
    fun normalized_clampsRanges() {
        val n = RationSurveyAnswers(
            weeklyBudgetRub = 99_999,
            repeatLimit = 7,
            cuisines = emptySet(),
            stopFoods = listOf(" Лук ", "лук", ""),
        ).normalized()
        assertEquals(15_000, n.weeklyBudgetRub)
        assertEquals(3, n.repeatLimit)
        assertEquals(setOf(Cuisine.ANY), n.cuisines)
        assertEquals(listOf("Лук"), n.stopFoods)
        assertEquals(1_000, RationSurveyAnswers(weeklyBudgetRub = 10).normalized().weeklyBudgetRub)
        assertEquals(2_300, RationSurveyAnswers(weeklyBudgetRub = 2_340).normalized().weeklyBudgetRub)
        assertEquals(1, RationSurveyAnswers(repeatLimit = 0).normalized().repeatLimit)
    }

    @Test
    fun noCooking_keepsRepeatsAndDays() {
        val custom = RationSurveyAnswers(repeatLimit = 3, cookDays = setOf(DayOfWeek.MONDAY))
        val back = custom.copy(cookingTime = CookingTime.NONE).copy(cookingTime = CookingTime.QUICK)
        assertEquals(3, back.repeatLimit)
        assertEquals(setOf(DayOfWeek.MONDAY), back.cookDays)
        assertTrue(back.cooks)
    }
}
