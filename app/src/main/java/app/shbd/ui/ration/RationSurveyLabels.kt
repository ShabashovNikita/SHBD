package app.shbd.ui.ration

import androidx.annotation.StringRes
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

/** String resources for the survey options: the stored answers keep enum ids only. */
internal object RationSurveyLabels {
    @StringRes
    fun meal(m: RationMeal): Int = when (m) {
        RationMeal.BREAKFAST -> R.string.ration_survey_meal_breakfast
        RationMeal.LUNCH -> R.string.ration_survey_meal_lunch
        RationMeal.DINNER -> R.string.ration_survey_meal_dinner
        RationMeal.SNACK -> R.string.ration_survey_meal_snack
    }

    @StringRes
    fun allergen(a: Allergen): Int = when (a) {
        Allergen.NUTS -> R.string.ration_survey_allergen_nuts
        Allergen.PEANUTS -> R.string.ration_survey_allergen_peanuts
        Allergen.GLUTEN -> R.string.ration_survey_allergen_gluten
        Allergen.LACTOSE -> R.string.ration_survey_allergen_lactose
        Allergen.EGGS -> R.string.ration_survey_allergen_eggs
        Allergen.FISH -> R.string.ration_survey_allergen_fish
        Allergen.SEAFOOD -> R.string.ration_survey_allergen_seafood
        Allergen.SOY -> R.string.ration_survey_allergen_soy
        Allergen.SESAME -> R.string.ration_survey_allergen_sesame
        Allergen.CITRUS -> R.string.ration_survey_allergen_citrus
        Allergen.HONEY -> R.string.ration_survey_allergen_honey
    }

    @StringRes
    fun dislike(d: Dislike): Int = when (d) {
        Dislike.MUSHROOMS -> R.string.ration_survey_dislike_mushrooms
        Dislike.ONION -> R.string.ration_survey_dislike_onion
        Dislike.GARLIC -> R.string.ration_survey_dislike_garlic
        Dislike.OLIVES -> R.string.ration_survey_dislike_olives
        Dislike.EGGPLANT -> R.string.ration_survey_dislike_eggplant
        Dislike.CILANTRO -> R.string.ration_survey_dislike_cilantro
        Dislike.SPICY -> R.string.ration_survey_dislike_spicy
        Dislike.SUGAR -> R.string.ration_survey_dislike_sugar
        Dislike.PORK -> R.string.ration_survey_dislike_pork
        Dislike.RED_MEAT -> R.string.ration_survey_dislike_red_meat
        Dislike.TOFU -> R.string.ration_survey_dislike_tofu
    }

    @StringRes
    fun cooking(c: CookingTime): Int = when (c) {
        CookingTime.NONE -> R.string.ration_survey_cooking_none
        CookingTime.QUICK -> R.string.ration_survey_cooking_quick
        CookingTime.NORMAL -> R.string.ration_survey_cooking_normal
        CookingTime.LOVE -> R.string.ration_survey_cooking_love
    }

    @StringRes
    fun cookingDesc(c: CookingTime): Int = when (c) {
        CookingTime.NONE -> R.string.ration_survey_cooking_none_desc
        CookingTime.QUICK -> R.string.ration_survey_cooking_quick_desc
        CookingTime.NORMAL -> R.string.ration_survey_cooking_normal_desc
        CookingTime.LOVE -> R.string.ration_survey_cooking_love_desc
    }

    @StringRes
    fun tool(t: KitchenTool): Int = when (t) {
        KitchenTool.STOVE -> R.string.ration_survey_tool_stove
        KitchenTool.OVEN -> R.string.ration_survey_tool_oven
        KitchenTool.MICROWAVE -> R.string.ration_survey_tool_microwave
        KitchenTool.MULTICOOKER -> R.string.ration_survey_tool_multicooker
        KitchenTool.AIR_FRYER -> R.string.ration_survey_tool_air_fryer
        KitchenTool.BLENDER -> R.string.ration_survey_tool_blender
        KitchenTool.STEAMER -> R.string.ration_survey_tool_steamer
        KitchenTool.GRILL_PAN -> R.string.ration_survey_tool_grill_pan
    }

