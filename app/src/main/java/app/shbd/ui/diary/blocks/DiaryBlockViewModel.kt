package app.shbd.ui.diary.blocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.shbd.AppContainer
import app.shbd.services.health.DailySeriesPoint
import app.shbd.ui.progress.TimeRange
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Health Connect days for a block chart range; [loading] until the first read lands. */
data class BlockHealthState(
    val range: TimeRange? = null,
    val end: LocalDate? = null,
    val loading: Boolean = true,
    val days: Map<LocalDate, DailySeriesPoint> = emptyMap(),
    val stepsGranted: Boolean = false,
    val exerciseGranted: Boolean = false,
    val sleepGranted: Boolean = false,
)

/**
 * Health Connect series behind the Steps, Sleep and Workouts block charts:
 * one grouped per-day read for the chart range ending on the selected day.
 * Water and weight come from local repositories and need no loading here.
 */
class DiaryBlockViewModel(private val container: AppContainer) : ViewModel() {
    private val _health = MutableStateFlow(BlockHealthState())
    val health: StateFlow<BlockHealthState> = _health.asStateFlow()
    private var job: Job? = null

    /** Reads [range] ending on [end]; [force] re-reads the same window (app resume). */
    fun load(range: TimeRange, end: LocalDate, force: Boolean = false) {
        val current = _health.value
        if (!force && current.range == range && current.end == end && !current.loading) return
        _health.value = current.copy(range = range, end = end, loading = true)
        job?.cancel()
        job = viewModelScope.launch {
            val health = container.health
            val caps = runCatching { health.capabilities() }.getOrNull()
            val today = LocalDate.now()
            val start = range.dateRange(end).first
            val last = minOf(end, today)
            val points = if (caps != null && (caps.stepsRead || caps.exerciseRead || caps.sleepRead) && !start.isAfter(last)) {
                runCatching { health.readDailySeries(start, last) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _health.value = BlockHealthState(
                range = range,
                end = end,
                loading = false,
                days = points.associateBy { it.date },
                stepsGranted = caps?.stepsRead == true,
                exerciseGranted = caps?.exerciseRead == true,
                sleepGranted = caps?.sleepRead == true,
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DiaryBlockViewModel(container) as T
    }
}
