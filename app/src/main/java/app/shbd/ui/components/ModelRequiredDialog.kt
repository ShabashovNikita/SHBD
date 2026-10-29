package app.shbd.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.shbd.AppContainer
import app.shbd.R
import app.shbd.services.ondevice.ModelRequiredKind
import app.shbd.ui.navigation.ShbdRoutes
import app.shbd.ui.theme.ShbdTheme

/**
 * "This feature needs an AI model" with a jump to the Coach tab, where the
 * local model is installed or a remote one connected. Shown before photo / text analysis or goal
 * recalculation starts, so no draft or analyzing screen appears first.
 */
@Composable
fun ModelRequiredDialog(
    kind: ModelRequiredKind,
    onOpenCoach: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_required_title)) },
        text = {
            Text(
                stringResource(
                    when (kind) {
                        ModelRequiredKind.FOOD -> R.string.model_required_food
                        ModelRequiredKind.GOALS -> R.string.model_required_goals
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenCoach) { Text(stringResource(R.string.model_required_open_coach)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** Opens the Coach tab through the `shbd://go/coach` destination inbox. */
fun openCoachTab(container: AppContainer) {
    container.launchDestinationInbox.value = ShbdRoutes.COACH
}

@Preview(showBackground = true, name = "Model required — food, light")
@Preview(
    showBackground = true,
    name = "Model required — food, dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ModelRequiredFoodPreview() {
    ShbdTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ModelRequiredDialog(ModelRequiredKind.FOOD, onOpenCoach = {}, onDismiss = {})
        }
    }
}

@Preview(showBackground = true, name = "Model required — goals, light")
@Preview(
    showBackground = true,
    name = "Model required — goals, dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ModelRequiredGoalsPreview() {
    ShbdTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ModelRequiredDialog(ModelRequiredKind.GOALS, onOpenCoach = {}, onDismiss = {})
        }
    }
}
