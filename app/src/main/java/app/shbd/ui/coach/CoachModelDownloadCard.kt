package app.shbd.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shbd.R
import app.shbd.services.ondevice.OnDeviceDownloadState
import app.shbd.ui.theme.AppTextOpacity
import app.shbd.ui.theme.ShbdTheme

/**
 * Coach's message while no AI model is ready: two ways to get one — connect a
 * remote (cloud) model, or install the local model, which downloads over any
 * connection. While the local model downloads: progress with Cancel, "waiting
 * for the internet", file check, or the error with Retry. Drawn as a chat
 * item styled like an assistant bubble (also alone in an empty chat).
 */
@Composable
internal fun CoachModelDownloadCard(
    state: OnDeviceDownloadState,
    sizeText: String,
    localSupported: Boolean,
    onSetupRemote: () -> Unit,
    onInstallLocal: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AssistantBadge()
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.coach_model_card_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when (state) {
                is OnDeviceDownloadState.WaitingForNetwork -> {
                    StatusText(stringResource(R.string.coach_model_waiting_network))
                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CancelButton(onCancel)
                }
                is OnDeviceDownloadState.Downloading -> {
                    StatusText(stringResource(R.string.coach_model_downloading, state.progressPercent))
                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CancelButton(onCancel)
                }
                is OnDeviceDownloadState.Verifying -> {
                    StatusText(stringResource(R.string.coach_model_verifying))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is OnDeviceDownloadState.NotDownloaded,
                is OnDeviceDownloadState.Downloaded,
                is OnDeviceDownloadState.Failed -> {
                    if (state is OnDeviceDownloadState.Failed) {
                        Text(
                            stringResource(R.string.coach_model_failed, state.message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    ModelChoices(
                        localLabel = when (state) {
                            // The file is already there (a cloud provider was active): just switch to it.
                            is OnDeviceDownloadState.Downloaded -> stringResource(R.string.coach_model_use_local)
                            is OnDeviceDownloadState.Failed -> stringResource(R.string.action_retry)
                            else -> stringResource(R.string.coach_model_install_local, sizeText)
                        },
                        localSupported = localSupported,
                        onSetupRemote = onSetupRemote,
                        onInstallLocal = onInstallLocal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelChoices(
    localLabel: String,
    localSupported: Boolean,
    onSetupRemote: () -> Unit,
    onInstallLocal: () -> Unit,
) {
    // Each hint sits right above its own button.
    StatusText(
        stringResource(
            if (localSupported) R.string.coach_model_local_hint else R.string.coach_model_local_unsupported,
        ),
    )
    Button(onClick = onInstallLocal, enabled = localSupported, modifier = Modifier.fillMaxWidth()) {
        Text(localLabel)
    }
    StatusText(stringResource(R.string.coach_model_remote_hint))
    OutlinedButton(onClick = onSetupRemote, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.coach_model_remote))
    }
}

/** Step-by-step help for a cloud model, with a jump to Profile → AI Provider. */
@Composable
internal fun CoachRemoteSetupDialog(
    onOpenAiProvider: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.coach_remote_setup_title)) },
        text = { Text(stringResource(R.string.coach_remote_setup_steps)) },
        confirmButton = {
            TextButton(onClick = onOpenAiProvider) { Text(stringResource(R.string.coach_remote_setup_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun StatusText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary),
    )
}

@Composable
private fun CancelButton(onCancel: () -> Unit) {
    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
}

@Composable
private fun CardStatesPreview() {
    ShbdTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    OnDeviceDownloadState.NotDownloaded to true,
                    OnDeviceDownloadState.NotDownloaded to false,
                    OnDeviceDownloadState.WaitingForNetwork(0) to true,
                    OnDeviceDownloadState.Downloading(42) to true,
                    OnDeviceDownloadState.Verifying to true,
                    OnDeviceDownloadState.Failed("No space left on device") to true,
                ).forEach { (state, supported) ->
                    CoachModelDownloadCard(
                        state = state,
                        sizeText = "2.6 GB",
                        localSupported = supported,
                        onSetupRemote = {},
                        onInstallLocal = {},
                        onCancel = {},
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Coach model card — light", heightDp = 1600)
@Composable
private fun CoachModelDownloadCardLightPreview() = CardStatesPreview()

@Preview(
    showBackground = true,
    name = "Coach model card — dark",
    heightDp = 1600,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CoachModelDownloadCardDarkPreview() = CardStatesPreview()

@Preview(showBackground = true, name = "Remote setup dialog — light")
@Composable
private fun CoachRemoteSetupDialogLightPreview() = ShbdTheme { CoachRemoteSetupDialog({}, {}) }

@Preview(
    showBackground = true,
    name = "Remote setup dialog — dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CoachRemoteSetupDialogDarkPreview() = ShbdTheme { CoachRemoteSetupDialog({}, {}) }
