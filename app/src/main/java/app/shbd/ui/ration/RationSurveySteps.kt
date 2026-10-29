package app.shbd.ui.ration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shbd.R
import app.shbd.models.Allergen
import app.shbd.models.CookingTime
import app.shbd.models.Cuisine
import app.shbd.models.Dislike
import app.shbd.models.KitchenTool
import app.shbd.models.LocaleFormat
import app.shbd.models.RationMeal
import app.shbd.models.RationSurveyAnswers
import app.shbd.models.RationSurveyState
import app.shbd.models.RationSurveyStep
import app.shbd.ui.navigation.BottomNavScrollPadding
import app.shbd.ui.onboarding.ChoiceRow
import app.shbd.ui.onboarding.StepHeader
import app.shbd.ui.onboarding.ToggleCard
import app.shbd.ui.theme.AppColors
import app.shbd.ui.theme.AppTextOpacity
import app.shbd.ui.theme.ShbdTheme
import java.time.DayOfWeek

/** "2 000 ₽" in the display locale's grouping. */
@Composable
internal fun rubText(rub: Int): String = stringResource(R.string.ration_survey_rub, LocaleFormat.integer(rub))

@Composable
internal fun RationIntro(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, bottom = BottomNavScrollPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.RestaurantMenu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.ration_survey_intro_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ration_survey_intro_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.ration_survey_intro_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Common frame of a question step: "Step N of M", progress, title, body, Back / Next (Done). */
@Composable
internal fun RationSurveyStepFrame(
    ui: RationSurveyUiState,
    onUpdate: ((RationSurveyAnswers) -> RationSurveyAnswers) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val position = ui.position ?: return
    val titles = RationSurveyLabels.stepTitles(ui.step) ?: return
    Column(Modifier.fillMaxSize().padding(bottom = BottomNavScrollPadding)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
            Text(
                stringResource(R.string.ration_survey_step_of, position.number, position.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { position.number.toFloat() / position.total },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            StepHeader(stringResource(titles.first), stringResource(titles.second))
            RationStepBody(ui.step, ui.answers, onUpdate)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (position.number > 1) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(R.string.ration_survey_back))
                }
            }
            Button(onClick = onNext, enabled = ui.canProceed, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(if (ui.isLastStep) R.string.ration_survey_done else R.string.ration_survey_next))
            }
        }
    }
}

@Composable
private fun RationStepBody(
    step: RationSurveyStep,
    a: RationSurveyAnswers,
    onUpdate: ((RationSurveyAnswers) -> RationSurveyAnswers) -> Unit,
) {
    when (step) {
        RationSurveyStep.MEALS -> MealsStep(a, onUpdate)
        RationSurveyStep.BUDGET -> BudgetStep(a, onUpdate)
        RationSurveyStep.RESTRICTIONS -> RestrictionsStep(a, onUpdate)
        RationSurveyStep.COOKING -> CookingStep(a, onUpdate)
        RationSurveyStep.KITCHEN -> KitchenStep(a, onUpdate)
        RationSurveyStep.CUISINES -> CuisinesStep(a, onUpdate)
        RationSurveyStep.DELIVERY -> DeliveryStep(a, onUpdate)
        RationSurveyStep.INTRO, RationSurveyStep.SUMMARY -> Unit
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Note(text: String, color: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)) {
    Spacer(Modifier.height(12.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

/** Multi-select chips over an enum list. */
@Composable
private fun <T> ChipGroup(options: List<T>, selected: Set<T>, label: (T) -> Int, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                label = { Text(stringResource(label(option))) },
            )
        }
    }
}

@Composable
private fun MealsStep(a: RationSurveyAnswers, onUpdate: ((RationSurveyAnswers) -> RationSurveyAnswers) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RationMeal.entries.forEach { meal ->
            ChoiceRow(
                label = stringResource(RationSurveyLabels.meal(meal)),
                selected = meal in a.meals,
                onClick = { onUpdate { it.copy(meals = it.meals.toggle(meal)) } },
            )
        }
    }
    if (a.meals.isEmpty()) Note(stringResource(R.string.ration_survey_meals_hint), MaterialTheme.colorScheme.error)
}

