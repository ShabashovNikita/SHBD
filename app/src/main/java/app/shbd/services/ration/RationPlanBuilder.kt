package app.shbd.services.ration

import app.shbd.models.RationCatalog
import app.shbd.models.RationMeal
import app.shbd.models.RationPlan
import app.shbd.models.RationPlanDay
import app.shbd.models.RationPlanSlot
import app.shbd.models.RationRecipe
import app.shbd.models.RationSlotMode
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/** Input of [RationPlanBuilder.build]: the repaired grid plus plan metadata. */
data class RationPlanInput(
    val catalog: RationCatalog,
    val allowed: Map<RationMeal, List<RationRecipe>>,
    val schedule: List<List<RationScheduledSlot>>,
    val grid: List<List<String>>,
    val repeatLimit: Int,
    /** Null: no goal in the profile, [RationPlanBuilder.DEFAULT_CALORIES] is used. */
    val calorieGoal: Int?,
    /** Null = unlimited. */
    val budgetRub: Int?,
    val dates: List<LocalDate>,
    val createdAt: Long = 0L,
    val answersHash: Int = 0,
    val repairedSlots: Int = 0,
    val repeatsRaised: Boolean = false,
    val comment: String = "",
)

/**
 * Portions, nutrition, cost and budget. Each day gets its own portion factor
 * so the day lands on the calorie goal and never above it (the goal already
 * holds the deficit). Days whose dishes need portions outside 0.8…1.6 get a
 * lighter / heavier dish first. Over budget, the priciest cooked dishes are
 * swapped for cheaper allowed ones — portions are never cut for money.
 */
object RationPlanBuilder {
    const val DEFAULT_CALORIES = 2000
    const val MIN_FACTOR = 0.8
    const val MAX_FACTOR = 1.6

    /** A day may go over the goal by at most this much. */
    const val MAX_OVER_KCAL = 50
    private const val MAX_CALORIE_SWAPS = 40

    fun build(input: RationPlanInput): RationPlan {
        val goal = input.calorieGoal?.takeIf { it > 0 } ?: DEFAULT_CALORIES
        val grid = input.grid.map { it.toMutableList() }.toMutableList()
        val portions = mutableMapOf<String, RationPortion>()
        fun portion(id: String): RationPortion =
            portions.getOrPut(id) { RationRecipeMath.portion(input.catalog.recipe(id)!!, input.catalog) }

        fitCalories(input, grid, goal, ::portion)
        var plan = assemble(input, grid, goal, ::portion)
        val budget = input.budgetRub
        if (budget != null) {
            while (plan.weekCostRub > budget) {
                if (!swapCheaper(input, grid, ::portion)) break
                plan = assemble(input, grid, goal, ::portion)
            }
        }
        return plan.copy(
            calorieGoalAssumed = input.calorieGoal == null || input.calorieGoal <= 0,
            overBudgetRub = budget?.let { (plan.weekCostRub - it).coerceAtLeast(0) } ?: 0,
        )
    }

    /**
     * Factor of one day: goal / day kcal, rounded down to 0.01, at most
     * [MAX_FACTOR]. No lower bound, so the day never goes above the goal
     * (only portion rounding, a few kcal, well under [MAX_OVER_KCAL]).
     */
    fun dayFactor(goal: Int, dayKcal: Double): Double {
        if (dayKcal <= 0.0) return 1.0
        return floor(goal / dayKcal * 100).coerceIn(1.0, MAX_FACTOR * 100) / 100.0
    }

    /** How far a day is from portions in [MIN_FACTOR]…[MAX_FACTOR]; 0 inside. */
    private fun dayPenalty(goal: Int, dayKcal: Double): Double {
        if (dayKcal <= 0.0) return 0.0
        val f = goal / dayKcal
        return maxOf(0.0, ln(MIN_FACTOR / f), ln(f / MAX_FACTOR))
    }

    /**
     * Swaps dishes of days that would need too small / too big portions for
     * allowed dishes of the same meal (filter and repeat limit kept), while
     * the total penalty goes down. Reheat slots follow their cook slot.
     */
    private fun fitCalories(
        input: RationPlanInput,
        grid: MutableList<MutableList<String>>,
        goal: Int,
        portion: (String) -> RationPortion,
    ) {
        fun total(): Double = grid.sumOf { day -> dayPenalty(goal, day.sumOf { portion(it).kcal }) }
        var current = total()
        repeat(MAX_CALORIE_SWAPS) {
            if (current <= 0.0) return
            val uses = cookUses(input, grid)
            var best: Pair<RationScheduledSlot, String>? = null
            var bestPenalty = current
            for (slot in input.schedule.flatten().filter { it.mode == RationSlotMode.COOK }) {
                val old = gridAt(input, grid, slot)
                for (candidate in input.allowed[slot.meal].orEmpty()) {
                    if (candidate.id == old || (uses[candidate.id] ?: 0) + slot.portions > input.repeatLimit) continue
                    replaceDish(input, grid, slot, candidate.id)
                    val penalty = total()
                    replaceDish(input, grid, slot, old)
                    if (penalty < bestPenalty - 1e-9) {
                        bestPenalty = penalty
                        best = slot to candidate.id
                    }
                }
            }
            val (slot, id) = best ?: return
            replaceDish(input, grid, slot, id)
            current = bestPenalty
        }
    }

