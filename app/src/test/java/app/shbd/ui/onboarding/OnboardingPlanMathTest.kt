package app.shbd.ui.onboarding

import app.shbd.models.ActivityLevel
import app.shbd.models.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

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
    fun planKcal_followsThePace_notTheBmrFloor() {
        listOf(0.25 to 275, 0.5 to 550, 1.0 to 1100).forEach { (pace, deficit) ->
            val state = OnboardingState(weeklyChangeKg = pace)
            val norm = state.buildProfile().tdee.roundToInt()
            assertEquals(norm - deficit, state.planKcal)
        }
    }

    @Test
    fun planEta_matchesTheGoalSpeedStep() {
        // Goal Speed shows gap ÷ pace × 7 days; Plan Ready must agree at the pace's deficit.
        val state = OnboardingState(weeklyChangeKg = 1.0)
        val norm = state.buildProfile().tdee.roundToInt()
        val days = planDaysToGoal(state.weightKg, state.goalWeightKg, state.goal, norm, state.planKcal)
        assertEquals(35, days)
    }

    @Test
    fun planProfile_carriesThePlanCalories() {
        val state = OnboardingState(weeklyChangeKg = 1.0)
        assertEquals(state.planKcal, state.buildPlanProfile().effectiveCalories)
        val picked = state.copy(customCalories = 1500)
        assertEquals(1500, picked.planKcal)
        assertEquals(1500, picked.buildPlanProfile().effectiveCalories)
    }

    @Test
    fun doctorWarning_onlyAboveOneKiloAWeek() {
        assertEquals(1100, PLAN_WARNING_DEFICIT_KCAL)
        assertFalse(planNeedsDoctorWarning(WeightGoal.LOSE, normKcal = 2500, planKcal = 1400))
        assertTrue(planNeedsDoctorWarning(WeightGoal.LOSE, normKcal = 2500, planKcal = 1300))
        assertFalse(planNeedsDoctorWarning(WeightGoal.MAINTAIN, normKcal = 2500, planKcal = 1000))
    }

    @Test
    fun minRecommended_isTheOneKiloPlan() {
        assertEquals(1400, planMinRecommendedKcal(2500))
        assertEquals(900, planMinRecommendedKcal(2000))
        assertEquals(0, planMinRecommendedKcal(1000))
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
