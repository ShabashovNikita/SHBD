package app.shbd.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.shbd.R
import app.shbd.models.AIProvider
import app.shbd.models.AiProfilePreset
import app.shbd.models.LocaleFormat
import app.shbd.services.ondevice.OnDeviceDownloadState

private fun leftoverGb(bytes: Long): String =
    "${LocaleFormat.decimal(bytes / 1_073_741_824.0, 1)} GB"

/**
 * One "AI profile" card: the active profile and everything it is made of —
 * provider, model, key, endpoint, the on-device model file. Profiles are
 * added, renamed and deleted in the profile picker sheet; every row below the
 * picker edits the active profile. Entry-flow behavior (serving sizes, photo note,
 * constituents) lives in Food & Entry — linked from the AI screen footer.
 */
@Composable
internal fun SettingsAiSection(
    ui: SettingsUiState,
    vm: SettingsViewModel,
    onOpenSheet: (SettingsSheet) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_section_ai_profile)) {
        // AI features are always on: there is no master switch here.
        // Named AI profile: everything below edits the active profile; other
        // profiles keep their own provider, model, endpoint and key.
        SettingRow(
            stringResource(R.string.settings_ai_profile),
            // Name only: the provider has its own row below, and a long
            // "name · provider" value crowded the label.
            ui.activeProfile?.name.orEmpty(),
            icon = Icons.Outlined.AccountCircle
        ) { onOpenSheet(SettingsSheet.AI_PROFILE) }
        HorizontalDivider()
        // Provider picker: grouped Recommended / Cloud / Local / On-device
        // with one-line subtitles (ListSheet sectionHeader). The default
        // "Local model" profile always runs on the device, so it has none.
        if (ui.activeProfile?.preset != AiProfilePreset.LOCAL) {
            SettingRow(
                stringResource(R.string.settings_ai_provider),
                stringResource(ui.activeProvider.displayNameRes),
                icon = Icons.Outlined.SmartToy
            ) { onOpenSheet(SettingsSheet.AI_PROVIDER) }
            HorizontalDivider()
        }
        SettingRow(stringResource(R.string.settings_ai_model), ui.activeModel.ifEmpty { stringResource(R.string.settings_ai_model_unset) }, icon = Icons.Outlined.Tune) { onOpenSheet(SettingsSheet.AI_MODEL) }
        if (ui.activeProvider.requiresApiKey) {
            HorizontalDivider()
            // ✓/✗ mirrors the fresh key-test result from the key sheet.
            val keySubtitle = ui.apiKeyMasked.ifEmpty { stringResource(R.string.settings_not_set) } +
                when (ui.apiKeyTestOk) {
                    true -> "  ✓"
                    false -> "  ✗"
                    null -> ""
                }
            SettingRow(stringResource(R.string.settings_api_key), keySubtitle, icon = Icons.Outlined.Key) { onOpenSheet(SettingsSheet.API_KEY) }
        }
        if (ui.activeProvider.requiresCustomEndpoint || ui.activeProvider == AIProvider.OLLAMA) {
            HorizontalDivider()
            SettingRow(
                if (ui.activeProvider.requiresCustomEndpoint) stringResource(R.string.settings_base_url) else stringResource(R.string.settings_server_url),
                ui.activeProfile?.baseUrl.orEmpty().ifEmpty { stringResource(R.string.settings_not_set) },
                icon = Icons.Outlined.Link
            ) { onOpenSheet(SettingsSheet.CUSTOM_BASE_URL) }
            // Issue #8 follow-up: release builds allow cleartext only via this
            // opt-in; the AI layer rejects http:// URLs while it is off.
            HorizontalDivider()
            ToggleRow(
                stringResource(R.string.settings_ai_allow_insecure_http),
                ui.allowInsecureHttp,
                icon = Icons.Outlined.Warning,
                onChange = { vm.setAllowInsecureHttp(it) }
            )
            SettingFootnote(stringResource(R.string.settings_ai_allow_insecure_http_footer))
        }
        val downloadManager = vm.container.onDeviceModelDownloadManager
        val storageEpoch by downloadManager.storageEpoch.collectAsState()
        val leftoverBytes = remember(storageEpoch) { downloadManager.occupiedBytes() }
        if (ui.activeProvider == AIProvider.ON_DEVICE) {
            HorizontalDivider()
            val downloadState by downloadManager.state(ui.activeModel)
                .collectAsState(initial = OnDeviceDownloadState.NotDownloaded)
            val subtitle = when (val s = downloadState) {
                is OnDeviceDownloadState.Downloaded ->
                    stringResource(R.string.settings_on_device_model_ready)
                is OnDeviceDownloadState.Downloading ->
                    stringResource(R.string.on_device_model_downloading, s.progressPercent)
                is OnDeviceDownloadState.WaitingForNetwork ->
                    stringResource(R.string.coach_model_waiting_network)
                is OnDeviceDownloadState.Verifying ->
                    stringResource(R.string.on_device_model_verifying)
                is OnDeviceDownloadState.Failed ->
                    stringResource(R.string.on_device_download_failed)
                is OnDeviceDownloadState.NotDownloaded ->
                    if (leftoverBytes > 0L) leftoverGb(leftoverBytes)
                    else stringResource(R.string.settings_on_device_model_not_downloaded)
            }
            SettingRow(
                stringResource(R.string.settings_on_device_model),
                subtitle,
                icon = Icons.Outlined.Download
            ) { onOpenSheet(SettingsSheet.ON_DEVICE_MODEL) }
            SettingFootnote(stringResource(R.string.settings_on_device_accuracy_footer))
        } else if (leftoverBytes > 0L) {
            // A downloaded local model while a cloud provider is active still
            // needs a path to delete the file.
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.settings_on_device_model),
                leftoverGb(leftoverBytes),
                icon = Icons.Outlined.Download
            ) { onOpenSheet(SettingsSheet.ON_DEVICE_MODEL) }
            SettingFootnote(
                stringResource(R.string.on_device_model_leftover_footer, leftoverGb(leftoverBytes))
            )
        }
        // Privacy disclosure per provider: cloud providers receive food/chat/
        // profile data; only on-device Gemma 4 keeps everything local.
        SettingFootnote(
            when (ui.activeProvider) {
                AIProvider.ON_DEVICE -> stringResource(R.string.settings_ai_privacy_ondevice)
                AIProvider.OLLAMA -> stringResource(R.string.settings_ai_privacy_ollama)
                else -> stringResource(R.string.settings_ai_privacy_cloud)
            }
        )
        // Add / rename / delete live only in the "AI profile" picker sheet.
    }
}
