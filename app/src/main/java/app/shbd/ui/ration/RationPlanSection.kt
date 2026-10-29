package app.shbd.ui.ration

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shbd.R
import app.shbd.models.RationMeal
import app.shbd.models.RationPlan
import app.shbd.models.RationPlanDay
import app.shbd.models.RationPlanSlot
import app.shbd.models.RationSlotMode
import app.shbd.services.ai.userMessage
import app.shbd.services.ondevice.ModelCatalog
import app.shbd.services.ondevice.OnDeviceDownloadState
import app.shbd.ui.coach.AiModelSetupContent
import app.shbd.ui.coach.CoachRemoteSetupDialog
import app.shbd.ui.components.ShbdSurface
import app.shbd.ui.components.energyText
import app.shbd.ui.theme.AppColors
import app.shbd.ui.theme.AppTextOpacity
import app.shbd.ui.theme.ShbdTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Callbacks of the weekly menu block. */
class RationPlanActions(
    val onBuildMenu: () -> Unit = {},
    val onInstallLocal: () -> Unit = {},
    val onCancelDownload: () -> Unit = {},
    val onShowRemoteHelp: (Boolean) -> Unit = {},
    val onOpenAiProvider: () -> Unit = {},
)

/**
 * Weekly menu block under the survey summary: build button, progress, the
 * "pick a model" card (install the local model or connect a remote one, as in
 * Coach), error with retry, or the saved plan as a simple 7-day list.
 * Dish names come from the catalog and stay Russian in every UI language.
 */
@Composable
internal fun RationPlanSection(ui: RationPlanUiState, actions: RationPlanActions) {
    if (!ui.loaded) return
    val phase = ui.phase
    val onBuildMenu = actions.onBuildMenu
    if (ui.showRemoteHelp) {
        CoachRemoteSetupDialog(
            onOpenAiProvider = actions.onOpenAiProvider,
            onDismiss = { actions.onShowRemoteHelp(false) },
            steps = stringResource(R.string.ration_remote_setup_steps),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (phase) {
            RationPlanPhase.Building -> ShbdSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.ration_plan_building), style = MaterialTheme.typography.bodyMedium)
                }
            }
            RationPlanPhase.NeedModel -> ModelCard(ui, actions)
            is RationPlanPhase.Failed -> MessageCard(
                text = failureText(phase.failure),
                action = stringResource(R.string.ration_plan_retry),
                onAction = onBuildMenu,
                isError = true,
            )
            RationPlanPhase.Idle -> if (ui.plan == null) {
                Button(onClick = onBuildMenu, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.ration_plan_build))
                }
            }
        }
        ui.plan?.let { PlanContent(it, showRebuild = phase == RationPlanPhase.Idle, onRebuild = onBuildMenu) }
    }
}

@Composable
private fun failureText(failure: RationPlanFailure): String = when (failure) {
    is RationPlanFailure.NoDishes -> stringResource(
        R.string.ration_plan_no_dishes,
        failure.meals.sortedBy { it.ordinal }.map { stringResource(RationSurveyLabels.meal(it)) }.joinToString(", "),
    )
    RationPlanFailure.BadAnswer -> stringResource(R.string.ration_plan_bad_answer)
    is RationPlanFailure.Ai -> failure.error.userMessage(LocalContext.current)
    is RationPlanFailure.Other -> stringResource(R.string.ration_plan_failed)
}

/** No model yet: the same two choices as the Coach card; the menu follows once a model is ready. */
@Composable
private fun ModelCard(ui: RationPlanUiState, actions: RationPlanActions) {
    val context = LocalContext.current
    val sizeText = remember { Formatter.formatShortFileSize(context, ModelCatalog.default.sizeBytes) }
    ShbdSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiModelSetupContent(
                text = stringResource(R.string.ration_plan_model_text),
                state = ui.downloadState,
                sizeText = sizeText,
                localSupported = ui.localSupported,
                onSetupRemote = { actions.onShowRemoteHelp(true) },
                onInstallLocal = actions.onInstallLocal,
                onCancel = actions.onCancelDownload,
            )
        }
    }
}

