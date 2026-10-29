package app.shbd.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.shbd.R

/** Voice input always uses the system (Android) recognizer; only its language is chosen here. */
@Composable
internal fun SettingsSpeechSection(ui: SettingsUiState, onOpenSheet: (SettingsSheet) -> Unit) {
    SectionCard(title = stringResource(R.string.settings_section_speech)) {
        SettingRow(
            stringResource(R.string.settings_speech_language),
            stringResource(ui.selectedSpeechLanguage.displayNameRes),
            icon = Icons.Outlined.Language
        ) { onOpenSheet(SettingsSheet.SPEECH_LANGUAGE) }
        SettingFootnote(stringResource(ui.selectedSpeech.descriptionRes))
    }
}
