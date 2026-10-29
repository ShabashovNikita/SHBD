package app.shbd.ui.ration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.shbd.AppContainer
import app.shbd.models.RationStepPosition
import app.shbd.models.RationSurveyAnswers
import app.shbd.models.RationSurveyState
import app.shbd.models.RationSurveyStep
import app.shbd.models.RationSurveySteps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RationSurveyUiState(
    /** False until the saved survey is read, so the intro never flashes over a saved step. */
    val loaded: Boolean = false,
    val answers: RationSurveyAnswers = RationSurveyAnswers(),
    val step: RationSurveyStep = RationSurveyStep.INTRO,
    val completed: Boolean = false,
    val position: RationStepPosition? = null,
    val canProceed: Boolean = true,
    val isLastStep: Boolean = false,
    /** Daily goal from the Diary profile (UserProfile.effectiveCalories); null without a profile. */
    val dailyCalories: Int? = null,
)

/** UI snapshot of a saved survey (also used by previews). */
internal fun RationSurveyState.toUiState(kcal: Int?) = RationSurveyUiState(
    loaded = true,
    answers = answers,
    step = step,
    completed = completed,
    position = RationSurveySteps.position(step, answers),
    canProceed = RationSurveySteps.canProceed(step, answers),
    isLastStep = step != RationSurveyStep.SUMMARY && RationSurveySteps.isLast(step, answers),
    dailyCalories = kcal,
)

/**
 * Ration survey state holder. The store comes in as a read Flow + write
 * function so tests swap in a fake; every change is written right away
 * together with the open step (resume after the app is closed).
 */
class RationSurveyViewModel(
    load: Flow<RationSurveyState>,
    private val save: suspend (RationSurveyState) -> Unit,
    dailyCalories: Flow<Int?>,
) : ViewModel() {
    private val survey = MutableStateFlow<RationSurveyState?>(null)

    val state: StateFlow<RationSurveyUiState> =
        combine(survey, dailyCalories) { s, kcal -> s?.toUiState(kcal) ?: RationSurveyUiState(dailyCalories = kcal) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, RationSurveyUiState())

    init {
        viewModelScope.launch {
            survey.value = load.first()
            // StateFlow conflates: writes run in order and the latest state always lands.
            survey.filterNotNull().drop(1).collect { save(it) }
        }
    }

    private fun mutate(transform: (RationSurveyState) -> RationSurveyState) {
        val current = survey.value ?: return
        survey.value = transform(current)
    }

    fun start() = mutate { it.start() }

    fun update(transform: (RationSurveyAnswers) -> RationSurveyAnswers) =
        mutate { it.copy(answers = transform(it.answers)) }

    fun next() = mutate { it.next() }

    fun back() = mutate { it.back() }

    fun finish() = mutate { it.finish() }

    fun editAnswers() = mutate { it.editAnswers() }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RationSurveyViewModel(
                load = container.prefs.rationSurveyState,
                save = container.prefs::setRationSurveyState,
                dailyCalories = container.prefs.userProfile.map { it?.effectiveCalories },
            ) as T
    }
}
