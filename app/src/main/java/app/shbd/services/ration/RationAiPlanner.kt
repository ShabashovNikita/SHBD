package app.shbd.services.ration

import app.shbd.models.CookingTime
import app.shbd.models.RationCatalog
import app.shbd.models.RationMeal
import app.shbd.models.RationRecipe
import app.shbd.models.RationSlotMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** What the model chose: per day (0..6) the recipe id of each meal, plus a short comment. */
data class RationModelAnswer(
    val days: List<Map<RationMeal, String>>,
    val comment: String,
)

/** Everything the prompt needs; candidates are already filtered by [RationDishFilter]. */
data class RationPlannerRequest(
    val catalog: RationCatalog,
    val allowed: Map<RationMeal, List<RationRecipe>>,
    val schedule: List<List<RationScheduledSlot>>,
    val dates: List<LocalDate>,
    val calorieGoal: Int,
    /** Null = unlimited. */
    val budgetRub: Int?,
    val repeatLimit: Int,
    val cookingTime: CookingTime,
    val seed: Long,
    /** English name of the comment language ("Russian"); null = English. */
    val responseLanguage: String?,
)

/**
 * Asks the active AI model (Coach / Profile → AI provider) to pick the week's
 * dishes. [model] is `FoodAnalysisService.completeText` in the app, a fake in
 * tests. The prompt is English for model reliability; only the comment is in
 * the UI language.
 */
class RationAiPlanner(private val model: suspend (String) -> String) {

    /** Unreadable twice → [RationPlanError.BadAnswer]; AI errors propagate unchanged. */
    suspend fun plan(request: RationPlannerRequest): RationModelAnswer {
        val prompt = buildPrompt(request)
        parse(model(prompt))?.let { return it }
        parse(model(prompt + RETRY_SUFFIX))?.let { return it }
        throw RationPlanError.BadAnswer
    }

    companion object {
        const val MAX_CANDIDATES = 25
        const val MAX_COMMENT = 300
        const val RETRY_SUFFIX = "\n\nReturn ONLY the JSON object described above, with no other text."

        private val json = Json { isLenient = true; ignoreUnknownKeys = true }

        /** At most [MAX_CANDIDATES] per meal; a seeded pick keeps the same answers → same prompt. */
        fun candidates(allowed: Map<RationMeal, List<RationRecipe>>, seed: Long): Map<RationMeal, List<RationRecipe>> =
            allowed.mapValues { (meal, list) ->
                if (list.size <= MAX_CANDIDATES) {
                    list
                } else {
                    val picked = list.shuffled(Random(seed * 31 + meal.ordinal)).take(MAX_CANDIDATES).toSet()
                    list.filter { it in picked }
                }
            }

        fun buildPrompt(r: RationPlannerRequest): String = buildString {
            val meals = r.allowed.keys.sortedBy { it.ordinal }
            appendLine("You are a meal planner. Build a 7-day menu by picking dishes ONLY from the candidate lists below, by id.")
            appendLine("Daily calorie goal: ${r.calorieGoal} kcal (it already includes the user's deficit or surplus). A day must not go over it. The app scales each day's portions to the goal; still pick dishes so that every day is close to it.")
            appendLine("Meals every day: ${meals.joinToString(", ") { it.name }}.")
            appendLine(
                if (r.budgetRub == null) "Weekly food budget: unlimited."
                else "Weekly food budget: ${r.budgetRub} RUB for the whole week (prices below are per portion).",
            )
            appendLine("A dish may appear at most ${r.repeatLimit} time(s) per week; reheated days count.")
            appendLine("Days: " + r.dates.mapIndexed { i, d -> "${i + 1}=${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" }.joinToString(", ") + ".")
            val reheats = r.schedule.flatten().filter { it.mode == RationSlotMode.REHEAT }
            if (reheats.isNotEmpty()) {
                appendLine("Batch cooking: these slots reuse the dish cooked earlier, repeat the same id there:")
                reheats.forEach { appendLine("- day ${it.day + 1} ${it.meal.name} = day ${it.sourceDay + 1} ${it.meal.name}") }
            }
            appendLine("Rules: variety across the week; protein in every main meal; mix cuisines and bases (grain, potato, pasta).")
            if (r.cookingTime == CookingTime.LOVE) {
                val weekend = r.dates.indices.filter { r.dates[it].dayOfWeek == DayOfWeek.SATURDAY || r.dates[it].dayOfWeek == DayOfWeek.SUNDAY }
                if (weekend.isNotEmpty()) appendLine("The user loves cooking: longer dishes fit best on days ${weekend.joinToString(", ") { "${it + 1}" }}.")
            }
            appendLine()
            appendLine("Candidates (id | name | cuisine | minutes | kcal | rub):")
            candidates(r.allowed, r.seed).forEach { (meal, list) ->
                appendLine("${meal.name}:")
                list.forEach { recipe ->
                    val p = RationRecipeMath.portion(recipe, r.catalog)
                    appendLine("${recipe.id} | ${recipe.name} | ${recipe.cuisine.name} | ${recipe.minutes} | ${p.kcal.roundToInt()} | ${p.costRub.roundToInt()}")
                }
            }
            appendLine()
            val example = meals.joinToString(",") { "\"${it.name}\":\"id\"" }
            appendLine("Answer with ONLY this JSON, no markdown:")
            appendLine("{\"days\":[{\"day\":1,$example}, ... exactly 7 objects, day 1 to 7],\"comment\":\"...\"}")
            append("The comment is one or two short sentences about the menu, in ${r.responseLanguage ?: "English"}.")
        }

        /** Tolerant: the first `{` to the last `}`, meal keys in any case, extra keys ignored. */
        fun parse(text: String): RationModelAnswer? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val root = runCatching { json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject }.getOrNull() ?: return null
            val daysArray = root.entries.firstOrNull { it.key.equals("days", ignoreCase = true) }?.value as? JsonArray ?: return null
            val days = MutableList<Map<RationMeal, String>>(RationCookSchedule.DAYS) { emptyMap() }
            daysArray.forEachIndexed { index, element ->
                val obj = element as? JsonObject ?: return@forEachIndexed
                val dayIndex = ((obj["day"] as? JsonPrimitive)?.intOrNull?.minus(1)) ?: index
                if (dayIndex !in days.indices) return@forEachIndexed
                days[dayIndex] = obj.entries.mapNotNull { (key, value) ->
                    val meal = RationMeal.entries.firstOrNull { it.name.equals(key.trim(), ignoreCase = true) } ?: return@mapNotNull null
                    val id = (value as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    meal to id
                }.toMap()
            }
            val comment = (root["comment"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().take(MAX_COMMENT)
            return RationModelAnswer(days, comment)
        }
    }
}
