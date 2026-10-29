package app.shbd.ui.onboarding

import app.shbd.models.ActivityLevel
import app.shbd.models.UserProfile
import app.shbd.models.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** "Your plan" from the diary: norm at the latest weight, pace plan, macros re-split. */
class CaloriePlanMathTest {

    private val saved = UserProfile(
        heightCm = 175.0,
        weightKg = 80.0,
        activityLevel = ActivityLevel.MODERATE,
        goal = WeightGoal.LOSE,
        weeklyChangeKg = 0.5,
        goalWeightKg = 70.0,
        customCalories = 1500,
        customProtein = 200,
        customCarbs = 100,
        customFat = 40,
    )

    @Test
    fun base_usesLatestWeightAndDropsOwnTargets() {
        val base = planBaseProfile(saved, latestWeightKg = 75.0)
        assertEquals(75.0, base.weightKg, 0.0)
        assertNull(base.customCalories)
        assertNull(base.customProtein)
        assertNull(base.customCarbs)
        assertNull(base.customFat)
        // The norm follows the lighter weight.
        assertTrue(base.tdee < saved.copy(customCalories = null).tdee)
    }

    @Test
    fun base_withoutWeighIns_keepsProfileWeight() {
        assertEquals(80.0, planBaseProfile(saved, latestWeightKg = null).weightKg, 0.0)
    }

    @Test
    fun defaultPlan_isNormMinusPace() {
        val base = planBaseProfile(saved, latestWeightKg = 75.0)
        assertEquals(base.tdee.roundToInt() - 550, defaultPlanKcal(base))
    }

    @Test
    fun planProfile_carriesPickedCaloriesAndResplitsMacros() {
        val base = planBaseProfile(saved, latestWeightKg = 75.0)
        val low = planProfileFor(base, 1600)
        val high = planProfileFor(base, 2000)
        assertEquals(1600, low.effectiveCalories)
        assertEquals(2000, high.effectiveCalories)
        assertTrue(low.effectiveCarbs < high.effectiveCarbs)
    }

    @Test
    fun base_keepsOrFillsTheDesiredWeight() {
        assertEquals(70.0, planBaseProfile(saved, latestWeightKg = 75.0).goalWeightKg!!, 0.0)
        // Lose without a saved goal weight: the onboarding default below the current weight.
        val noGoal = planBaseProfile(saved.copy(goalWeightKg = null), latestWeightKg = 75.0)
        assertEquals(defaultGoalWeightKg(75.0, WeightGoal.LOSE), noGoal.goalWeightKg!!, 0.0)
        // Maintain keeps no desired weight.
        assertNull(planBaseProfile(saved.copy(goal = WeightGoal.MAINTAIN, goalWeightKg = null), 75.0).goalWeightKg)
    }

    @Test
    fun back_skipsTheBuildingStep() {
        assertEquals(OnboardingStep.HEALTH_CONNECT, previousOnboardingStep(OnboardingStep.PLAN_READY))
        assertEquals(OnboardingStep.GOAL_SPEED, previousOnboardingStep(OnboardingStep.HEALTH_CONNECT))
        assertNull(previousOnboardingStep(OnboardingStep.WELCOME))
    }
}
