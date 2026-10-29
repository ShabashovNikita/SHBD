package app.shbd.services.ration

import app.shbd.models.RationMeal
import app.shbd.models.RationSlotMode
import app.shbd.models.RationSurveyAnswers
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builder on the real catalog with an engine-filled grid (no model). */
class RationPlanBuilderTest {
    private val catalog = RationTestFixtures.realCatalog
    private val monday = LocalDate.of(2026, 11, 2)

    private fun input(answers: RationSurveyAnswers, goal: Int?): RationPlanInput {
        val filter = RationDishFilter.allowed(catalog, answers)
        val schedule = RationCookSchedule.build(monday, answers)
        val repair = RationSlotRepair.repair(
            allowed = filter.allowed,
            schedule = schedule,
            answer = null,
            repeatLimit = answers.repeatLimit,
            targetKcal = RationMenuService.mealTargets(goal ?: 2000, filter.allowed.keys),
            kcalOf = { RationRecipeMath.portion(it, catalog).kcal },
            seed = 1L,
        )
        return RationPlanInput(
            catalog = catalog,
            allowed = filter.allowed,
            schedule = schedule,
            grid = repair.grid,
            repeatLimit = repair.repeatLimit,
            calorieGoal = goal,
            budgetRub = if (answers.budgetUnlimited) null else answers.weeklyBudgetRub,
            dates = RationCookSchedule.dates(monday),
        )
    }

    @Test
    fun goal1800_everyDayOnGoalNeverAbove() {
        val plan = RationPlanBuilder.build(input(RationSurveyAnswers(budgetUnlimited = true), 1_800))
        for (day in plan.days) {
            assertTrue("${day.date} ${day.kcal}", day.kcal <= 1_800 + RationPlanBuilder.MAX_OVER_KCAL)
            val atMax = day.portionFactor == RationPlanBuilder.MAX_FACTOR
            assertTrue("${day.date} ${day.kcal} k ${day.portionFactor}", day.kcal >= 1_800 - 60 || atMax)
        }
        assertEquals(7, plan.days.size)
        assertTrue(plan.days.all { day -> day.slots.map { it.meal } == listOf(RationMeal.BREAKFAST, RationMeal.LUNCH, RationMeal.DINNER) })
    }

    @Test
    fun noGoal_uses2000AndFlagsIt() {
        val plan = RationPlanBuilder.build(input(RationSurveyAnswers(budgetUnlimited = true), null))
        assertEquals(2_000, plan.calorieGoal)
        assertTrue(plan.calorieGoalAssumed)
    }

    @Test
    fun budget1000_costWithinOrOverReported() {
        val answers = RationSurveyAnswers(weeklyBudgetRub = 1_000)
        val inp = input(answers, 1_800)
        val plan = RationPlanBuilder.build(inp)
        assertEquals(1_000, plan.budgetRub)
        assertTrue(plan.weekCostRub <= 1_000 || plan.overBudgetRub == plan.weekCostRub - 1_000)
        assertTrue(plan.overBudgetRub >= 0)
        // Swaps keep the filter and the repeat limit.
        val allowedIds = inp.allowed.values.flatten().map { it.id }.toSet()
        val slots = plan.days.flatMap { it.slots }
        assertTrue(slots.all { it.recipeId in allowedIds })
        val uses = slots.groupingBy { it.recipeId }.eachCount()
        assertTrue(uses.toString(), uses.values.all { it <= inp.repeatLimit })
    }

    @Test
    fun budgetSwapsReduceCost() {
        val unlimited = RationPlanBuilder.build(input(RationSurveyAnswers(budgetUnlimited = true), 1_800))
        val tight = RationPlanBuilder.build(input(RationSurveyAnswers(weeklyBudgetRub = 1_000), 1_800))
        assertTrue("${tight.weekCostRub} vs ${unlimited.weekCostRub}", tight.weekCostRub <= unlimited.weekCostRub)
    }

    @Test
    fun unlimitedBudget_dishesAsGiven() {
        val inp = input(RationSurveyAnswers(budgetUnlimited = true), 1_800)
        val plan = RationPlanBuilder.build(inp)
        assertEquals(inp.grid, plan.days.map { day -> day.slots.map { it.recipeId } })
        assertEquals(null, plan.budgetRub)
        assertEquals(0, plan.overBudgetRub)
    }

    @Test
    fun batchCooking_reheatSlotsMarked_andShoppingCountsCookOnly() {
        val answers = RationSurveyAnswers(cookDays = setOf(DayOfWeek.WEDNESDAY), repeatLimit = 2, budgetUnlimited = true)
        val plan = RationPlanBuilder.build(input(answers, 1_800))
        val wed = plan.days[2].slots.first { it.meal == RationMeal.DINNER }
        val thu = plan.days[3].slots.first { it.meal == RationMeal.DINNER }
        assertEquals(2, wed.portions)
        assertEquals(RationSlotMode.REHEAT, thu.mode)
        assertEquals(wed.recipeId, thu.recipeId)
        assertTrue(plan.shopping.isNotEmpty())
        assertTrue(plan.pantry.all { it.costRub == 0 })
        assertEquals(plan.shopping.sumOf { it.costRub }, plan.weekCostRub)
    }

    @Test
    fun dayFactor_roundsDownAndCapsAtMax() {
        assertEquals(1.6, RationPlanBuilder.dayFactor(4_000, 1_000.0), 1e-9)
        // No lower bound: a heavy day is cut down to the goal.
        assertEquals(0.5, RationPlanBuilder.dayFactor(1_000, 2_000.0), 1e-9)
        assertEquals(1.2, RationPlanBuilder.dayFactor(1_800, 1_500.0), 1e-9)
        // 1647 / 1886 = 0.8732… → 0.87 (down, never above the goal).
        assertEquals(0.87, RationPlanBuilder.dayFactor(1_647, 1_886.0), 1e-9)
    }

    @Test
    fun lowGoals_noDayAboveGoalPlus50() {
        for (goal in listOf(1_200, 1_400, 1_647, 2_600)) {
            for (meals in listOf(
                setOf(RationMeal.BREAKFAST, RationMeal.LUNCH, RationMeal.DINNER),
                setOf(RationMeal.BREAKFAST, RationMeal.LUNCH, RationMeal.DINNER, RationMeal.SNACK),
                setOf(RationMeal.BREAKFAST, RationMeal.DINNER),
            )) {
                val answers = RationSurveyAnswers(meals = meals, cookDays = setOf(DayOfWeek.WEDNESDAY), repeatLimit = 2, weeklyBudgetRub = 1_500)
                val plan = RationPlanBuilder.build(input(answers, goal))
                assertTrue(plan.days.all { it.kcal <= goal + RationPlanBuilder.MAX_OVER_KCAL })
            }
        }
    }
}
