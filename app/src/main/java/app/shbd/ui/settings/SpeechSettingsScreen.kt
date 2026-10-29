package app.shbd.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import app.shbd.AppContainer
import app.shbd.R
import app.shbd.ui.navigation.ShbdRoutes

/** Voice input language (system recognizer only). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }

    SettingsSubScreen(
        title = stringResource(R.string.settings_section_speech),
        onBack = onBack,
    ) {
        SettingsSpeechSection(
            ui = ui,
            onOpenSheet = { sheet = it },
        )
        RelatedLinks(
            rows = listOf(
                RelatedLink(label = stringResource(R.string.settings_group_ai)) {
                    nav.navigate(ShbdRoutes.SETTINGS_AI)
                },
            ),
        )
    }

    sheet?.let { s ->
        SettingsSheets(
            sheet = s,
            ui = ui,
            vm = vm,
            onDismiss = { sheet = null },
            onInvalidGoalWeight = {},
            onRebalanceBlocked = {},
        )
    }
}
