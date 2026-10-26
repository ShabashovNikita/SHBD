package app.shbd.ui.diary

import app.shbd.models.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DiaryDashboardMathTest {
    private val zone = ZoneOffset.UTC

    private fun weighIn(day: LocalDate, hour: Int, kg: Double) =
        WeightEntry(date = day.atTime(hour, 0).toInstant(zone), weightKg = kg)

    @Test
    fun latestWeight_picksNewestOnOrBeforeDay() {
        val day = LocalDate.of(2026, 10, 25)
        val entries = listOf(
            weighIn(day.minusDays(3), 8, 80.0),
            weighIn(day, 23, 79.5),
            weighIn(day.plusDays(1), 7, 79.0),
        )
        assertEquals(79.5, DiaryDashboardMath.latestWeightOnOrBefore(entries, day, zone)!!.weightKg, 0.0)
        assertEquals(80.0, DiaryDashboardMath.latestWeightOnOrBefore(entries, day.minusDays(1), zone)!!.weightKg, 0.0)
    }

    @Test
    fun latestWeight_nullWhenNothingBefore() {
        val day = LocalDate.of(2026, 10, 25)
        assertNull(DiaryDashboardMath.latestWeightOnOrBefore(listOf(weighIn(day.plusDays(1), 7, 79.0)), day, zone))
        assertNull(DiaryDashboardMath.latestWeightOnOrBefore(emptyList(), day, zone))
    }

    @Test
    fun sleepHoursMinutes_splitsAndClamps() {
        assertEquals(7 to 30, DiaryDashboardMath.sleepHoursMinutes(450))
        assertEquals(0 to 45, DiaryDashboardMath.sleepHoursMinutes(45))
        assertEquals(0 to 0, DiaryDashboardMath.sleepHoursMinutes(-5))
    }

    @Test
    fun progressFraction_clampsAndHandlesZeroGoal() {
        assertEquals(0.5f, DiaryDashboardMath.progressFraction(50.0, 100.0), 0f)
        assertEquals(1f, DiaryDashboardMath.progressFraction(150.0, 100.0), 0f)
        assertEquals(0f, DiaryDashboardMath.progressFraction(10.0, 0.0), 0f)
        assertEquals(0f, DiaryDashboardMath.progressFraction(-1.0, 100.0), 0f)
    }
}
