package app.shbd.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Steps and sleep the user entered by hand for one day. A non-null value wins
 * over Health Connect for that day, so the user can log or correct the day
 * without a wearable; null means "not entered, use Health Connect".
 */
@Serializable
data class ManualDayMetric(
    /** ISO local date YYYY-MM-DD */
    val date: String,
    val steps: Long? = null,
    val sleepMinutes: Int? = null,
)

/** Per-day manual steps and sleep, local only (never written to Health Connect). */
class ManualDayMetricsRepository(private val prefs: PreferencesStore) {
    val byDay: Flow<Map<LocalDate, ManualDayMetric>> =
        prefs.listPref(Keys.MANUAL_DAY_METRICS, ManualDayMetric.serializer()).map { list ->
            list.mapNotNull { m -> runCatching { LocalDate.parse(m.date) }.getOrNull()?.let { it to m } }.toMap()
        }

    /** Sets (or with null clears) the day's manual steps. */
    suspend fun setSteps(date: LocalDate, steps: Long?) =
        edit(date) { it.copy(steps = steps?.coerceIn(0L, MAX_STEPS)) }

    /** Sets (or with null clears) the day's manual sleep. */
    suspend fun setSleepMinutes(date: LocalDate, minutes: Int?) =
        edit(date) { it.copy(sleepMinutes = minutes?.coerceIn(0, MAX_SLEEP_MINUTES)) }

    private suspend fun edit(date: LocalDate, change: (ManualDayMetric) -> ManualDayMetric) {
        val key = date.toString()
        prefs.editListPref(Keys.MANUAL_DAY_METRICS, ManualDayMetric.serializer()) { current ->
            val updated = change(current.firstOrNull { it.date == key } ?: ManualDayMetric(key))
            val rest = current.filterNot { it.date == key }
            if (updated.steps == null && updated.sleepMinutes == null) rest else rest + updated
        }
    }

    companion object {
        const val MAX_STEPS = 200_000L
        const val MAX_SLEEP_MINUTES = 24 * 60
    }
}
