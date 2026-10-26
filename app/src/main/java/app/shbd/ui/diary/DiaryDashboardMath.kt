package app.shbd.ui.diary

import app.shbd.models.WeightEntry
import java.time.LocalDate
import java.time.ZoneId

/** Pure helpers behind the Diary dashboard cards (kept UI-free for unit tests). */
internal object DiaryDashboardMath {
    /** Latest weigh-in on or before the end of [date] in [zone]; null when none. */
    fun latestWeightOnOrBefore(
        entries: List<WeightEntry>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WeightEntry? {
        val endExclusive = date.plusDays(1).atStartOfDay(zone).toInstant()
        return entries.filter { it.date.isBefore(endExclusive) }.maxByOrNull { it.date }
    }

    /** Split [minutes] of sleep into whole hours and the remaining minutes. */
    fun sleepHoursMinutes(minutes: Int): Pair<Int, Int> {
        val safe = minutes.coerceAtLeast(0)
        return safe / 60 to safe % 60
    }

    /** Bar fill for [current] of [goal], clamped to 0..1; a non-positive goal is empty. */
    fun progressFraction(current: Double, goal: Double): Float {
        if (goal <= 0.0 || current <= 0.0) return 0f
        return (current / goal).coerceIn(0.0, 1.0).toFloat()
    }
}
