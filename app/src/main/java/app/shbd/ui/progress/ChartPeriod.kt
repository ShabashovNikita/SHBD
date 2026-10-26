package app.shbd.ui.progress

import app.shbd.models.LocaleFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Selected chart period: [range] ending on [end] (the selected day). */
data class ChartPeriod(val range: TimeRange, val end: LocalDate)

/**
 * Days a chart of [range] ending on [end] spans, always [TimeRange.days] long.
 * The first reading always sits on the left edge: when [firstDataDay] falls
 * inside the period after its first day, the window starts on it and runs
 * forward (empty days to the right) instead of ending on [end]. Without data,
 * or once readings cover the whole period, it is the usual period ending on [end].
 */
fun chartWindow(range: TimeRange, end: LocalDate, firstDataDay: LocalDate?): Pair<LocalDate, LocalDate> {
    val (start, last) = range.dateRange(end)
    if (firstDataDay == null || !firstDataDay.isAfter(start) || firstDataDay.isAfter(last)) return start to last
    return firstDataDay to firstDataDay.plusDays(range.days - 1L)
}

/**
 * X-axis span in epoch ms for [chartWindow]: start of its first day through
 * the end of its last. Null for "All time" (the axis then follows the data).
 */
fun ChartPeriod.domainMs(firstDataDay: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long>? {
    if (range == TimeRange.ALL_TIME) return null
    val (start, last) = chartWindow(range, end, firstDataDay)
    return start.atStartOfDay(zone).toInstant().toEpochMilli() to
        (last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
}

/** Labelled x-axis days of [chartWindow] (see [axisTickOffsets]). */
fun ChartPeriod.tickDays(firstDataDay: LocalDate?): List<LocalDate> {
    if (range == TimeRange.ALL_TIME) return emptyList()
    val (start, _) = chartWindow(range, end, firstDataDay)
    return axisTickOffsets(range, range.days).map { start.plusDays(it.toLong()) }
}

/**
 * Indices of the labelled slots among [count] chart slots: every day of a
 * week; days 1, 7, 14, 21 and the last one of a month; five evenly spaced
 * slots for longer ranges.
 */
fun axisTickOffsets(range: TimeRange, count: Int): List<Int> {
    if (count <= 0) return emptyList()
    return when {
        range == TimeRange.WEEK || count <= 5 -> (0 until count).toList()
        range == TimeRange.MONTH -> (listOf(0, 7, 14, 21).filter { it < count - 1 } + (count - 1))
        else -> pickXLabelIndices(count, maxLabels = 5)
    }
}

/** Tick text: weekday for a week, month name for a year, "26.10" otherwise. */
fun chartTickLabel(range: TimeRange): (LocalDate) -> String {
    val locale = Locale.getDefault()
    return when (range) {
        TimeRange.WEEK -> { d -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) }
        TimeRange.YEAR, TimeRange.ALL_TIME -> { d -> d.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale) }
        else -> {
            val fmt = LocaleFormat.axisDay()
            return { d -> d.format(fmt) }
        }
    }
}
