package app.shbd.ui.diary.blocks

import app.shbd.models.WaterEntry
import app.shbd.ui.progress.TimeRange
import app.shbd.ui.progress.chartWindow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One chart bar: a day (or the first day of a week/month bucket) and its value; null = no data. */
data class DayValue(val date: LocalDate, val value: Double?)

/** Ranges offered under the Diary block charts (no "All time"). */
val DiaryChartRanges: List<TimeRange> = listOf(
    TimeRange.WEEK,
    TimeRange.MONTH,
    TimeRange.THREE_MONTHS,
    TimeRange.SIX_MONTHS,
    TimeRange.YEAR,
)

/**
 * One point per calendar day of the chart window ([chartWindow]: [range] ending
 * on [end], or starting on the period's first day with data so that day sits on
 * the left); days missing from [values] are null.
 */
fun daySeries(range: TimeRange, end: LocalDate, values: Map<LocalDate, Double>): List<DayValue> {
    val (periodStart, periodEnd) = range.dateRange(end)
    val firstDataDay = values.keys.filter { !it.isBefore(periodStart) && !it.isAfter(periodEnd) }.minOrNull()
    val (start, last) = chartWindow(range, end, firstDataDay)
    val out = ArrayList<DayValue>(range.days)
    var day = start
    while (!day.isAfter(last)) {
        out.add(DayValue(day, values[day]))
        day = day.plusDays(1)
    }
    return out
}

/**
 * Bars that fit on a phone: Week and Month stay daily, 3 and 6 months become
 * 7-day buckets (the last one ends on the selected day), Year becomes calendar
 * months. A bucket's value is the mean of its days with data; empty days never
 * pull the mean down, and a bucket with no data stays null.
 */
fun bucketSeries(series: List<DayValue>, range: TimeRange): List<DayValue> = when (range) {
    TimeRange.WEEK, TimeRange.MONTH -> series
    TimeRange.YEAR, TimeRange.ALL_TIME -> series
        .groupBy { YearMonth.from(it.date) }
        .map { (_, days) -> DayValue(days.first().date, meanOfPresent(days)) }
    TimeRange.THREE_MONTHS, TimeRange.SIX_MONTHS -> series
        .asReversed()
        .chunked(7)
        .map { chunk -> chunk.asReversed() }
        .asReversed()
        .map { days -> DayValue(days.first().date, meanOfPresent(days)) }
}

fun hasAnyValue(series: List<DayValue>): Boolean = series.any { it.value != null }

/** Total millilitres per local day. */
fun waterByDay(entries: List<WaterEntry>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Double> =
    entries
        .groupBy { it.date.atZone(zone).toLocalDate() }
        .mapValues { (_, list) -> list.sumOf { it.milliliters }.toDouble() }
        .filterValues { it > 0 }

/** Keeps the positive values of a per-day list, keyed by date (Health Connect series). */
fun <T> positiveByDay(items: List<T>, date: (T) -> LocalDate, value: (T) -> Number?): Map<LocalDate, Double> =
    buildMap {
        items.forEach { item ->
            val v = value(item)?.toDouble() ?: return@forEach
            if (v > 0) put(date(item), v)
        }
    }

private fun meanOfPresent(days: List<DayValue>): Double? {
    val present = days.mapNotNull { it.value }
    return if (present.isEmpty()) null else present.average()
}

/** [base] per-day values with [overrides] (hand-typed days) replacing them; non-positive overrides are ignored. */
fun withOverrides(base: Map<LocalDate, Double>, overrides: Map<LocalDate, Double>): Map<LocalDate, Double> =
    if (overrides.isEmpty()) base else base + overrides.filterValues { it > 0 }

/**
 * Daily calorie deficit for the Weight block: (norm − eaten) + workouts, kcal.
 * [normKcal] is the maintenance norm (TDEE). Only days with logged food count —
 * an empty diary day is "no data", not a full-norm deficit. Workouts are the
 * day's burned kcal (Health Connect + manual), missing = 0. Negative = surplus.
 */
fun deficitByDay(
    normKcal: Int,
    eatenByDay: Map<LocalDate, Int>,
    workoutsByDay: Map<LocalDate, Int>,
): Map<LocalDate, Double> =
    eatenByDay
        .filterValues { it > 0 }
        .mapValues { (day, eaten) ->
            (normKcal - eaten + (workoutsByDay[day] ?: 0).coerceAtLeast(0)).toDouble()
        }

/** Sums ISO-date keyed kcal maps (Health Connect burn + manual workouts) into one per-day map. */
fun workoutsByDay(vararg byIsoDay: Map<String, Int>): Map<LocalDate, Int> {
    val out = HashMap<LocalDate, Int>()
    byIsoDay.forEach { map ->
        map.forEach { (iso, kcal) ->
            val day = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return@forEach
            out[day] = (out[day] ?: 0) + kcal.coerceAtLeast(0)
        }
    }
    return out
}
