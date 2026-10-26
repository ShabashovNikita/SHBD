package app.shbd.ui.settings

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import app.shbd.R
import app.shbd.ui.navigation.ShbdRoutes
import java.text.Normalizer
import java.util.Locale

/**
 * Static, offline index of every settings row for hub search (F1). Each entry
 * maps a setting label + guessable synonyms to the route that owns it.
 *
 * Labels and groups are string resources, so they match whatever locale the
 * app is in, and the localized group label is itself searchable. Keywords live
 * in per-entry string-array resources with English defaults in
 * values/strings.xml: a locale can add native search vocabulary, and any
 * locale without an array silently falls back to English — the same resource
 * fallback model as the rest of the strings. Conditional rows (keto,
 * on-device) are included with their owning screen so search still finds them.
 */
internal data class SettingsIndexEntry(
    @StringRes val groupRes: Int,
    @StringRes val labelRes: Int,
    @ArrayRes val keywordsRes: Int,
    val route: String,
    val icon: ImageVector,
)

private val DIACRITIC_REGEX = Regex("\\p{M}")

/**
 * Normalize for matching: lowercase + strip diacritics (Größe -> grosse).
 * Case folding is [Locale.ROOT]-stable: Turkish lowercases I -> ı, which would
 * otherwise break "Imperial" / "API" lookups for Turkish users.
 */
internal fun settingsSearchFold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITIC_REGEX, "")
        .lowercase(Locale.ROOT)
        // ß has no NFD decomposition; map it so "grosse" finds "Größe".
        .replace("ß", "ss")

/**
 * Pre-folded search entry: label + group + keywords normalized once per locale
 * so the results list folds the query only, not every index entry, on each
 * keystroke (F perf pass, device walk 2026-08-24). [settingsSearchMatchesFolded]
 * is the per-keystroke matcher over it.
 */
internal class FoldedSettingsEntry(
    val entry: SettingsIndexEntry,
    val label: String,
    val labelFold: String,
    val groupFold: String,
    val keywordFolds: List<String>,
)

internal fun foldSettingsEntry(
    entry: SettingsIndexEntry,
    label: String,
    group: String,
    keywords: List<String>,
): FoldedSettingsEntry =
    FoldedSettingsEntry(
        entry = entry,
        label = label,
        labelFold = settingsSearchFold(label),
        groupFold = settingsSearchFold(group),
        keywordFolds = keywords.map { settingsSearchFold(it) },
    )

/**
 * Per-keystroke matcher over a pre-folded entry; [foldedQuery] must already be
 * folded by the caller (the results list folds the query once per keystroke,
 * not once per entry).
 */
internal fun settingsSearchMatchesFolded(folded: FoldedSettingsEntry, foldedQuery: String): Boolean {
    if (foldedQuery.isBlank()) return false
    return folded.labelFold.contains(foldedQuery) ||
        folded.groupFold.contains(foldedQuery) ||
        folded.keywordFolds.any { it.contains(foldedQuery) }
}

