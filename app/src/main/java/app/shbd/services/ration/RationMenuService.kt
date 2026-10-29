package app.shbd.services.ration

import app.shbd.models.RationCatalog
import app.shbd.models.RationMeal
import app.shbd.models.RationPlan
import app.shbd.models.RationSurveyAnswers
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlinx.serialization.json.Json

/**
 * Builds and saves the weekly menu: filter → batch-cooking slots → AI model
 * → repair → portions / budget / shopping → save. A failure leaves the saved
 * plan untouched.
 */
class RationMenuService(
    private val catalog: suspend () -> RationCatalog,
    model: suspend (String) -> String,
    private val save: suspend (RationPlan) -> Unit,
    private val responseLanguage: () -> String? = { null },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val planner = RationAiPlanner(model)

    suspend fun build(answers: RationSurveyAnswers, calorieGoal: Int?, today: LocalDate): RationPlan {
        val catalog = catalog()
        val normalized = answers.normalized()
        val filter = RationDishFilter.allowed(catalog, normalized)
        if (filter.emptyMeals.isNotEmpty()) throw RationPlanError.NoDishes(filter.emptyMeals)

        val dates = RationCookSchedule.dates(today)
        val schedule = RationCookSchedule.build(today, normalized)
        val answersHash = answersHash(normalized)
        val seed = answersHash.toLong() * 100 + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val goal = calorieGoal?.takeIf { it > 0 } ?: RationPlanBuilder.DEFAULT_CALORIES
        val budget = if (normalized.budgetUnlimited) null else normalized.weeklyBudgetRub

        val answer = planner.plan(
            RationPlannerRequest(
                catalog = catalog,
                allowed = filter.allowed,
                schedule = schedule,
                dates = dates,
                calorieGoal = goal,
                budgetRub = budget,
                repeatLimit = normalized.repeatLimit,
                cookingTime = normalized.cookingTime,
                seed = seed,
                responseLanguage = responseLanguage(),
            ),
        )
        val repair = RationSlotRepair.repair(
            allowed = filter.allowed,
            schedule = schedule,
            answer = answer,
            repeatLimit = normalized.repeatLimit,
            targetKcal = mealTargets(goal, filter.allowed.keys),
            kcalOf = { RationRecipeMath.portion(it, catalog).kcal },
            seed = seed,
        )
        val plan = RationPlanBuilder.build(
            RationPlanInput(
                catalog = catalog,
                allowed = filter.allowed,
                schedule = schedule,
                grid = repair.grid,
                repeatLimit = repair.repeatLimit,
                calorieGoal = calorieGoal,
                budgetRub = budget,
                dates = dates,
                createdAt = now(),
                answersHash = answersHash,
                repairedSlots = repair.repairedSlots,
                repeatsRaised = repair.repeatsRaised,
                comment = answer.comment,
            ),
        )
        save(plan)
        return plan
    }

    companion object {
        private val hashJson = Json { encodeDefaults = true }

        /** Stable across launches (enum hashCodes are not): hash of the JSON form. */
        fun answersHash(answers: RationSurveyAnswers): Int =
            hashJson.encodeToString(RationSurveyAnswers.serializer(), answers).hashCode()

        /** Share of the daily goal per meal, for picking a dish when the engine fills a slot. */
        fun mealTargets(goal: Int, meals: Set<RationMeal>): Map<RationMeal, Double> {
            val weights = mapOf(RationMeal.BREAKFAST to 0.25, RationMeal.LUNCH to 0.35, RationMeal.DINNER to 0.3, RationMeal.SNACK to 0.1)
            val total = meals.sumOf { weights.getValue(it) }.takeIf { it > 0 } ?: 1.0
            return meals.associateWith { goal * weights.getValue(it) / total }
        }
    }
}
