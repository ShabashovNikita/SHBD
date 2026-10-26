package app.shbd.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Slider
import app.shbd.models.LocaleFormat
import app.shbd.models.WeightGoal
import java.time.LocalDate
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.stateDescription
import app.shbd.R
import app.shbd.models.AIProvider
import app.shbd.models.CalorieSafety
import app.shbd.models.EnergyFormat
import app.shbd.ui.components.ShbdTextField
import app.shbd.ui.components.OptionPickerSheet
import app.shbd.ui.components.energyText
import app.shbd.ui.components.energyUnitLabel
import app.shbd.ui.navigation.LocalEnergyUnit
import app.shbd.ui.theme.AppColors
import app.shbd.ui.theme.warning
import app.shbd.ui.theme.AppRadii
import app.shbd.ui.theme.AppTextOpacity

/**
 * iOS BuildingPlanStepView: animated percentage counter, gradient progress bar,
 * and a five-item checklist that ticks off over ~4 seconds, then auto-advances.
 */
@Composable
internal fun BuildingPlanStep(vm: OnboardingViewModel, onComplete: () -> Unit) {
    val items = listOf(
        stringResource(R.string.onboarding_building_calories) to Icons.Outlined.LocalFireDepartment,
        stringResource(R.string.onboarding_building_carbs) to Icons.Outlined.Restaurant,
        stringResource(R.string.onboarding_building_protein) to Icons.Outlined.FitnessCenter,
        stringResource(R.string.onboarding_building_fats) to Icons.Outlined.Bolt,
        stringResource(R.string.onboarding_building_health_score) to Icons.Filled.Favorite
    )
    var checkedCount by remember { mutableIntStateOf(0) }
    var percent by remember { mutableIntStateOf(0) }
    var aiDone by remember { mutableStateOf(false) }
    val targetProgress = checkedCount / items.size.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 400),
        label = "plan_progress"
    )

    // Compute the plan with AI in parallel with the animation (seeds the Plan Ready targets;
    // leaves them null on failure so the formula values are used).
    LaunchedEffect(Unit) { vm.buildPlanWithAI { aiDone = true } }

    LaunchedEffect(Unit) {
        val percentSteps = listOf(20, 40, 60, 80, 100)
        for (i in 0 until items.size) {
            kotlinx.coroutines.delay(700)
            checkedCount = i + 1
            percent = percentSteps[i]
        }
        kotlinx.coroutines.delay(400)
        // Advance only once the AI plan calc has also finished.
        while (!aiDone) kotlinx.coroutines.delay(100)
        onComplete()
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.onboarding_building_percent_format, percent),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_building_setting_up),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        MaterialTheme.colorScheme.primary
                    )
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.onboarding_building_finalizing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
        )
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.onboarding_building_recommendation),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            items.forEachIndexed { index, (label, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.onboarding_building_bullet),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    if (index < checkedCount) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Plan step: "1 637 of 2 500 kcal", where 2 500 is the maintenance norm (TDEE —
 * the daily calories that keep the weight stable). A slider picks the plan within
 * [planCalorieRange] and the time-to-goal line follows it live. Protein, fat and
 * carbs are shown for reference only — they re-split from the calories and cannot
 * be edited here.
 */
@Composable
internal fun PlanReadyStep(state: OnboardingState, vm: OnboardingViewModel) {
    val profile = state.buildProfile()
    val unit = LocalEnergyUnit.current
    val normKcal = profile.tdee.roundToInt()
    val range = planCalorieRange(state.goal, normKcal)
    val planKcal = profile.effectiveCalories.coerceIn(range.first, range.last)
    val days = planDaysToGoal(state.weightKg, state.goalWeightKg, state.goal, normKcal, planKcal)
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            StepHeader(
                stringResource(R.string.onboarding_plan_title),
                subtitle = stringResource(R.string.onboarding_plan_subtitle)
            )
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        LocaleFormat.integer(EnergyFormat.quantity(planKcal, unit)),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            R.string.onboarding_plan_of_norm,
                            LocaleFormat.integer(EnergyFormat.quantity(normKcal, unit)),
                            energyUnitLabel()
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = muted,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                Text(
                    stringResource(R.string.onboarding_plan_norm_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            val sliderCd = stringResource(R.string.onboarding_plan_slider_cd)
            Slider(
                value = planKcal.toFloat(),
                onValueChange = { v ->
                    val snapped = ((v / PLAN_SLIDER_STEP_KCAL).roundToInt() * PLAN_SLIDER_STEP_KCAL)
                        .coerceIn(range.first, range.last)
                    if (snapped != planKcal) vm.setCustomCalories(snapped)
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = sliderCd }
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    LocaleFormat.integer(EnergyFormat.quantity(range.first, unit)),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    LocaleFormat.integer(EnergyFormat.quantity(range.last, unit)),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted
                )
            }
            Spacer(Modifier.height(16.dp))
            PlanGoalEtaCard(state = state, normKcal = normKcal, planKcal = planKcal, days = days)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MacroCard(
                    label = stringResource(R.string.macro_protein),
                    value = profile.effectiveProtein,
                    color = AppColors.Protein,
                    unitRes = R.string.unit_g,
                    modifier = Modifier.weight(1f)
                )
                MacroCard(
                    label = stringResource(R.string.macro_carbs),
                    value = profile.effectiveCarbs,
                    color = AppColors.Carbs,
                    unitRes = R.string.unit_g,
                    modifier = Modifier.weight(1f)
                )
                MacroCard(
                    label = stringResource(R.string.macro_fat),
                    value = profile.effectiveFat,
                    color = AppColors.Fat,
                    unitRes = R.string.unit_g,
                    modifier = Modifier.weight(1f)
                )
            }
            if (planKcal < CalorieSafety.floorKcal(profile.bmr)) {
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(AppRadii.Tile),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.warning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.onboarding_plan_doctor_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.onboarding_plan_doctor_message, energyText(CalorieSafety.floorKcal(profile.bmr))),
                                style = MaterialTheme.typography.bodySmall,
                                color = muted
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        // Adaptive Goals is on by default for new installs. The note sits outside the
        // scrolling content, right above the "Continue" button (below the macros).
        Text(
            stringResource(R.string.onboarding_plan_adaptive_note),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

/** "Deficit 863 kcal a day" + "Goal 65.4 kg in about 83 days (by 18 Jan 2027)". */
@Composable
private fun PlanGoalEtaCard(state: OnboardingState, normKcal: Int, planKcal: Int, days: Int?) {
    val unit = LocalEnergyUnit.current
    val delta = planKcal - normKcal
    Card(
        shape = RoundedCornerShape(AppRadii.Tile),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (delta != 0) {
                Text(
                    stringResource(
                        if (delta < 0) R.string.onboarding_plan_daily_deficit else R.string.onboarding_plan_daily_surplus,
                        LocaleFormat.integer(EnergyFormat.quantity(kotlin.math.abs(delta), unit)),
                        energyUnitLabel()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val goalWeight = stringResource(R.string.kg_value_format, state.goalWeightKg)
            Text(
                when {
                    state.goal == WeightGoal.MAINTAIN -> stringResource(R.string.onboarding_plan_eta_maintain)
                    days == null -> stringResource(R.string.onboarding_plan_eta_never, goalWeight)
                    days == 0 -> stringResource(R.string.onboarding_plan_eta_reached)
                    else -> stringResource(
                        R.string.onboarding_plan_eta,
                        goalWeight,
                        days,
                        LocalDate.now().plusDays(days.toLong()).format(LocaleFormat.mediumDate())
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Strong)
            )
        }
    }
}

/**
 * Final onboarding step: safety + accuracy notices get their own screen so they
 * are not clipped under the plan targets and footer CTA.
 */
@Composable
internal fun DisclaimersStep() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        StepHeader(
            stringResource(R.string.onboarding_disclaimers_title),
            subtitle = stringResource(R.string.onboarding_disclaimers_subtitle)
        )
        Spacer(Modifier.height(8.dp))
        Card(
            shape = RoundedCornerShape(AppRadii.Tile),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.12f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.warning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.onboarding_safety_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.onboarding_safety_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Subtle)
                    )
                    Text(
                        stringResource(R.string.onboarding_safety_low_bf),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Subtle)
                    )
                    Text(
                        stringResource(R.string.onboarding_safety_not_medical),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Subtle)
                    )
                    Text(
                        stringResource(R.string.onboarding_safety_water),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Subtle)
                    )
                    Text(
                        stringResource(R.string.onboarding_safety_settings_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Subtle)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(AppRadii.Tile),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.onboarding_privacy_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.onboarding_privacy_cloud_title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Subtle)
                    )
                    Text(
                        stringResource(R.string.onboarding_privacy_cloud_lead),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Subtle)
                    )
                    listOf(
                        R.string.onboarding_privacy_cloud_food,
                        R.string.onboarding_privacy_cloud_whatif,
                        R.string.onboarding_privacy_cloud_coach,
                        R.string.onboarding_privacy_cloud_goals
                    ).forEach { res ->
                        Text(
                            stringResource(res),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Subtle)
                        )
                    }
                    Text(
                        stringResource(R.string.onboarding_privacy_ondevice_title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Subtle)
                    )
                    Text(
                        stringResource(R.string.onboarding_privacy_ondevice_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Subtle)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(AppRadii.Tile),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.onboarding_accuracy_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.onboarding_accuracy_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Subtle)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MacroCard(
    label: String,
    value: Int,
    color: Color,
    unitRes: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(AppRadii.Field))
            .background(color.copy(alpha = 0.10f))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = AppTextOpacity.Strong),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$value",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    stringResource(unitRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
