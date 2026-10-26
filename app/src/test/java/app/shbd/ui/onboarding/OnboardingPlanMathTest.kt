package app.shbd.ui.onboarding

import app.shbd.models.ActivityLevel
import app.shbd.models.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingPlanMathTest {

    @Test
    fun planRange_loseAndMaintainGoFromZeroToNorm() {
        assertEquals(0..2500, planCalorieRange(WeightGoal.LOSE, 2500))
        assertEquals(0..2500, planCalorieRange(WeightGoal.MAINTAIN, 2500))
    }

    @Test
    fun planRange_gainStartsAtNorm() {
        assertEquals(2500..(2500 + PLAN_MAX_SURPLUS_KCAL), planCalorieRange(WeightGoal.GAIN, 2500))
    }

    @Test
    fun daysToGoal_followsTheDailyDeficit() {
        // 5 kg × 7 700 kcal ÷ 863 kcal/day = 44.6 → 45 days.
        assertEquals(45, planDaysToGoal(70.4, 65.4, WeightGoal.LOSE, normKcal = 2500, planKcal = 1637))
        // A smaller deficit takes longer.
        assertEquals(77, planDaysToGoal(70.4, 65.4, WeightGoal.LOSE, normKcal = 2500, planKcal = 2000))
    }

    @Test
    fun daysToGoal_nullWhenPlanDoesNotMoveTowardGoal() {
        assertNull(planDaysToGoal(70.4, 65.4, WeightGoal.LOSE, normKcal = 2500, planKcal = 2500))
        assertNull(planDaysToGoal(70.4, 75.4, WeightGoal.GAIN, normKcal = 2500, planKcal = 2500))
        assertNull(planDaysToGoal(70.4, 70.4, WeightGoal.MAINTAIN, normKcal = 2500, planKcal = 2000))
    }

    @Test
    fun daysToGoal_zeroWhenAlreadyThere() {
        assertEquals(0, planDaysToGoal(65.4, 65.4, WeightGoal.LOSE, normKcal = 2500, planKcal = 1800))
    }

    @Test
    fun defaultGoalWeight_alwaysEndsInFourTenths() {
        assertEquals(65.4, defaultGoalWeightKg(70.8, WeightGoal.LOSE), 1e-9)
        assertEquals(75.4, defaultGoalWeightKg(70.0, WeightGoal.GAIN), 1e-9)
        assertEquals(70.8, defaultGoalWeightKg(70.8, WeightGoal.MAINTAIN), 1e-9)
        assertEquals(70.4, DEFAULT_ONBOARDING_WEIGHT_KG, 1e-9)
    }

    @Test
    fun onboardingDefaults_sedentaryLoseWeight() {
        val state = OnboardingState()
        assertEquals(ActivityLevel.SEDENTARY, state.activity)
        assertEquals(WeightGoal.LOSE, state.goal)
        assertEquals(defaultGoalWeightKg(state.weightKg, WeightGoal.LOSE), state.goalWeightKg, 1e-9)
        assertEquals(65.4, DEFAULT_ONBOARDING_GOAL_WEIGHT_KG, 1e-9)
    }
}
