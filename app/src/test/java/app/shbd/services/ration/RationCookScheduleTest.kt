package app.shbd.services.ration

import app.shbd.models.CookingTime
import app.shbd.models.RationMeal
import app.shbd.models.RationSlotMode
import app.shbd.models.RationSurveyAnswers
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RationCookScheduleTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val answers = RationSurveyAnswers(
        meals = setOf(RationMeal.BREAKFAST, RationMeal.DINNER, RationMeal.SNACK),
        cookDays = setOf(DayOfWeek.WEDNESDAY),
        repeatLimit = 2,
    )

    private fun slot(days: List<List<RationScheduledSlot>>, day: Int, meal: RationMeal) = days[day].first { it.meal == meal }

    @Test
    fun wednesdayLimit2_dinnerCookedForTwo_thursdayReheat() {
        val s = RationCookSchedule.build(monday, answers)
        assertEquals(7, s.size)
        val wed = slot(s, 2, RationMeal.DINNER)
        assertEquals(RationSlotMode.COOK, wed.mode)
        assertEquals(2, wed.portions)
        val thu = slot(s, 3, RationMeal.DINNER)
        assertEquals(RationSlotMode.REHEAT, thu.mode)
        assertEquals(2, thu.sourceDay)
        assertEquals(RationSlotMode.COOK, slot(s, 4, RationMeal.DINNER).mode)
        assertEquals(1, slot(s, 0, RationMeal.DINNER).portions)
    }

    @Test
    fun limit1_noReheats() {
        val s = RationCookSchedule.build(monday, answers.copy(repeatLimit = 1))
        assertTrue(s.flatten().none { it.mode == RationSlotMode.REHEAT })
    }

    @Test
    fun noCookDays_everythingCooked() {
        val s = RationCookSchedule.build(monday, answers.copy(cookDays = emptySet()))
        assertTrue(s.flatten().all { it.mode == RationSlotMode.COOK && it.portions == 1 })
    }

    @Test
    fun breakfastAndSnack_alwaysCooked() {
        val s = RationCookSchedule.build(monday, answers.copy(cookDays = DayOfWeek.entries.toSet(), repeatLimit = 3))
        assertTrue(s.flatten().filter { it.meal == RationMeal.BREAKFAST || it.meal == RationMeal.SNACK }.all { it.mode == RationSlotMode.COOK })
    }

    @Test
    fun notCooking_everythingCooked() {
        val s = RationCookSchedule.build(monday, answers.copy(cookingTime = CookingTime.NONE))
        assertTrue(s.flatten().all { it.mode == RationSlotMode.COOK })
    }

    @Test
    fun fridayStart_weekIsFriToThu_batchStopsAtNextCookDay() {
        val friday = LocalDate.of(2026, 11, 6)
        val dates = RationCookSchedule.dates(friday)
        assertEquals(DayOfWeek.FRIDAY, dates.first().dayOfWeek)
        assertEquals(DayOfWeek.THURSDAY, dates.last().dayOfWeek)
        val s = RationCookSchedule.build(friday, answers.copy(cookDays = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), repeatLimit = 3))
        // Sunday (day 2) cooks one portion: Monday is the next cook day.
        assertEquals(1, slot(s, 2, RationMeal.DINNER).portions)
        // Monday (day 3) cooks for Mon, Tue, Wed (limit 3).
        assertEquals(3, slot(s, 3, RationMeal.DINNER).portions)
        assertEquals(RationSlotMode.REHEAT, slot(s, 5, RationMeal.DINNER).mode)
        assertEquals(RationSlotMode.COOK, slot(s, 6, RationMeal.DINNER).mode)
    }

    @Test
    fun batchNeverRunsPastTheWeek() {
        val sunday = LocalDate.of(2026, 11, 1)
        val s = RationCookSchedule.build(sunday, answers.copy(cookDays = setOf(DayOfWeek.SATURDAY), repeatLimit = 3))
        // Saturday is the last day (day 6): one portion only.
        assertEquals(1, slot(s, 6, RationMeal.DINNER).portions)
    }
}