    private fun assemble(
        input: RationPlanInput,
        grid: List<List<String>>,
        goal: Int,
        portion: (String) -> RationPortion,
    ): RationPlan {
        val factors = grid.map { day -> dayFactor(goal, day.sumOf { portion(it).kcal }) }
        // Every eaten portion (reheats too) at its own day's factor.
        val eaten = input.schedule.flatMapIndexed { d, day ->
            day.indices.map { s -> input.catalog.recipe(grid[d][s])!! to factors[d] }
        }
        val shopping = RationShoppingList.build(input.catalog, eaten)
        val days = input.schedule.mapIndexed { d, day ->
            val k = factors[d]
            RationPlanDay(
                date = input.dates[d].toString(),
                portionFactor = k,
                slots = day.mapIndexed { s, slot ->
                    val id = grid[d][s]
                    val p = portion(id)
                    RationPlanSlot(
                        meal = slot.meal,
                        recipeId = id,
                        recipeName = input.catalog.recipe(id)!!.name,
                        mode = slot.mode,
                        portions = slot.portions,
                        kcal = (p.kcal * k).roundToInt(),
                        protein = round1(p.protein * k),
                        fat = round1(p.fat * k),
                        carbs = round1(p.carbs * k),
                        costRub = (p.costRub * k).roundToInt(),
                    )
                },
            )
        }
        return RationPlan(
            createdAt = input.createdAt,
            startDate = input.dates.firstOrNull()?.toString().orEmpty(),
            answersHash = input.answersHash,
            calorieGoal = goal,
            portionFactor = if (factors.isEmpty()) 1.0 else (factors.average() * 100).roundToInt() / 100.0,
            days = days,
            shopping = shopping.items,
            pantry = shopping.pantry,
            weekCostRub = shopping.costRub,
            budgetRub = input.budgetRub,
            repeatsRaised = input.repeatsRaised,
            repairedSlots = input.repairedSlots,
            comment = input.comment,
        )
    }

    /**
     * Swaps the priciest cooked dish that has a cheaper allowed alternative of
     * the same meal within the repeat limit; its reheat slots follow. False
     * when no swap is left.
     */
    private fun swapCheaper(input: RationPlanInput, grid: MutableList<MutableList<String>>, portion: (String) -> RationPortion): Boolean {
        val uses = cookUses(input, grid)
        val cookSlots = input.schedule.flatten().filter { it.mode == RationSlotMode.COOK }
            .sortedByDescending { portion(gridAt(input, grid, it)).costRub * it.portions }
        for (slot in cookSlots) {
            val current = gridAt(input, grid, slot)
            val currentCost = portion(current).costRub
            val cheaper = input.allowed[slot.meal].orEmpty()
                .filter { it.id != current && portion(it.id).costRub < currentCost }
                .filter { (uses[it.id] ?: 0) + slot.portions <= input.repeatLimit }
                .minByOrNull { portion(it.id).costRub }
                ?: continue
            replaceDish(input, grid, slot, cheaper.id)
            return true
        }
        return false
    }

    /** Cooked portions per dish over the week. */
    private fun cookUses(input: RationPlanInput, grid: List<List<String>>): Map<String, Int> {
        val uses = mutableMapOf<String, Int>()
        input.schedule.forEachIndexed { d, day ->
            day.forEachIndexed { s, slot -> if (slot.mode == RationSlotMode.COOK) uses.merge(grid[d][s], slot.portions, Int::plus) }
        }
        return uses
    }

    /** Puts [id] into the COOK [slot] and every reheat slot fed by it. */
    private fun replaceDish(input: RationPlanInput, grid: MutableList<MutableList<String>>, slot: RationScheduledSlot, id: String) {
        input.schedule.forEachIndexed { d, day ->
            day.forEachIndexed { s, other ->
                val same = other.meal == slot.meal && (
                    (other.day == slot.day && other.mode == RationSlotMode.COOK) ||
                        (other.mode == RationSlotMode.REHEAT && other.sourceDay == slot.day)
                    )
                if (same) grid[d][s] = id
            }
        }
    }

    private fun gridAt(input: RationPlanInput, grid: List<List<String>>, slot: RationScheduledSlot): String =
        grid[slot.day][input.schedule[slot.day].indexOfFirst { it.meal == slot.meal }]

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
}
