package app.shbd.ui.ration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shbd.R
import app.shbd.models.Allergen
import app.shbd.models.Cuisine
import app.shbd.models.RationMeal
import app.shbd.models.RationSurveyAnswers
import app.shbd.models.RationSurveyStep
import app.shbd.ui.components.ShbdSurface
import app.shbd.ui.components.energyText
import app.shbd.ui.navigation.BottomNavScrollPadding
import app.shbd.ui.theme.AppColors
import app.shbd.ui.theme.AppTextOpacity
import app.shbd.ui.theme.ShbdTheme
import java.time.DayOfWeek

/** Survey summary: Diary calorie goal, every answer, then the weekly menu block. */
@Composable
internal fun RationSummaryScreen(
    ui: RationSurveyUiState,
    onEditAnswers: () -> Unit,
    plan: RationPlanUiState = RationPlanUiState(loaded = true),
    planActions: RationPlanActions = RationPlanActions(),
) {
    val a = ui.answers
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = BottomNavScrollPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.ration_survey_summary_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        ShbdSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            Column {
                Text(stringResource(R.string.ration_survey_summary_calories), style = MaterialTheme.typography.bodyMedium, color = muted)
                Spacer(Modifier.height(4.dp))
                Text(
                    ui.dailyCalories?.let { energyText(it) } ?: stringResource(R.string.ration_survey_summary_calories_missing),
                    style = if (ui.dailyCalories != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Calorie,
                )
            }
        }
        ShbdSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            Column {
                SummaryRows(a)
            }
        }
        RationPlanSection(plan, planActions)
        OutlinedButton(onClick = onEditAnswers, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.ration_survey_summary_edit))
        }
    }
}

@Composable
private fun SummaryRows(a: RationSurveyAnswers) {
    val none = stringResource(R.string.ration_survey_summary_none)
    // Lists keep the on-screen option order, not the tap order.
    @Composable
    fun <T : Enum<T>> joined(set: Set<T>, label: (T) -> Int): String =
        set.sortedBy { it.ordinal }.map { stringResource(label(it)) }.joinToString(", ").ifEmpty { none }

    val budget = if (a.budgetUnlimited) {
        stringResource(R.string.ration_survey_budget_unlimited)
    } else {
        stringResource(
            R.string.ration_survey_summary_budget_value,
            rubText(a.weeklyBudgetRub),
            stringResource(RationSurveyLabels.budgetLevel(RationSurveyAnswers.budgetLevel(a.weeklyBudgetRub))),
        )
    }
    val rows = buildList {
        add(R.string.ration_survey_summary_meals to joined(a.meals, RationSurveyLabels::meal))
        add(R.string.ration_survey_summary_budget to budget)
        add(R.string.ration_survey_summary_allergies to joined(a.allergens, RationSurveyLabels::allergen))
        add(R.string.ration_survey_summary_dislikes to joined(a.dislikes, RationSurveyLabels::dislike))
        add(R.string.ration_survey_summary_stop_list to a.stopFoods.joinToString(", ").ifEmpty { none })
        add(R.string.ration_survey_summary_cooking to stringResource(RationSurveyLabels.cooking(a.cookingTime)))
        if (a.cooks) {
            add(
                R.string.ration_survey_summary_repeats to
                    pluralStringResource(R.plurals.ration_survey_repeat_times, a.repeatLimit, a.repeatLimit),
            )
            add(R.string.ration_survey_summary_cook_days to joined(a.cookDays, RationSurveyLabels::day))
            add(R.string.ration_survey_summary_kitchen to joined(a.kitchen, RationSurveyLabels::tool))
        }
        add(R.string.ration_survey_summary_cuisines to joined(a.cuisines, RationSurveyLabels::cuisine))
        add(
            R.string.ration_survey_summary_delivery to stringResource(
                if (a.deliveryWanted) R.string.ration_survey_summary_delivery_yes else R.string.ration_survey_summary_delivery_no,
            ),
        )
    }
    rows.forEachIndexed { i, (label, value) ->
        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.weight(0.45f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.55f),
            )
        }
    }
}

private val previewAnswers = RationSurveyAnswers(
    meals = setOf(RationMeal.BREAKFAST, RationMeal.DINNER),
    weeklyBudgetRub = 2_000,
    allergens = setOf(Allergen.NUTS),
    stopFoods = listOf("Сельдерей"),
    cookDays = setOf(DayOfWeek.MONDAY),
    cuisines = setOf(Cuisine.ITALIAN, Cuisine.JAPANESE),
    deliveryWanted = true,
)

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun RationSummaryLightPreview() = ShbdTheme(darkTheme = false, useDynamicColor = false) {
    Surface(color = MaterialTheme.colorScheme.background) {
        RationSummaryScreen(rationPreviewUi(RationSurveyStep.SUMMARY, previewAnswers), onEditAnswers = {})
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun RationSummaryDarkPreview() = ShbdTheme(darkTheme = true, useDynamicColor = false) {
    Surface(color = MaterialTheme.colorScheme.background) {
        RationSummaryScreen(rationPreviewUi(RationSurveyStep.SUMMARY, previewAnswers), onEditAnswers = {})
    }
}
