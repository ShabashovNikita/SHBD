package app.shbd.ui.diary.blocks

import app.shbd.models.WaterEntry
import app.shbd.ui.progress.ChartPeriod
import app.shbd.ui.progress.TimeRange
import app.shbd.ui.progress.axisTickOffsets
import app.shbd.ui.progress.chartWindow
import app.shbd.ui.progress.domainMs
import app.shbd.ui.progress.tickDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DiaryChartMathTest {
    private val end = LocalDate.of(2026, 10, 25)

    @Test
    fun daySeries_weekEndsOnSelectedDay_andKeepsGapsNull() {
        val values = mapOf(end to 8000.0, end.minusDays(3) to 5000.0, end.minusDays(6) to 4000.0)
        val series = daySeries(TimeRange.WEEK, end, values)
        assertEquals(7, series.size)
        assertEquals(end.minusDays(6), series.first().date)
        assertEquals(end, series.last().date)
        assertEquals(8000.0, series.last().value!!, 0.0)
        assertNull(series[5].value)
        assertEquals(5000.0, series[3].value!!, 0.0)
    }

    @Test
    fun daySeries_startsOnFirstDayWithData() {
        // First steps logged three days ago: that day is on the left, the window runs forward.
        val series = daySeries(TimeRange.WEEK, end, mapOf(end to 8000.0, end.minusDays(3) to 5000.0))
        assertEquals(7, series.size)
        assertEquals(end.minusDays(3), series.first().date)
        assertEquals(5000.0, series.first().value!!, 0.0)
        assertEquals(end.plusDays(3), series.last().date)
        assertNull(series.last().value)
        // No data at all: the usual period ending on the selected day.
        assertEquals(end, daySeries(TimeRange.WEEK, end, emptyMap()).last().date)
    }

    @Test
    fun chartWindow_andAxisTicks() {
        assertEquals(end.minusDays(6) to end, chartWindow(TimeRange.WEEK, end, null))
        assertEquals(end.minusDays(6) to end, chartWindow(TimeRange.WEEK, end, end.minusDays(40)))
        assertEquals(end to end.plusDays(6), chartWindow(TimeRange.WEEK, end, end))
        assertEquals((0..6).toList(), axisTickOffsets(TimeRange.WEEK, 7))
        assertEquals(listOf(0, 7, 14, 21, 29), axisTickOffsets(TimeRange.MONTH, 30))
        assertEquals(5, axisTickOffsets(TimeRange.THREE_MONTHS, 13).size)
        val ticks = ChartPeriod(TimeRange.MONTH, end).tickDays(end)
        assertEquals(listOf(end, end.plusDays(7), end.plusDays(14), end.plusDays(21), end.plusDays(29)), ticks)
    }

    @Test
    fun bucketSeries_weekAndMonthStayDaily() {
        val month = daySeries(TimeRange.MONTH, end, emptyMap())
        assertEquals(30, bucketSeries(month, TimeRange.MONTH).size)
    }

    @Test
    fun bucketSeries_threeMonths_weeklyMeansIgnoreEmptyDays() {
        // A reading on the period's first day keeps the window ending on the selected day.
        val values = mapOf(end to 10.0, end.minusDays(1) to 20.0, end.minusDays(89) to 5.0)
        val buckets = bucketSeries(daySeries(TimeRange.THREE_MONTHS, end, values), TimeRange.THREE_MONTHS)
        assertEquals(13, buckets.size)
        // Last bucket ends on the selected day: mean of 10 and 20, the five empty days ignored.
        assertEquals(end.minusDays(6), buckets.last().date)
        assertEquals(15.0, buckets.last().value!!, 0.0)
        assertNull(buckets[1].value)
    }

    @Test
    fun bucketSeries_yearGivesCalendarMonths() {
        val buckets = bucketSeries(daySeries(TimeRange.YEAR, end, emptyMap()), TimeRange.YEAR)
        assertTrue(buckets.size in 12..13)
        assertEquals(1, buckets.last().date.dayOfMonth)
        assertEquals(10, buckets.last().date.monthValue)
    }

    @Test
    fun hasAnyValue_falseWhenAllNull() {
        assertFalse(hasAnyValue(daySeries(TimeRange.WEEK, end, emptyMap())))
        assertTrue(hasAnyValue(daySeries(TimeRange.WEEK, end, mapOf(end to 1.0))))
    }

    @Test
    fun waterByDay_sumsPortionsPerLocalDay() {
        val zone = ZoneOffset.UTC
        val noon = end.atTime(12, 0).toInstant(zone)
        val entries = listOf(
            WaterEntry(date = noon, milliliters = 250),
            WaterEntry(date = noon.plusSeconds(3600), milliliters = 300),
            WaterEntry(date = noon.minusSeconds(86_400), milliliters = 200),
        )
        val byDay = waterByDay(entries, zone)
        assertEquals(550.0, byDay[end]!!, 0.0)
        assertEquals(200.0, byDay[end.minusDays(1)]!!, 0.0)
    }

    @Test
    fun positiveByDay_dropsZeroAndNull() {
        data class P(val d: LocalDate, val v: Int?)
        val map = positiveByDay(listOf(P(end, 0), P(end.minusDays(1), null), P(end.minusDays(2), 30)), { it.d }, { it.v })
        assertEquals(mapOf(end.minusDays(2) to 30.0), map)
    }

    @Test
    fun withOverrides_handTypedDayReplacesHealthConnect() {
        val health = mapOf(end to 4000.0, end.minusDays(1) to 6000.0)
        val manual = mapOf(end to 9000.0, end.minusDays(3) to 1200.0, end.minusDays(4) to 0.0)
        val merged = withOverrides(health, manual)
        assertEquals(9000.0, merged[end]!!, 0.0)
        assertEquals(6000.0, merged[end.minusDays(1)]!!, 0.0)
        assertEquals(1200.0, merged[end.minusDays(3)]!!, 0.0)
        assertFalse(merged.containsKey(end.minusDays(4)))
        assertEquals(health, withOverrides(health, emptyMap()))
    }

    @Test
    fun deficitByDay_isNormMinusEatenPlusWorkouts_onLoggedDaysOnly() {
        val eaten = mapOf(end to 1600, end.minusDays(1) to 3000, end.minusDays(2) to 0)
        val workouts = mapOf(end to 400, end.minusDays(5) to 700)
        val deficit = deficitByDay(normKcal = 2500, eatenByDay = eaten, workoutsByDay = workouts)
        assertEquals(2500 - 1600 + 400.0, deficit[end]!!, 0.0)
        // Surplus day stays negative.
        assertEquals(-500.0, deficit[end.minusDays(1)]!!, 0.0)
        // No food logged: no value, even with a workout that day.
        assertFalse(deficit.containsKey(end.minusDays(2)))
        assertFalse(deficit.containsKey(end.minusDays(5)))
    }

    @Test
    fun periodDomain_coversWholeWeekOrMonth_andAllTimeFollowsData() {
        val zone = ZoneOffset.UTC
        val (weekFrom, weekTo) = ChartPeriod(TimeRange.WEEK, end).domainMs(null, zone)!!
        assertEquals(end.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli(), weekFrom)
        assertEquals(end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1, weekTo)
        val (monthFrom, _) = ChartPeriod(TimeRange.MONTH, end).domainMs(null, zone)!!
        assertTrue(monthFrom < weekFrom)
        assertNull(ChartPeriod(TimeRange.ALL_TIME, end).domainMs(null, zone))
    }

    @Test
    fun workoutsByDay_sumsHealthConnectAndManualPerDay() {
        val merged = workoutsByDay(
            mapOf(end.toString() to 300, "bad" to 50),
            mapOf(end.toString() to 200, end.minusDays(1).toString() to 150),
        )
        assertEquals(mapOf(end to 500, end.minusDays(1) to 150), merged)
    }
}
