package app.shbd.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.shbd.AppContainer
import app.shbd.models.WeightEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Dashboard values HomeViewModel does not carry: workouts, sleep and weight. */
data class DashboardExtras(
    val date: LocalDate = LocalDate.now(),
    val exerciseMinutes: Int? = null,
    val sleepMinutes: Int? = null,
    val latestWeight: WeightEntry? = null,
    val stepsGranted: Boolean = false,
    val sleepGranted: Boolean = false,
    val exerciseGranted: Boolean = false,
)

/**
 * Light companion to [app.shbd.ui.home.HomeViewModel] for the Diary dashboard.
 * Calories, macros, water and steps come from HomeViewModel (single source of
 * the goal math); this one only reads Health Connect sleep/workout aggregates
 * for the selected day and tracks the latest weigh-in.
 */
class DiaryDashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val _extras = MutableStateFlow(DashboardExtras())
    val extras: StateFlow<DashboardExtras> = _extras.asStateFlow()

    private var weights: List<WeightEntry> = emptyList()
    private var healthJob: Job? = null

    init {
        viewModelScope.launch {
            container.weightRepository.entries.collect { list ->
                weights = list
                _extras.update {
                    it.copy(latestWeight = DiaryDashboardMath.latestWeightOnOrBefore(list, it.date))
                }
            }
        }
    }

    /** Re-read Health Connect for [date]; call on day change and on app resume. */
    fun load(date: LocalDate) {
        _extras.update {
            it.copy(
                date = date,
                latestWeight = DiaryDashboardMath.latestWeightOnOrBefore(weights, date),
                exerciseMinutes = if (it.date == date) it.exerciseMinutes else null,
                sleepMinutes = if (it.date == date) it.sleepMinutes else null,
            )
        }
        healthJob?.cancel()
        healthJob = viewModelScope.launch {
            val health = container.health
            val caps = runCatching { health.capabilities() }.getOrNull()
            val daysBack = ChronoUnit.DAYS.between(date, LocalDate.now())
            // HC readers walk back from today one aggregate per day; past the
            // window (or for future days) there is nothing to show.
            val inWindow = daysBack in 0 until HEALTH_WINDOW_DAYS
            val days = (daysBack + 1).toInt()
            val activity = if (inWindow && caps != null && (caps.stepsRead || caps.exerciseRead)) {
                runCatching { health.readDailyActivity(days) }.getOrNull()?.firstOrNull { it.date == date }
            } else null
            val wellness = if (inWindow && caps?.sleepRead == true) {
                runCatching { health.readDailyWellness(days) }.getOrNull()?.firstOrNull { it.date == date }
            } else null
            _extras.update {
                if (it.date != date) it else it.copy(
                    exerciseMinutes = activity?.exerciseMinutes?.takeIf { m -> caps?.exerciseRead == true && m > 0 },
                    sleepMinutes = wellness?.sleepMinutes,
                    stepsGranted = caps?.stepsRead == true,
                    sleepGranted = caps?.sleepRead == true,
                    exerciseGranted = caps?.exerciseRead == true,
                )
            }
        }
    }

    fun addWeight(kg: Double, at: Instant) {
        viewModelScope.launch {
            val event = container.weightRepository.addEntry(WeightEntry(weightKg = kg, date = at))
            if (event != null &&
                container.prefs.notificationsEnabled.first() &&
                container.prefs.goalReachedNotificationsEnabled.first()
            ) {
                container.notifications.showGoalReached()
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DiaryDashboardViewModel(container) as T
    }

    private companion object {
        const val HEALTH_WINDOW_DAYS = 30L
    }
}
