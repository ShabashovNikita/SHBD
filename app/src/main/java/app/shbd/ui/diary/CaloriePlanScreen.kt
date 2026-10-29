package app.shbd.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.shbd.AppContainer
import app.shbd.R
import app.shbd.models.UserProfile
import app.shbd.models.WeightGoal
import app.shbd.ui.onboarding.OnboardingWeightWheel
import app.shbd.ui.onboarding.PlanCaloriesContent
import app.shbd.ui.onboarding.defaultPlanKcal
import app.shbd.ui.onboarding.planBaseProfile
import app.shbd.ui.onboarding.planProfileFor
import app.shbd.ui.settings.SettingsTopBar
import app.shbd.ui.theme.AppRadii
import app.shbd.ui.theme.AppTextOpacity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Your plan" opened from the Nutrition card's "Change calorie goal": the
 * onboarding plan screen with the norm at the latest logged weight. Save writes
 * that weight, the picked calories (macros re-split) and the desired weight;
 * Back changes nothing.
 */
@Composable
fun CaloriePlanScreen(container: AppContainer, onBack: () -> Unit) {
    var base by remember { mutableStateOf<UserProfile?>(null) }
    var planKcal by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val profile = container.profileRepository.current() ?: return@LaunchedEffect onBack()
        val latestKg = container.weightRepository.latest.first()?.weightKg
        val loaded = planBaseProfile(profile, latestKg)
        planKcal = defaultPlanKcal(loaded)
        base = loaded
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.onboarding_plan_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.nav_diary),
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    val loaded = base ?: return@Button
                    scope.launch {
                        container.profileRepository.save(planProfileFor(loaded, planKcal))
                        onBack()
                    }
                },
                enabled = base != null,
                shape = RoundedCornerShape(AppRadii.Sheet),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp)
                    .height(52.dp),
            ) {
                Text(
                    stringResource(R.string.action_save),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) { padding ->
        val loaded = base ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                stringResource(R.string.calorie_plan_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
            )
            Spacer(Modifier.height(16.dp))
            PlanCaloriesContent(
                profile = planProfileFor(loaded, planKcal),
                planKcal = planKcal,
                onPlanChange = { planKcal = it },
            )
            val goalWeightKg = loaded.goalWeightKg
            if (loaded.goal != WeightGoal.MAINTAIN && goalWeightKg != null) {
                Spacer(Modifier.height(16.dp))
                GoalWeightCard(
                    currentKg = loaded.weightKg,
                    goalKg = goalWeightKg,
                    onGoalChange = { kg -> base = loaded.copy(goalWeightKg = kg) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** "Desired weight": current weight and weight goal in the note, then the onboarding kg wheel. */
@Composable
private fun GoalWeightCard(currentKg: Double, goalKg: Double, onGoalChange: (Double) -> Unit) {
    Card(
        shape = RoundedCornerShape(AppRadii.Tile),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.calorie_plan_goal_weight_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    R.string.calorie_plan_goal_weight_note,
                    stringResource(R.string.kg_value_format, currentKg),
                    stringResource(R.string.kg_value_format, goalKg),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
            )
            Spacer(Modifier.height(8.dp))
            OnboardingWeightWheel(kg = goalKg, onWeightChange = onGoalChange)
        }
    }
}
