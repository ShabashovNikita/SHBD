package app.shbd.ui.ration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.shbd.AppContainer
import app.shbd.models.RationMeal
import app.shbd.models.RationPlan
import app.shbd.services.ai.AiError
import app.shbd.services.ondevice.AiAvailability
import app.shbd.services.ondevice.ModelCatalog
import app.shbd.services.ondevice.OnDeviceDownloadState
import app.shbd.services.ration.RationPlanError
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why the last build failed; the screen turns it into text. */
sealed interface RationPlanFailure {
    class NoDishes(val meals: Set<RationMeal>) : RationPlanFailure
    object BadAnswer : RationPlanFailure
    class Ai(val error: AiError) : RationPlanFailure
    class Other(val message: String?) : RationPlanFailure
}

sealed interface RationPlanPhase {
    object Idle : RationPlanPhase
    object Building : RationPlanPhase

    /** No model: the "pick a model" card; the menu is built by itself once a model is ready. */
    object NeedModel : RationPlanPhase
    class Failed(val failure: RationPlanFailure) : RationPlanPhase
}

/** [plan] is the saved menu (kept while building and after a failure). */
data class RationPlanUiState(
    val loaded: Boolean = false,
    val plan: RationPlan? = null,
    val phase: RationPlanPhase = RationPlanPhase.Idle,
    /** Local model download, shown on the "pick a model" card. */
    val downloadState: OnDeviceDownloadState = OnDeviceDownloadState.NotDownloaded,
    /** The device can run the local model. */
    val localSupported: Boolean = true,
    /** The "connect a remote model" instructions are open. */
    val showRemoteHelp: Boolean = false,
)

/**
 * Weekly menu on the Ration summary: checks the AI model, runs the build and
 * shows the saved plan. Watches the model live: once a model is ready (set
 * up here, in Coach or in Profile → AI), the "pick a model" card goes away and
 * the requested menu is built. The store and the service come in as
 * functions so tests swap in fakes.
 */
class RationPlanViewModel(
    plan: Flow<RationPlan?>,
    private val availability: Flow<AiAvailability>,
    private val build: suspend (today: LocalDate) -> RationPlan,
    private val today: () -> LocalDate = LocalDate::now,
    downloadState: Flow<OnDeviceDownloadState> = flowOf(OnDeviceDownloadState.NotDownloaded),
    private val localSupported: Boolean = true,
    private val installLocal: suspend () -> Unit = {},
    private val cancelDownload: () -> Unit = {},
) : ViewModel() {
    private val phase = MutableStateFlow<RationPlanPhase>(RationPlanPhase.Idle)
    private val remoteHelp = MutableStateFlow(false)
    private var job: Job? = null

    val state: StateFlow<RationPlanUiState> =
        combine(plan, phase, downloadState, remoteHelp) { p, ph, download, help ->
            RationPlanUiState(
                loaded = true,
                plan = p,
                phase = ph,
                downloadState = download,
                localSupported = localSupported,
                showRemoteHelp = help,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RationPlanUiState())

    init {
        availability
            .onEach { a ->
                if (phase.value != RationPlanPhase.NeedModel || a == AiAvailability.MODEL_MISSING) return@onEach
                phase.value = RationPlanPhase.Idle
                remoteHelp.value = false
                if (a == AiAvailability.READY) buildMenu()
            }
            .launchIn(viewModelScope)
    }

    fun buildMenu() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            if (availability.first() == AiAvailability.MODEL_MISSING) {
                phase.value = RationPlanPhase.NeedModel
                return@launch
            }
            phase.value = RationPlanPhase.Building
            phase.value = try {
                build(today())
                RationPlanPhase.Idle
            } catch (e: CancellationException) {
                phase.value = RationPlanPhase.Idle
                throw e
            } catch (e: RationPlanError.NoDishes) {
                RationPlanPhase.Failed(RationPlanFailure.NoDishes(e.meals))
            } catch (e: RationPlanError.BadAnswer) {
                RationPlanPhase.Failed(RationPlanFailure.BadAnswer)
            } catch (e: AiError.OnDeviceModelNotDownloaded) {
                RationPlanPhase.NeedModel
            } catch (e: AiError) {
                RationPlanPhase.Failed(RationPlanFailure.Ai(e))
            } catch (e: Exception) {
                RationPlanPhase.Failed(RationPlanFailure.Other(e.message))
            }
        }
    }

    /** "Install the local model": makes it the active AI and downloads it. */
    fun installLocalModel() {
        if (!localSupported) return
        viewModelScope.launch { installLocal() }
    }

    fun cancelModelDownload() = cancelDownload()

    fun showRemoteHelp(show: Boolean) {
        remoteHelp.value = show
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RationPlanViewModel(
                plan = container.prefs.rationPlan,
                availability = container.aiAvailability,
                build = { today ->
                    container.rationMenu.build(
                        answers = container.prefs.rationSurveyState.first().answers,
                        calorieGoal = container.prefs.userProfile.map { it?.effectiveCalories }.first(),
                        today = today,
                    )
                },
                downloadState = container.onDeviceModelDownloadManager.state(ModelCatalog.default.modelId),
                localSupported = container.onDeviceSupported,
                // Same as Coach: make the local model active and download it over any connection.
                installLocal = {
                    container.prefs.useEmbeddedModel()
                    val entry = ModelCatalog.default
                    if (!container.onDeviceModelDownloadManager.isDownloaded(entry)) {
                        container.onDeviceModelDownloadManager.startDownload(entry, overWifiOnly = false)
                    }
                },
                cancelDownload = { container.onDeviceModelDownloadManager.cancelDownload() },
            ) as T
    }
}
