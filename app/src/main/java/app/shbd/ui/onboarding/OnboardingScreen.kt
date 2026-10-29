package app.shbd.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shbd.AppContainer
import app.shbd.R
import app.shbd.models.CalorieSafety
import app.shbd.models.WeightGoal
import java.time.LocalDate
import java.time.Period
import app.shbd.ui.theme.AppRadii
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(container: AppContainer, onComplete: () -> Unit) {
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    var showMinorDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .statusBarsPadding()
            // Keep the footer CTA above the gesture / 3-button navigation bar.
            .navigationBarsPadding()
            // Shrink for the keyboard so number fields and the footer CTA stay reachable.
            .imePadding()
    ) {
        // iOS shows a chevron-left back button + a thin Capsule progress bar at
        // the top, only on steps 1..N-2 (hidden on Welcome and Review).
        if (ui.step != OnboardingStep.WELCOME && ui.step != OnboardingStep.BUILDING_PLAN) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (ui.step != OnboardingStep.DISCLAIMERS) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.onboarding_back),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { vm.back() }
                    )
                }
                val totalSteps = OnboardingStep.entries.size
                val progress = ui.step.ordinal.toFloat() / (totalSteps - 1).toFloat()
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(24.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
            when (ui.step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.GENDER -> GenderStep(selected = ui.gender, onSelect = vm::setGender)
                OnboardingStep.BIRTHDAY -> BirthdayStep(current = ui.birthday, onChange = vm::setBirthday)
                OnboardingStep.HEIGHT -> HeightStep(cm = ui.heightCm, onHeightChange = vm::setHeight)
                OnboardingStep.WEIGHT -> WeightStep(kg = ui.weightKg, onWeightChange = vm::setWeight)
                OnboardingStep.BODY_FAT -> BodyFatStep(
                    bodyFat = ui.bodyFatPercentage,
                    goalBodyFat = ui.goalBodyFatPercentage,
                    onChange = vm::setBodyFat,
                    onGoalChange = vm::setGoalBodyFat
                )
                OnboardingStep.ACTIVITY -> ActivityStep(
                    selected = ui.activity,
                    onSelect = vm::setActivity
                )
                OnboardingStep.GOAL -> {
                    // WHO BMI bands suggest the starting goal; the badge marks it,
                    // selection default is LOSE (state default), the badge never changes it.
                    val bmi = if (ui.heightCm > 0 && ui.weightKg > 0.0) {
                        ui.weightKg / Math.pow(ui.heightCm / 100.0, 2.0)
                    } else {
                        null
                    }
                    val suggested = bmi?.let {
                        when {
                            it < 18.5 -> WeightGoal.GAIN
                            it < 25.0 -> WeightGoal.MAINTAIN
                            else -> WeightGoal.LOSE
                        }
                    }
                    GoalStep(
                        selected = ui.goal,
                        onSelect = vm::setGoal,
                        suggested = suggested,
                        bmi = bmi,
                    )
                }
                OnboardingStep.GOAL_WEIGHT -> GoalWeightStep(
                    current = ui.goalWeightKg,
                    goal = ui.goal,
                    onChange = vm::setGoalWeight
                )
                OnboardingStep.GOAL_SPEED -> {
                    GoalSpeedStep(
                        weeklyKg = ui.weeklyChangeKg,
                        goal = ui.goal,
                        useMetric = ui.weightMetric,
                        currentKg = ui.weightKg,
                        targetKg = ui.goalWeightKg,
                        onSelect = vm::setWeeklyChange,
                    )
                }
                OnboardingStep.HEALTH_CONNECT -> HealthConnectStep(
                    container = container,
                    enabled = ui.healthConnectEnabled,
                    onToggle = vm::setHealthConnectEnabled
                )
                OnboardingStep.BUILDING_PLAN -> BuildingPlanStep(vm = vm, onComplete = vm::next)
                OnboardingStep.PLAN_READY -> PlanReadyStep(state = ui, vm = vm)
                OnboardingStep.DISCLAIMERS -> DisclaimersStep()
            }
        }

        when (ui.step) {
            OnboardingStep.WELCOME -> {
                // iOS Welcome: full-width pink-gradient "Get Started" capsule.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(AppRadii.Field))
                            .background(
                                MaterialTheme.colorScheme.primary
                            )
                            .clickable { vm.next() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.action_get_started),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            OnboardingStep.BUILDING_PLAN -> {
                // Auto-advancing animation; no CTA. Reserve the same footer
                // height so layout doesn't jump when we land on this step.
                Spacer(Modifier.height(52.dp + 20.dp))
            }
            OnboardingStep.DISCLAIMERS -> {
                // Final step — completes onboarding after the user has room to
                // read safety + accuracy notices (kept off the crowded plan screen).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 20.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(AppRadii.Sheet))
                        .background(
                            MaterialTheme.colorScheme.primary
                        )
                        .clickable { vm.complete(onComplete) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.action_get_started),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            else -> {
                // iOS continueButton: full-width inverse-coloured capsule.
                Button(
                    onClick = {
                        app.shbd.ui.components.MagnitudeDrafts.commitAll()
                        val age = Period.between(ui.birthday, LocalDate.now()).years
                        if (ui.step == OnboardingStep.BIRTHDAY && age < CalorieSafety.ADULT_MIN_AGE) {
                            showMinorDialog = true
                        } else vm.next()
                    },
                    enabled = ui.canAdvance,
                    shape = RoundedCornerShape(AppRadii.Sheet),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 20.dp)
                        .height(52.dp)
                ) {
                    Text(
                        stringResource(R.string.action_continue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showMinorDialog) {
        AlertDialog(
            onDismissRequest = { showMinorDialog = false },
            title = { Text(stringResource(R.string.onboarding_minor_title)) },
            text = { Text(stringResource(R.string.onboarding_minor_message)) },
            confirmButton = {
                TextButton(onClick = { showMinorDialog = false; vm.next() }) {
                    Text(stringResource(R.string.onboarding_minor_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMinorDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