internal val SETTINGS_INDEX: List<SettingsIndexEntry> = listOf(
    // — Personal Info —
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_units,
        R.array.settings_search_kw_units, ShbdRoutes.SETTINGS_PERSONAL, Icons.Outlined.Straighten),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_energy_unit,
        R.array.settings_search_kw_energy, ShbdRoutes.SETTINGS_PERSONAL, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_gender,
        R.array.settings_search_kw_gender, ShbdRoutes.SETTINGS_PERSONAL, Icons.Outlined.Person),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_birthday,
        R.array.settings_search_kw_birthday, ShbdRoutes.SETTINGS_PERSONAL, Icons.Outlined.Person),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_height,
        R.array.settings_search_kw_height, ShbdRoutes.SETTINGS_PERSONAL, Icons.Outlined.Straighten),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_weight,
        R.array.settings_search_kw_weight, ShbdRoutes.SETTINGS_PERSONAL, Icons.Outlined.MonitorWeight),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_body_fat,
        R.array.settings_search_kw_body_fat, ShbdRoutes.SETTINGS_PERSONAL, Icons.Outlined.Percent),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_use_body_fat_bmr,
        R.array.settings_search_kw_use_body_fat_bmr, ShbdRoutes.SETTINGS_PERSONAL, Icons.Outlined.Percent),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.settings_goal_body_fat,
        R.array.settings_search_kw_goal_body_fat, ShbdRoutes.SETTINGS_PERSONAL, Icons.Outlined.TrackChanges),
    SettingsIndexEntry(R.string.settings_section_personal, R.string.body_measurements_title,
        R.array.settings_search_kw_body_measurements, ShbdRoutes.BODY_MEASUREMENTS, Icons.Outlined.Straighten),

    // — Goals & Nutrition —
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_weight_goal,
        R.array.settings_search_kw_weight_goal, ShbdRoutes.SETTINGS_GOALS, Icons.Outlined.Equalizer),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_diet_mode,
        R.array.settings_search_kw_diet_mode, ShbdRoutes.SETTINGS_GOALS, Icons.Outlined.Restaurant),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_keto_carb_mode,
        R.array.settings_search_kw_keto_carb_mode, ShbdRoutes.SETTINGS_GOALS, Icons.Outlined.Tune),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_keto_net_carbs,
        R.array.settings_search_kw_keto_net_carbs, ShbdRoutes.SETTINGS_GOALS, Icons.Outlined.Restaurant),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_activity_level,
        R.array.settings_search_kw_activity_level, ShbdRoutes.SETTINGS_GOALS, Icons.Outlined.Speed),
    SettingsIndexEntry(
        R.string.settings_section_goals,
        R.string.home_display_calorie_mode,
        R.array.settings_search_kw_calorie_gauge,
        ShbdRoutes.SETTINGS_GOALS,
        Icons.Outlined.LocalFireDepartment,
    ),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_weekly_change,
        R.array.settings_search_kw_weekly_change, ShbdRoutes.SETTINGS_GOALS, Icons.Outlined.Speed),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_goal_weight,
        R.array.settings_search_kw_goal_weight, ShbdRoutes.SETTINGS_GOALS, Icons.Outlined.Equalizer),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_adaptive_goals,
        R.array.settings_search_kw_adaptive_goals, ShbdRoutes.SETTINGS_GOALS, Icons.Outlined.TrackChanges),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_recalculate_goals,
        R.array.settings_search_kw_recalculate_goals, ShbdRoutes.SETTINGS_GOALS, Icons.Outlined.Refresh),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_calc_methods,
        R.array.settings_search_kw_calc_methods, ShbdRoutes.CALCULATION_METHODS, Icons.Outlined.Calculate),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_day_types_title,
        R.array.settings_search_kw_day_types, ShbdRoutes.SETTINGS_DAY_TYPES, Icons.Outlined.CalendarToday),
    SettingsIndexEntry(R.string.settings_section_goals, R.string.settings_goal_phases_title,
        R.array.settings_search_kw_goal_phases, ShbdRoutes.GOAL_PHASES, Icons.Outlined.Flag),

    // — Calories & Macros —
    SettingsIndexEntry(R.string.settings_section_nutrition_targets, R.string.settings_energy_goals,
        R.array.settings_search_kw_energy_goals, ShbdRoutes.SETTINGS_NUTRITION_TARGETS, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_nutrition_targets, R.string.settings_calories,
        R.array.settings_search_kw_calories, ShbdRoutes.SETTINGS_NUTRITION_TARGETS, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_nutrition_targets, R.string.macro_protein,
        R.array.settings_search_kw_macro_protein, ShbdRoutes.SETTINGS_NUTRITION_TARGETS, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_nutrition_targets, R.string.macro_carbs,
        R.array.settings_search_kw_macro_carbs, ShbdRoutes.SETTINGS_NUTRITION_TARGETS, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_nutrition_targets, R.string.macro_fat,
        R.array.settings_search_kw_macro_fat, ShbdRoutes.SETTINGS_NUTRITION_TARGETS, Icons.Outlined.LocalFireDepartment),
    SettingsIndexEntry(R.string.settings_section_nutrition_targets, R.string.settings_other_nutrient_goals,
        R.array.settings_search_kw_other_nutrient_goals, ShbdRoutes.OPTIONAL_NUTRIENT_GOALS, Icons.Outlined.LocalFireDepartment),

    // — Food & Entry —
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_default_to_grams,
        R.array.settings_search_kw_default_to_grams, ShbdRoutes.SETTINGS_FOOD, Icons.Outlined.LocalDining),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_food_log_sort,
        R.array.settings_search_kw_food_log_sort, ShbdRoutes.SETTINGS_FOOD, Icons.Outlined.LocalDining),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_meal_times,
        R.array.settings_search_kw_meal_times, ShbdRoutes.SETTINGS_FOOD, Icons.Outlined.LocalDining),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_photo_note_prompt,
        R.array.settings_search_kw_photo_note_prompt, ShbdRoutes.SETTINGS_FOOD, Icons.Outlined.LocalDining),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_meal_constituents,
        R.array.settings_search_kw_meal_constituents, ShbdRoutes.SETTINGS_FOOD, Icons.Outlined.Restaurant),
    SettingsIndexEntry(R.string.settings_group_food, R.string.food_search_sheet_title,
        R.array.settings_search_kw_food_databases, ShbdRoutes.SETTINGS_FOOD, Icons.Outlined.Storage),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_serving_unit_mode,
        R.array.settings_search_kw_serving_unit_mode, ShbdRoutes.SETTINGS_FOOD, Icons.Outlined.Tune),
    SettingsIndexEntry(R.string.settings_group_food, R.string.settings_serving_unit_heuristics,
        R.array.settings_search_kw_serving_unit_heuristics, ShbdRoutes.SETTINGS_FOOD, Icons.Outlined.Tune),

    // — Display —
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_home_display,
        R.array.settings_search_kw_home_display, ShbdRoutes.HOME_DISPLAY, Icons.Outlined.Dashboard),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_appearance,
        R.array.settings_search_kw_appearance, ShbdRoutes.SETTINGS_APP, Icons.Outlined.Brightness6),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_theme_color,
        R.array.settings_search_kw_theme_color, ShbdRoutes.SETTINGS_APP, Icons.Outlined.Palette),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_language_title,
        R.array.settings_search_kw_language, ShbdRoutes.SETTINGS_APP, Icons.Outlined.Language),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_fixed_launcher_icon,
        R.array.settings_search_kw_fixed_launcher_icon, ShbdRoutes.SETTINGS_APP, Icons.Outlined.Star),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_system_date_time_pickers,
        R.array.settings_search_kw_system_date_time_pickers, ShbdRoutes.SETTINGS_APP, Icons.Outlined.Schedule),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_customize_progress,
        R.array.settings_search_kw_customize_progress, ShbdRoutes.CUSTOMIZE_PROGRESS, Icons.AutoMirrored.Outlined.ShowChart),
    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_progress_nutrient_averages,
        R.array.settings_search_kw_progress_nutrient_averages, ShbdRoutes.CUSTOMIZE_PROGRESS, Icons.AutoMirrored.Outlined.ShowChart),

    SettingsIndexEntry(R.string.settings_group_app_display, R.string.settings_week_starts,
        R.array.settings_search_kw_week_starts, ShbdRoutes.CUSTOMIZE_PROGRESS, Icons.Outlined.CalendarToday),

    // — Trackers & Reminders —
    SettingsIndexEntry(R.string.settings_group_trackers, R.string.settings_water_title,
        R.array.settings_search_kw_water, ShbdRoutes.waterRoute("search"), Icons.Outlined.WaterDrop),
    SettingsIndexEntry(R.string.settings_group_trackers, R.string.settings_nicotine_title,
        R.array.settings_search_kw_nicotine, ShbdRoutes.nicotineRoute("search"), Icons.Outlined.FilterAlt),
    SettingsIndexEntry(R.string.settings_group_trackers, R.string.settings_caffeine_title,
        R.array.settings_search_kw_caffeine, ShbdRoutes.caffeineRoute("search"), Icons.Outlined.LocalCafe),
    SettingsIndexEntry(R.string.settings_group_trackers, R.string.settings_notes_title,
        R.array.settings_search_kw_notes, ShbdRoutes.notesRoute("search"), Icons.Outlined.Notes),
    SettingsIndexEntry(R.string.settings_group_trackers, R.string.settings_fasting_title,
        R.array.settings_search_kw_fasting, ShbdRoutes.fastingRoute("search"), Icons.Outlined.Schedule),
    SettingsIndexEntry(R.string.settings_group_trackers, R.string.settings_notifications,
        R.array.settings_search_kw_notifications, ShbdRoutes.notificationsRoute("search"), Icons.Outlined.Notifications),

    // — AI Provider —
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_features_master,
        R.array.settings_search_kw_ai_features_master, ShbdRoutes.SETTINGS_AI, Icons.Outlined.SmartToy),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_provider,
        R.array.settings_search_kw_ai_provider, ShbdRoutes.SETTINGS_AI, Icons.Outlined.SmartToy),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_model,
        R.array.settings_search_kw_ai_model, ShbdRoutes.SETTINGS_AI, Icons.Outlined.Tune),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_vision_model,
        R.array.settings_search_kw_ai_vision_model, ShbdRoutes.SETTINGS_AI, Icons.Outlined.Tune),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_api_key,
        R.array.settings_search_kw_api_key, ShbdRoutes.SETTINGS_AI, Icons.Outlined.Key),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_base_url,
        R.array.settings_search_kw_base_url, ShbdRoutes.SETTINGS_AI, Icons.Outlined.Link),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_allow_insecure_http,
        R.array.settings_search_kw_ai_allow_insecure_http, ShbdRoutes.SETTINGS_AI, Icons.Outlined.Link),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_on_device_model,
        R.array.settings_search_kw_on_device_model, ShbdRoutes.SETTINGS_AI, Icons.Outlined.SmartToy),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_max_tokens,
        R.array.settings_search_kw_max_tokens, ShbdRoutes.SETTINGS_AI, Icons.Outlined.Numbers),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_ai_read_timeout,
        R.array.settings_search_kw_ai_read_timeout, ShbdRoutes.SETTINGS_AI, Icons.Outlined.Speed),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_gemini_google_search,
        R.array.settings_search_kw_gemini_google_search, ShbdRoutes.SETTINGS_AI, Icons.Outlined.Search),
    SettingsIndexEntry(R.string.settings_group_ai, R.string.settings_section_fallback,
        R.array.settings_search_kw_section_fallback, ShbdRoutes.SETTINGS_AI, Icons.Outlined.SmartToy),

    // — Speech-to-Text —
    SettingsIndexEntry(R.string.settings_section_speech, R.string.settings_section_speech,
        R.array.settings_search_kw_section_speech, ShbdRoutes.SETTINGS_SPEECH, Icons.Outlined.Mic),

    // — Coach —
    SettingsIndexEntry(R.string.settings_group_coach, R.string.settings_show_coach_tab,
        R.array.settings_search_kw_show_coach_tab, ShbdRoutes.SETTINGS_COACH, Icons.Outlined.Forum),
    SettingsIndexEntry(R.string.settings_group_coach, R.string.settings_section_custom_instructions,
        R.array.settings_search_kw_section_custom_instructions, ShbdRoutes.SETTINGS_COACH, Icons.Outlined.SmartToy),

    // — Health Data —
    SettingsIndexEntry(R.string.settings_section_health, R.string.settings_health_connect,
        R.array.settings_search_kw_health_connect, ShbdRoutes.SETTINGS_HEALTH, Icons.Outlined.Favorite),
    SettingsIndexEntry(R.string.settings_section_health, R.string.settings_manage_health_access,
        R.array.settings_search_kw_manage_health_access, ShbdRoutes.SETTINGS_HEALTH, Icons.Outlined.Favorite),
    SettingsIndexEntry(R.string.settings_section_health, R.string.settings_health_background_sync,
        R.array.settings_search_kw_health_background_sync, ShbdRoutes.SETTINGS_HEALTH, Icons.Outlined.Favorite),
    SettingsIndexEntry(R.string.settings_group_data, R.string.export_diary_title,
        R.array.settings_search_kw_export_diary, ShbdRoutes.SETTINGS_DATA, Icons.Outlined.Sync),
    SettingsIndexEntry(R.string.settings_group_data, R.string.export_body_metrics_title,
        R.array.settings_search_kw_export_body_metrics, ShbdRoutes.SETTINGS_DATA, Icons.Outlined.MonitorWeight),
    SettingsIndexEntry(R.string.settings_group_data, R.string.import_diary_title,
        R.array.settings_search_kw_import_diary, ShbdRoutes.SETTINGS_DATA, Icons.Outlined.Sync),
    SettingsIndexEntry(R.string.settings_group_data, R.string.import_body_metrics_title,
        R.array.settings_search_kw_import_body_metrics, ShbdRoutes.SETTINGS_DATA, Icons.Outlined.MonitorWeight),
    SettingsIndexEntry(R.string.settings_group_data, R.string.settings_sync_section,
        R.array.settings_search_kw_sync, ShbdRoutes.syncRoute("search"), Icons.Outlined.Sync),
    SettingsIndexEntry(R.string.settings_danger_zone, R.string.settings_clear_food_log,
        R.array.settings_search_kw_clear_food_log, ShbdRoutes.SETTINGS_DATA, Icons.Outlined.DeleteForever),
    SettingsIndexEntry(R.string.settings_danger_zone, R.string.settings_delete_all_data,
        R.array.settings_search_kw_delete_all_data, ShbdRoutes.SETTINGS_DATA, Icons.Outlined.DeleteForever),
)

/** Resolve one index entry into its localized foldable form. */
internal fun resolveSettingsIndex(context: Context): List<FoldedSettingsEntry> =
    SETTINGS_INDEX.map { entry ->
        foldSettingsEntry(
            entry = entry,
            label = context.getString(entry.labelRes),
            group = context.getString(entry.groupRes),
            keywords = context.resources.getStringArray(entry.keywordsRes).toList(),
        )
    }
