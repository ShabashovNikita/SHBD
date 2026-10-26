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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    container: AppContainer,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }

    SettingsSubScreen(
        title = stringResource(R.string.settings_group_ai),
        onBack = onBack,
    ) {
        SettingsAiSection(
            ui = ui,
            vm = vm,
            onOpenSheet = { sheet = it },
        )
        SettingsFallbackSection(
            ui = ui,
            vm = vm,
            onOpenSheet = { sheet = it },
        )
        // Speech and Coach are their own screens now; serving-unit behavior is
        // edited in Food & Entry.
        RelatedLinks(
            rows = listOf(
                RelatedLink(label = stringResource(R.string.settings_section_speech)) {
                    nav.navigate(ShbdRoutes.SETTINGS_SPEECH)
                },
                RelatedLink(label = stringResource(R.string.settings_group_coach)) {
                    nav.navigate(ShbdRoutes.SETTINGS_COACH)
                },
                RelatedLink(label = stringResource(R.string.settings_group_food)) {
                    nav.navigate(ShbdRoutes.SETTINGS_FOOD)
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
            onOpenSheet = { sheet = it },
        )
    }
}
