package app.shbd.ui.ration

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shbd.AppContainer
import app.shbd.models.RationSurveyState
import app.shbd.models.RationSurveyStep

/**
 * Ration tab: survey intro, one question step or the summary, all on one
 * route. System Back walks the survey steps after the first one; elsewhere it
 * falls through to the app-wide "back to Diary".
 */
@Composable
fun RationScreen(container: AppContainer) {
    val vm: RationSurveyViewModel = viewModel(factory = RationSurveyViewModel.Factory(container))
    val ui by vm.state.collectAsState()

    BackHandler(enabled = (ui.position?.number ?: 0) > 1) { vm.back() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Until the saved survey is read: blank, so the intro never flashes over a saved step.
            if (!ui.loaded) return@Box
            AnimatedContent(
                targetState = ui.step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "rationSurveyStep",
            ) { step ->
                // AnimatedContent keeps the outgoing step on screen: draw it from the live answers.
                val shown = if (step == ui.step) ui else RationSurveyState(ui.answers, step, ui.completed).toUiState(ui.dailyCalories)
                when (step) {
                    RationSurveyStep.INTRO -> RationIntro(onStart = vm::start)
                    RationSurveyStep.SUMMARY -> RationSummaryScreen(shown, onEditAnswers = vm::editAnswers)
                    else -> RationSurveyStepFrame(shown, onUpdate = vm::update, onNext = vm::next, onBack = vm::back)
                }
            }
        }
    }
}