@Composable
private fun MessageCard(text: String, action: String, onAction: () -> Unit, isError: Boolean = false) {
    ShbdSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

@Composable
private fun PlanContent(plan: RationPlan, showRebuild: Boolean, onRebuild: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.ration_plan_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        ShbdSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val budget = plan.budgetRub
                Text(
                    if (budget != null) {
                        stringResource(R.string.ration_plan_week_cost, rubText(plan.weekCostRub), rubText(budget))
                    } else {
                        stringResource(R.string.ration_plan_week_cost_unlimited, rubText(plan.weekCostRub))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (plan.overBudgetRub > 0) {
                    Text(
                        stringResource(R.string.ration_plan_over_budget, rubText(plan.overBudgetRub)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(stringResource(R.string.ration_plan_prices_note), style = MaterialTheme.typography.bodySmall, color = muted)
                if (plan.repeatsRaised) Text(stringResource(R.string.ration_plan_repeats_raised), style = MaterialTheme.typography.bodySmall, color = muted)
                if (plan.calorieGoalAssumed) Text(stringResource(R.string.ration_plan_goal_assumed), style = MaterialTheme.typography.bodySmall, color = muted)
                if (plan.comment.isNotBlank()) {
                    Text(plan.comment, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
            }
        }
        plan.days.forEach { DayCard(it) }
        if (showRebuild) {
            Button(onClick = onRebuild, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.ration_plan_rebuild))
            }
        }
    }
}

@Composable
private fun DayCard(day: RationPlanDay) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
    ShbdSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dayTitle(day.date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(energyText(day.kcal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AppColors.Calorie)
            }
            day.slots.forEach { slot ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row {
                    Text(
                        stringResource(RationSurveyLabels.meal(slot.meal)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        modifier = Modifier.weight(0.3f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(0.7f)) {
                        Text(slot.recipeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        slotNote(slot)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        energyText(slot.kcal),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun slotNote(slot: RationPlanSlot): String? = when {
    slot.mode == RationSlotMode.REHEAT -> stringResource(R.string.ration_plan_reheat)
    slot.portions > 1 -> stringResource(R.string.ration_plan_cook_portions, slot.portions)
    else -> null
}

/** "Пн, 3 нояб." / "Mon, 3 Nov"; the raw text if the date does not parse. */
private fun dayTitle(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
}.getOrDefault(iso)

// -- Previews -------------------------------------------------------------

private val previewPlan = RationPlan(
    startDate = "2026-11-02",
    calorieGoal = 1_850,
    portionFactor = 1.1,
    days = (0 until 7).map { d ->
        RationPlanDay(
            date = LocalDate.of(2026, 11, 2).plusDays(d.toLong()).toString(),
            slots = listOf(
                RationPlanSlot(RationMeal.BREAKFAST, "oml_classic", "Омлет с сыром и овощами", kcal = 420, costRub = 80),
                if (d == 3) {
                    RationPlanSlot(RationMeal.DINNER, "plov", "Плов с курицей", RationSlotMode.REHEAT, portions = 0, kcal = 640, costRub = 95)
                } else {
                    RationPlanSlot(RationMeal.DINNER, "plov", "Плов с курицей", portions = if (d == 2) 2 else 1, kcal = 640, costRub = 95)
                },
            ),
        )
    },
    weekCostRub = 2_350,
    budgetRub = 2_000,
    overBudgetRub = 350,
    comment = "Сытные ужины и быстрые завтраки, в среду ужин на два дня.",
)

@Composable
private fun SectionPreview(ui: RationPlanUiState, dark: Boolean) = ShbdTheme(darkTheme = dark, useDynamicColor = false) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            RationPlanSection(ui, RationPlanActions())
        }
    }
}

@Preview(showBackground = true) @Composable private fun NoPlanLightPreview() =
    SectionPreview(RationPlanUiState(loaded = true), dark = false)
@Preview(showBackground = true) @Composable private fun NoPlanDarkPreview() =
    SectionPreview(RationPlanUiState(loaded = true), dark = true)
@Preview(showBackground = true) @Composable private fun NeedModelLightPreview() =
    SectionPreview(RationPlanUiState(loaded = true, phase = RationPlanPhase.NeedModel), dark = false)
@Preview(showBackground = true) @Composable private fun NeedModelDarkPreview() =
    SectionPreview(RationPlanUiState(loaded = true, phase = RationPlanPhase.NeedModel), dark = true)
@Preview(showBackground = true) @Composable private fun DownloadingLightPreview() =
    SectionPreview(RationPlanUiState(loaded = true, phase = RationPlanPhase.NeedModel, downloadState = OnDeviceDownloadState.Downloading(42)), dark = false)
@Preview(showBackground = true, heightDp = 1600) @Composable private fun PlanLightPreview() =
    SectionPreview(RationPlanUiState(loaded = true, plan = previewPlan), dark = false)
@Preview(showBackground = true, heightDp = 1600) @Composable private fun PlanDarkPreview() =
    SectionPreview(RationPlanUiState(loaded = true, plan = previewPlan), dark = true)
