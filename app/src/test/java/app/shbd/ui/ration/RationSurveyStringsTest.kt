package app.shbd.ui.ration

import android.app.Application
import app.shbd.R
import app.shbd.models.Allergen
import app.shbd.models.BudgetLevel
import app.shbd.models.CookingTime
import app.shbd.models.Cuisine
import app.shbd.models.Dislike
import app.shbd.models.KitchenTool
import app.shbd.models.RationMeal
import app.shbd.models.RationSurveyStep
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Every survey option has a label in en and ru; formatted strings take their call-site args. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RationSurveyStringsTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun allLabels(): List<String> {
        val ids = RationMeal.entries.map(RationSurveyLabels::meal) +
            Allergen.entries.map(RationSurveyLabels::allergen) +
            Dislike.entries.map(RationSurveyLabels::dislike) +
            CookingTime.entries.flatMap { listOf(RationSurveyLabels.cooking(it), RationSurveyLabels.cookingDesc(it)) } +
            KitchenTool.entries.map(RationSurveyLabels::tool) +
            Cuisine.entries.map(RationSurveyLabels::cuisine) +
            DayOfWeek.entries.map(RationSurveyLabels::day) +
            BudgetLevel.entries.flatMap {
                listOf(RationSurveyLabels.budgetLevel(it), RationSurveyLabels.budgetLevelDesc(it))
            } +
            RationSurveyStep.entries.mapNotNull(RationSurveyLabels::stepTitles).flatMap { listOf(it.first, it.second) }
        return ids.map { context.getString(it) }
    }

    @Test
    fun english_everyOptionLabelled() {
        val labels = allLabels()
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals("Meals", context.getString(R.string.ration_survey_meals_title))
        assertEquals("Budget", context.getString(R.string.ration_survey_budget_title))
        assertEquals("Breakfast", context.getString(RationSurveyLabels.meal(RationMeal.BREAKFAST)))
        assertEquals("Step 5 of 6", context.getString(R.string.ration_survey_step_of, 5, 6))
        assertEquals("2,000 ₽", context.getString(R.string.ration_survey_rub, "2,000"))
        assertEquals("2 times", context.resources.getQuantityString(R.plurals.ration_survey_repeat_times, 2, 2))
    }

    @Test
    @Config(qualifiers = "ru")
    fun russian_everyOptionLabelled() {
        val labels = allLabels()
        assertTrue(labels.all { it.isNotBlank() })
        // Each option is translated: no Latin-only label slipped through.
        assertTrue(labels.filterNot { Regex("[А-Яа-яЁё]").containsMatchIn(it) }.isEmpty())
        assertEquals("Шаг 5 из 6", context.getString(R.string.ration_survey_step_of, 5, 6))
        assertEquals(
            "2 000 ₽ · Экономно",
            context.getString(
                R.string.ration_survey_summary_budget_value,
                context.getString(R.string.ration_survey_rub, "2 000"),
                context.getString(R.string.ration_survey_budget_economy),
            ),
        )
        assertEquals("2 раза", context.resources.getQuantityString(R.plurals.ration_survey_repeat_times, 2, 2))
        assertEquals("1 раз", context.resources.getQuantityString(R.plurals.ration_survey_repeat_times, 1, 1))
    }

    @Test
    fun english_planStringsTakeArgs() {
        assertEquals("Build menu", context.getString(R.string.ration_plan_build))
        assertEquals("Week: about 2,350 ₽ of 3,000 ₽", context.getString(R.string.ration_plan_week_cost, "2,350 ₽", "3,000 ₽"))
        assertEquals("Over budget by 350 ₽", context.getString(R.string.ration_plan_over_budget, "350 ₽"))
        assertEquals("cook ×2", context.getString(R.string.ration_plan_cook_portions, 2))
        assertTrue(context.getString(R.string.ration_plan_no_dishes, "Dinner").startsWith("No dishes for: Dinner"))
    }

    @Test
    @Config(qualifiers = "ru")
    fun russian_planStringsTakeArgs() {
        assertEquals("Подобрать меню", context.getString(R.string.ration_plan_build))
        assertEquals("Подобрать заново", context.getString(R.string.ration_plan_rebuild))
        assertEquals("Чтобы подобрать меню, нужна модель ИИ. Выберите вариант:", context.getString(R.string.ration_plan_model_text))
        assertTrue(context.getString(R.string.ration_remote_setup_steps).contains("«Рацион»"))
        assertTrue(!context.getString(R.string.ration_remote_setup_steps).contains("Тренер"))
        assertEquals("разогреть", context.getString(R.string.ration_plan_reheat))
        assertEquals("Неделя: примерно 2 350 ₽ из 3 000 ₽", context.getString(R.string.ration_plan_week_cost, "2 350 ₽", "3 000 ₽"))
        assertEquals("Неделя: примерно 2 350 ₽", context.getString(R.string.ration_plan_week_cost_unlimited, "2 350 ₽"))
        assertEquals("Превышение бюджета на 350 ₽", context.getString(R.string.ration_plan_over_budget, "350 ₽"))
        assertEquals("готовка ×2", context.getString(R.string.ration_plan_cook_portions, 2))
        assertTrue(context.getString(R.string.ration_plan_no_dishes, "Ужин").startsWith("Нет блюд для: Ужин"))
    }
}