@Composable
private fun BudgetStep(a: RationSurveyAnswers, onUpdate: ((RationSurveyAnswers) -> RationSurveyAnswers) -> Unit) {
    val level = RationSurveyAnswers.budgetLevel(a.weeklyBudgetRub)
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
    Text(
        if (a.budgetUnlimited) stringResource(R.string.ration_survey_budget_unlimited) else rubText(a.weeklyBudgetRub),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = if (a.budgetUnlimited) muted else MaterialTheme.colorScheme.onBackground,
    )
    if (!a.budgetUnlimited) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(RationSurveyLabels.budgetLevel(level)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Calorie,
        )
        Text(stringResource(RationSurveyLabels.budgetLevelDesc(level)), style = MaterialTheme.typography.bodyMedium, color = muted)
    }
    Spacer(Modifier.height(16.dp))
    val span = RationSurveyAnswers.MAX_BUDGET_RUB - RationSurveyAnswers.MIN_BUDGET_RUB
    Slider(
        value = a.weeklyBudgetRub.toFloat(),
        onValueChange = { v -> onUpdate { it.copy(weeklyBudgetRub = RationSurveyAnswers.normalizeBudget(v.toInt())) } },
        valueRange = RationSurveyAnswers.MIN_BUDGET_RUB.toFloat()..RationSurveyAnswers.MAX_BUDGET_RUB.toFloat(),
        steps = span / RationSurveyAnswers.BUDGET_STEP_RUB - 1,
        enabled = !a.budgetUnlimited,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rubText(RationSurveyAnswers.MIN_BUDGET_RUB), style = MaterialTheme.typography.bodySmall, color = muted)
        Text(rubText(RationSurveyAnswers.MAX_BUDGET_RUB), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    Spacer(Modifier.height(16.dp))
    ToggleCard(
        label = stringResource(R.string.ration_survey_budget_unlimited),
        subtitle = stringResource(R.string.ration_survey_budget_unlimited_subtitle),
        enabled = a.budgetUnlimited,
        onToggle = { on -> onUpdate { it.copy(budgetUnlimited = on) } },
    )
}

