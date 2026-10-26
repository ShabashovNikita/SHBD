package app.shbd.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.shbd.AppContainer
import app.shbd.R
import app.shbd.data.ManualDayMetric
import app.shbd.data.ManualDayMetricsRepository
import app.shbd.ui.components.ShbdDialog
import app.shbd.ui.components.ShbdDialogActions
import app.shbd.ui.components.ShbdTextField
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Type in the day's steps by hand. [hasManual] shows "Use Health Connect", which
 * clears the manual value so the day falls back to Health Connect again.
 */
@Composable
internal fun ManualStepsDialog(
    initial: Long?,
    hasManual: Boolean,
    onSave: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial?.takeIf { it > 0 }?.toString().orEmpty()) }
    val steps = text.trim().toLongOrNull()
    val valid = steps != null && steps in 0..ManualDayMetricsRepository.MAX_STEPS
    ShbdDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.diary_manual_steps_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        ShbdTextField(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
            placeholder = stringResource(R.string.diary_manual_steps_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        ManualMetricActions(
            hasManual = hasManual,
            valid = valid,
            onSave = { steps?.let(onSave) },
            onClear = onClear,
            onDismiss = onDismiss,
        )
    }
}

/** Type in last night's sleep as hours + minutes for the selected day. */
@Composable
internal fun ManualSleepDialog(
    initialMinutes: Int?,
    hasManual: Boolean,
    onSave: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val start = initialMinutes?.takeIf { it > 0 }
    var hours by rememberSaveable { mutableStateOf(start?.let { (it / 60).toString() }.orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(start?.let { (it % 60).toString() }.orEmpty()) }
    val h = hours.trim().ifEmpty { "0" }.toIntOrNull()
    val m = minutes.trim().ifEmpty { "0" }.toIntOrNull()
    val total = if (h != null && m != null && m in 0..59) h * 60 + m else null
    val valid = total != null && total in 1..ManualDayMetricsRepository.MAX_SLEEP_MINUTES
    ShbdDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.diary_manual_sleep_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShbdTextField(
                value = hours,
                onValueChange = { v -> hours = v.filter { it.isDigit() }.take(2) },
                placeholder = stringResource(R.string.diary_manual_sleep_hours),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            ShbdTextField(
                value = minutes,
                onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(2) },
                placeholder = stringResource(R.string.diary_manual_sleep_minutes),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        ManualMetricActions(
            hasManual = hasManual,
            valid = valid,
            onSave = { total?.let(onSave) },
            onClear = onClear,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ManualMetricActions(
    hasManual: Boolean,
    valid: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (hasManual) {
        TextButton(onClick = onClear) { Text(stringResource(R.string.diary_manual_use_health)) }
    }
    ShbdDialogActions(
        primaryText = stringResource(R.string.action_save),
        onPrimary = onSave,
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        primaryEnabled = valid,
    )
}

/**
 * The steps and sleep editors for [date], shared by the dashboard and the Steps /
 * Sleep block screens. Prefills with the manual value, else the Health Connect one.
 */
@Composable
internal fun ManualMetricEditors(
    container: AppContainer,
    date: LocalDate,
    manual: ManualDayMetric?,
    healthSteps: Long?,
    healthSleepMinutes: Int?,
    showSteps: Boolean,
    showSleep: Boolean,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repo = container.manualDayMetricsRepository
    if (showSteps) {
        ManualStepsDialog(
            initial = manual?.steps ?: healthSteps,
            hasManual = manual?.steps != null,
            onSave = { steps -> scope.launch { repo.setSteps(date, steps) }; onClose() },
            onClear = { scope.launch { repo.setSteps(date, null) }; onClose() },
            onDismiss = onClose,
        )
    }
    if (showSleep) {
        ManualSleepDialog(
            initialMinutes = manual?.sleepMinutes ?: healthSleepMinutes,
            hasManual = manual?.sleepMinutes != null,
            onSave = { minutes -> scope.launch { repo.setSleepMinutes(date, minutes) }; onClose() },
            onClear = { scope.launch { repo.setSleepMinutes(date, null) }; onClose() },
            onDismiss = onClose,
        )
    }
}