    @StringRes
    fun cuisine(c: Cuisine): Int = when (c) {
        Cuisine.ANY -> R.string.ration_survey_cuisine_any
        Cuisine.HOME -> R.string.ration_survey_cuisine_home
        Cuisine.EUROPEAN -> R.string.ration_survey_cuisine_european
        Cuisine.ITALIAN -> R.string.ration_survey_cuisine_italian
        Cuisine.MEDITERRANEAN -> R.string.ration_survey_cuisine_mediterranean
        Cuisine.ASIAN -> R.string.ration_survey_cuisine_asian
        Cuisine.JAPANESE -> R.string.ration_survey_cuisine_japanese
        Cuisine.CAUCASIAN -> R.string.ration_survey_cuisine_caucasian
        Cuisine.CENTRAL_ASIAN -> R.string.ration_survey_cuisine_central_asian
        Cuisine.MEXICAN -> R.string.ration_survey_cuisine_mexican
        Cuisine.MIDDLE_EASTERN -> R.string.ration_survey_cuisine_middle_eastern
        Cuisine.INDIAN -> R.string.ration_survey_cuisine_indian
    }

    @StringRes
    fun day(d: DayOfWeek): Int = when (d) {
        DayOfWeek.MONDAY -> R.string.ration_survey_day_mon
        DayOfWeek.TUESDAY -> R.string.ration_survey_day_tue
        DayOfWeek.WEDNESDAY -> R.string.ration_survey_day_wed
        DayOfWeek.THURSDAY -> R.string.ration_survey_day_thu
        DayOfWeek.FRIDAY -> R.string.ration_survey_day_fri
        DayOfWeek.SATURDAY -> R.string.ration_survey_day_sat
        DayOfWeek.SUNDAY -> R.string.ration_survey_day_sun
    }

    @StringRes
    fun budgetLevel(l: BudgetLevel): Int = when (l) {
        BudgetLevel.VERY_ECONOMY -> R.string.ration_survey_budget_very_economy
        BudgetLevel.ECONOMY -> R.string.ration_survey_budget_economy
        BudgetLevel.BALANCED -> R.string.ration_survey_budget_balanced
        BudgetLevel.COMFORT -> R.string.ration_survey_budget_comfort
        BudgetLevel.FREE -> R.string.ration_survey_budget_free
    }

    @StringRes
    fun budgetLevelDesc(l: BudgetLevel): Int = when (l) {
        BudgetLevel.VERY_ECONOMY -> R.string.ration_survey_budget_very_economy_desc
        BudgetLevel.ECONOMY -> R.string.ration_survey_budget_economy_desc
        BudgetLevel.BALANCED -> R.string.ration_survey_budget_balanced_desc
        BudgetLevel.COMFORT -> R.string.ration_survey_budget_comfort_desc
        BudgetLevel.FREE -> R.string.ration_survey_budget_free_desc
    }

    /** Title + subtitle of a question step. */
    fun stepTitles(step: RationSurveyStep): Pair<Int, Int>? = when (step) {
        RationSurveyStep.MEALS -> R.string.ration_survey_meals_title to R.string.ration_survey_meals_subtitle
        RationSurveyStep.BUDGET -> R.string.ration_survey_budget_title to R.string.ration_survey_budget_subtitle
        RationSurveyStep.RESTRICTIONS ->
            R.string.ration_survey_restrictions_title to R.string.ration_survey_restrictions_subtitle
        RationSurveyStep.COOKING -> R.string.ration_survey_cooking_title to R.string.ration_survey_cooking_subtitle
        RationSurveyStep.KITCHEN -> R.string.ration_survey_kitchen_title to R.string.ration_survey_kitchen_subtitle
        RationSurveyStep.CUISINES -> R.string.ration_survey_cuisines_title to R.string.ration_survey_cuisines_subtitle
        RationSurveyStep.DELIVERY -> R.string.ration_survey_delivery_title to R.string.ration_survey_delivery_subtitle
        RationSurveyStep.INTRO, RationSurveyStep.SUMMARY -> null
    }
}