@Composable
private fun RestrictionsStep(a: RationSurveyAnswers, onUpdate: ((RationSurveyAnswers) -> RationSurveyAnswers) -> Unit) {
    SectionTitle(stringResource(R.string.ration_survey_section_allergies))
    ChipGroup(Allergen.entries, a.allergens, RationSurveyLabels::allergen) { x ->
        onUpdate { it.copy(allergens = it.allergens.toggle(x)) }
    }
    Spacer(Modifier.height(20.dp))
    SectionTitle(stringResource(R.string.ration_survey_section_dislikes))
    ChipGroup(Dislike.entries, a.dislikes, RationSurveyLabels::dislike) { x ->
        onUpdate { it.copy(dislikes = it.dislikes.toggle(x)) }
    }
    Spacer(Modifier.height(20.dp))
    SectionTitle(stringResource(R.string.ration_survey_section_stop_list))
    // The draft stays in the UI; only an added product reaches the store.
    var draft by rememberSaveable { mutableStateOf("") }
    val add = {
        onUpdate { it.copy(stopFoods = RationSurveyAnswers.addStopFood(it.stopFoods, draft)) }
        draft = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(RationSurveyAnswers.STOP_FOOD_MAX_LENGTH) },
            placeholder = { Text(stringResource(R.string.ration_survey_stop_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = add, enabled = draft.isNotBlank()) {
            Text(stringResource(R.string.ration_survey_stop_add))
        }
    }
    if (a.stopFoods.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            a.stopFoods.forEach { food ->
                InputChip(
                    selected = false,
                    onClick = { onUpdate { it.copy(stopFoods = it.stopFoods - food) } },
                    label = { Text(food) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.ration_survey_stop_remove, food),
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CookingStep(a: RationSurveyAnswers, onUpdate: ((RationSurveyAnswers) -> RationSurveyAnswers) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CookingTime.entries.forEach { c ->
            ChoiceRow(
                label = stringResource(RationSurveyLabels.cooking(c)),
                subtitle = stringResource(RationSurveyLabels.cookingDesc(c)),
                selected = a.cookingTime == c,
                onClick = { onUpdate { it.copy(cookingTime = c) } },
            )
        }
    }
    // Hidden, not reset: repeats and days come back when the user picks cooking again.
    if (a.cooks) {
        Spacer(Modifier.height(24.dp))
        SectionTitle(stringResource(R.string.ration_survey_repeat_title))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RationSurveyAnswers.REPEAT_RANGE.forEach { n ->
                FilterChip(
                    selected = a.repeatLimit == n,
                    onClick = { onUpdate { it.copy(repeatLimit = n) } },
                    label = { Text(pluralStringResource(R.plurals.ration_survey_repeat_times, n, n)) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.ration_survey_cook_days_title))
        Text(
            stringResource(R.string.ration_survey_cook_days_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
        )
        Spacer(Modifier.height(8.dp))
        ChipGroup(DayOfWeek.entries, a.cookDays, RationSurveyLabels::day) { d ->
            onUpdate { it.copy(cookDays = it.cookDays.toggle(d)) }
        }
    }
}

@Composable
private fun KitchenStep(a: RationSurveyAnswers, onUpdate: ((RationSurveyAnswers) -> RationSurveyAnswers) -> Unit) {
    ChipGroup(KitchenTool.entries, a.kitchen, RationSurveyLabels::tool) { t ->
        onUpdate { it.copy(kitchen = it.kitchen.toggle(t)) }
    }
    if (a.kitchen.isEmpty()) Note(stringResource(R.string.ration_survey_kitchen_empty))
}

@Composable
private fun CuisinesStep(a: RationSurveyAnswers, onUpdate: ((RationSurveyAnswers) -> RationSurveyAnswers) -> Unit) {
    ChipGroup(Cuisine.entries, a.cuisines, RationSurveyLabels::cuisine) { c ->
        onUpdate { it.copy(cuisines = RationSurveyAnswers.toggleCuisine(it.cuisines, c)) }
    }
    Note(stringResource(R.string.ration_survey_cuisines_note))
}

@Composable
private fun DeliveryStep(a: RationSurveyAnswers, onUpdate: ((RationSurveyAnswers) -> RationSurveyAnswers) -> Unit) {
    ComingLaterBadge()
    Spacer(Modifier.height(12.dp))
    ToggleCard(
        label = stringResource(R.string.ration_survey_delivery_toggle),
        subtitle = stringResource(R.string.ration_survey_delivery_note),
        enabled = a.deliveryWanted,
        onToggle = { on -> onUpdate { it.copy(deliveryWanted = on) } },
    )
}

@Composable
private fun ComingLaterBadge() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = AppColors.Calorie.copy(alpha = 0.14f),
    ) {
        Text(
            stringResource(R.string.ration_survey_coming_later),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = AppColors.Calorie,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// -- Previews -------------------------------------------------------------

internal fun rationPreviewUi(step: RationSurveyStep, answers: RationSurveyAnswers = RationSurveyAnswers()) =
    RationSurveyState(answers = answers, step = step, completed = step == RationSurveyStep.SUMMARY)
        .toUiState(kcal = 1_850)

@Composable
private fun StepPreview(step: RationSurveyStep, dark: Boolean, answers: RationSurveyAnswers = RationSurveyAnswers()) {
    ShbdTheme(darkTheme = dark, useDynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            RationSurveyStepFrame(rationPreviewUi(step, answers), onUpdate = {}, onNext = {}, onBack = {})
        }
    }
}

@Preview(showBackground = true) @Composable private fun IntroLightPreview() =
    ShbdTheme(darkTheme = false, useDynamicColor = false) { Surface { RationIntro(onStart = {}) } }
@Preview(showBackground = true) @Composable private fun IntroDarkPreview() =
    ShbdTheme(darkTheme = true, useDynamicColor = false) { Surface { RationIntro(onStart = {}) } }
@Preview(showBackground = true) @Composable private fun MealsLightPreview() = StepPreview(RationSurveyStep.MEALS, false)
@Preview(showBackground = true) @Composable private fun MealsDarkPreview() =
    StepPreview(RationSurveyStep.MEALS, true, RationSurveyAnswers(meals = emptySet()))
@Preview(showBackground = true) @Composable private fun BudgetLightPreview() = StepPreview(RationSurveyStep.BUDGET, false)
@Preview(showBackground = true) @Composable private fun BudgetDarkPreview() =
    StepPreview(RationSurveyStep.BUDGET, true, RationSurveyAnswers(budgetUnlimited = true))
@Preview(showBackground = true) @Composable private fun RestrictionsLightPreview() = StepPreview(
    RationSurveyStep.RESTRICTIONS, false,
    RationSurveyAnswers(allergens = setOf(Allergen.NUTS), stopFoods = listOf("Сельдерей")),
)
@Preview(showBackground = true) @Composable private fun RestrictionsDarkPreview() =
    StepPreview(RationSurveyStep.RESTRICTIONS, true)
@Preview(showBackground = true) @Composable private fun CookingLightPreview() = StepPreview(RationSurveyStep.COOKING, false)
@Preview(showBackground = true) @Composable private fun CookingDarkPreview() =
    StepPreview(RationSurveyStep.COOKING, true, RationSurveyAnswers(cookingTime = CookingTime.NONE))
@Preview(showBackground = true) @Composable private fun KitchenLightPreview() = StepPreview(RationSurveyStep.KITCHEN, false)
@Preview(showBackground = true) @Composable private fun KitchenDarkPreview() =
    StepPreview(RationSurveyStep.KITCHEN, true, RationSurveyAnswers(kitchen = emptySet()))
@Preview(showBackground = true) @Composable private fun CuisinesLightPreview() = StepPreview(RationSurveyStep.CUISINES, false)
@Preview(showBackground = true) @Composable private fun CuisinesDarkPreview() =
    StepPreview(RationSurveyStep.CUISINES, true, RationSurveyAnswers(cuisines = setOf(Cuisine.ITALIAN, Cuisine.JAPANESE)))
@Preview(showBackground = true) @Composable private fun DeliveryLightPreview() = StepPreview(RationSurveyStep.DELIVERY, false)
@Preview(showBackground = true) @Composable private fun DeliveryDarkPreview() =
    StepPreview(RationSurveyStep.DELIVERY, true, RationSurveyAnswers(deliveryWanted = true))
