package app.shbd.services.ration

import app.shbd.models.RationMeal
import app.shbd.models.RationSlotMode
import app.shbd.models.RationSurveyAnswers
import app.shbd.services.ration.RationTestFixtures.recipe
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RationSlotRepairTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val breakfasts = (1..8).map { recipe("b$it", RationMeal.BREAKFAST, listOf("p" to 100)) }
    private val dinners = (1..8).map { recipe("d$it", RationMeal.DINNER, listOf("p" to 100)) }
    private val allowed = mapOf(RationMeal.BREAKFAST to breakfasts, RationMeal.DINNER to dinners)
    private val answers = RationSurveyAnswers(meals = setOf(RationMeal.BREAKFAST, RationMeal.DINNER), cookDays = emptySet(), repeatLimit = 1)
    private val targets = mapOf(RationMeal.BREAKFAST to 400.0, RationMeal.DINNER to 600.0)

    private fun repair(
        answer: RationModelAnswer?,
        allowedMap: Map<RationMeal, List<app.shbd.models.RationRecipe>> = allowed,
        a: RationSurveyAnswers = answers,
    ): RationRepairResult = RationSlotRepair.repair(
        allowed = allowedMap,
        schedule = RationCookSchedule.build(monday, a),
        answer = answer,
        repeatLimit = a.repeatLimit,
        targetKcal = targets,
        kcalOf = { 300.0 },
        seed = 42L,
    )

    private fun goodDays() = (1..7).map { mapOf(RationMeal.BREAKFAST to "b$it", RationMeal.DINNER to "d$it") }

    @Test
    fun validAnswer_keptAsIs() {
        val r = repair(RationModelAnswer(goodDays(), ""))
        assertEquals(0, r.repairedSlots)
        assertEquals(listOf("b1", "d1"), r.grid[0])
        assertFalse(r.repeatsRaised)
    }

    @Test
    fun unknownForbiddenWrongMealMissingAndOverLimit_areReplaced() {
        val days = goodDays().toMutableList()
        days[0] = mapOf(RationMeal.BREAKFAST to "nope", RationMeal.DINNER to "b2") // unknown id, wrong meal
        days[1] = mapOf(RationMeal.BREAKFAST to "b2") // dinner missing
        days[2] = mapOf(RationMeal.BREAKFAST to "b2", RationMeal.DINNER to "d3") // b2 over limit 1
        val r = repair(RationModelAnswer(days, ""))
        assertEquals(4, r.repairedSlots)
        val all = r.grid.flatten()
        assertTrue(all.all { id -> id.startsWith("b") || id.startsWith("d") })
        r.grid.forEach { day ->
            assertTrue(day[0].startsWith("b"))
            assertTrue(day[1].startsWith("d"))
        }
        // Limit 1 with 8 dishes per meal: no repeats at all.
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun nullAnswer_engineFillsEverything() {
        val r = repair(null)
        assertEquals(14, r.repairedSlots)
        assertEquals(7, r.grid.size)
    }

    @Test
    fun reheatSlotsCopyCookSlot() {
        val a = answers.copy(cookDays = setOf(DayOfWeek.WEDNESDAY), repeatLimit = 2)
        val schedule = RationCookSchedule.build(monday, a)
        assertEquals(RationSlotMode.REHEAT, schedule[3][1].mode)
        val r = repair(RationModelAnswer(goodDays(), ""), a = a)
        assertEquals(r.grid[2][1], r.grid[3][1])
    }

    @Test
    fun tooFewDishes_limitRaised() {
        val few = mapOf(RationMeal.BREAKFAST to breakfasts.take(3), RationMeal.DINNER to dinners)
        val r = repair(null, allowedMap = few)
        assertTrue(r.repeatsRaised)
        assertEquals(3, r.repeatLimit)
        assertTrue(r.grid.map { it[0] }.groupingBy { it }.eachCount().values.all { it <= 3 })
    }

    @Test
    fun deterministic() {
        assertEquals(repair(null).grid, repair(null).grid)
    }
}
