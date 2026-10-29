package app.shbd.services.ai

import app.shbd.models.FoodEntry
import app.shbd.models.FoodSource
import app.shbd.models.Recipe
import app.shbd.models.WeightEntry
import app.shbd.ui.onboarding.OnboardingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** The embedded coach has no data tools: the diary goes into the prompt, and tool names stay out. */
class OnDeviceCoachDataTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 29)

    private fun food(name: String, kcal: Int, day: LocalDate, hour: Int = 8) = FoodEntry(
        name = name,
        calories = kcal,
        protein = 10.0,
        carbs = 50.0,
        fat = 5.0,
        timestamp = day.atTime(hour, 0).toInstant(zone),
        source = FoodSource.BARCODE,
        mealType = "breakfast",
    )

    @Test
    fun snapshot_listsTodayYesterdayTotalsWeightsAndDishes() {
        val text = onDeviceDataSnapshot(
            foods = listOf(
                food("Овсянка", 350, today),
                food("Суп", 420, today.minusDays(1), hour = 13),
                food("Плов", 600, today.minusDays(3), hour = 19),
            ),
            weights = listOf(WeightEntry(date = today.atStartOfDay().toInstant(zone), weightKg = 70.4)),
            recipes = listOf(Recipe(name = "Сырники", nutritionCalories = 520, nutritionProtein = 30.0, nutritionCarbs = 40.0, nutritionFat = 20.0)),
            weightMetric = true,
            today = today,
            zone = zone,
        )
        assertTrue(text.contains("Food today (2026-10-29): 350 kcal"))
        assertTrue(text.contains("Овсянка — 350 kcal"))
        assertTrue(text.contains("Суп — 420 kcal"))
        assertTrue(text.contains("- 2026-10-26: 600 kcal"))
        assertTrue(text.contains("70.4 kg"))
        assertTrue(text.contains("Сырники: 520 kcal"))
    }

    @Test
    fun snapshot_emptyDayIsSaid() {
        val text = onDeviceDataSnapshot(emptyList(), emptyList(), emptyList(), weightMetric = true, today = today, zone = zone)
        assertTrue(text.contains("Food today (2026-10-29)\n- nothing logged"))
    }

    @Test
    fun snapshot_isBounded() {
        val foods = (0 until 14).flatMap { back ->
            (0 until 25).map { food("Блюдо с длинным названием номер $it", 100, today.minusDays(back.toLong()), hour = it % 24) }
        }
        val text = onDeviceDataSnapshot(foods, emptyList(), emptyList(), weightMetric = true, today = today, zone = zone)
        assertTrue(text.length <= ON_DEVICE_DATA_MAX_CHARS)
        assertTrue(text.startsWith("## User data\n### Food today"))
    }

    @Test
    fun onDevicePrompt_hasNoToolNamesAndCarriesTheData() {
        val profile = OnboardingState().buildProfile()
        val foods = listOf(food("Овсянка", 350, LocalDate.now()))
        val prompt = buildSystemPrompt(
            profile = profile,
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = foods,
            heightMetric = true,
            weightMetric = true,
            toolsAvailable = false,
            inlineData = onDeviceDataSnapshot(foods, emptyList(), emptyList(), weightMetric = true),
        )
        listOf("get_data_summary", "get_food_entries", "get_calorie_totals", "get_weight_history", "propose_log", "tool date ranges").forEach {
            assertFalse("prompt mentions $it", prompt.contains(it))
        }
        assertTrue(prompt.contains("Never ask or advise the user to call functions or tools"))
        assertTrue(prompt.contains("Овсянка — 350 kcal"))
    }

    @Test
    fun cloudPrompt_keepsTheTools() {
        val prompt = buildSystemPrompt(
            profile = OnboardingState().buildProfile(),
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = emptyList(),
            heightMetric = true,
            weightMetric = true,
        )
        assertTrue(prompt.contains("get_data_summary"))
        assertTrue(prompt.contains("propose_log_"))
        assertFalse(prompt.contains("## User data"))
    }
}
