package app.shbd.services.ration

import app.shbd.models.RationMeal
import app.shbd.models.RationRecipe
import app.shbd.models.RationSlotMode
import kotlin.math.abs
import kotlin.random.Random

/** Recipe id for every slot of the schedule (same shape), after repair. */
data class RationRepairResult(
    val grid: List<List<String>>,
    val repairedSlots: Int,
    val repeatsRaised: Boolean,
    /** The repeat limit actually used (raised when there were too few dishes). */
    val repeatLimit: Int,
)

/**
 * Makes the model's grid valid: unknown, forbidden, wrong-meal and over-limit
 * picks and missing slots are refilled from the allowed dishes, least used
 * first, then closest in kcal to the meal's share of the goal. Deterministic
 * for the same input and seed.
 */
object RationSlotRepair {

    fun repair(
        allowed: Map<RationMeal, List<RationRecipe>>,
        schedule: List<List<RationScheduledSlot>>,
        answer: RationModelAnswer?,
        repeatLimit: Int,
        targetKcal: Map<RationMeal, Double>,
        kcalOf: (RationRecipe) -> Double,
        seed: Long,
    ): RationRepairResult {
        val allowedIds = allowed.mapValues { (_, list) -> list.associateBy { it.id } }
        val uses = mutableMapOf<String, Int>()
        val grid = schedule.map { day -> MutableList<String?>(day.size) { null } }
        var limit = repeatLimit.coerceAtLeast(1)
        var repaired = 0

        // 1. Accept valid model picks for COOK slots, in day order.
        schedule.forEachIndexed { d, day ->
            day.forEachIndexed { s, slot ->
                if (slot.mode != RationSlotMode.COOK) return@forEachIndexed
                val id = answer?.days?.getOrNull(d)?.get(slot.meal)
                val ok = id != null && allowedIds[slot.meal]?.containsKey(id) == true &&
                    (uses[id] ?: 0) + slot.portions <= limit
                if (ok) {
                    grid[d][s] = id
                    uses[id!!] = (uses[id] ?: 0) + slot.portions
                } else {
                    repaired++
                }
            }
        }

        // 2. Fill the gaps; raise the limit only when no dish fits at all.
        var raised = false
        val order = allowed.mapValues { (meal, list) -> list.shuffled(Random(seed * 17 + meal.ordinal)) }
        schedule.forEachIndexed { d, day ->
            day.forEachIndexed { s, slot ->
                if (slot.mode != RationSlotMode.COOK || grid[d][s] != null) return@forEachIndexed
                val pool = order[slot.meal].orEmpty()
                if (pool.isEmpty()) return@forEachIndexed
                val target = targetKcal[slot.meal] ?: 0.0
                var pick: RationRecipe? = null
                while (pick == null) {
                    pick = pool
                        .filter { (uses[it.id] ?: 0) + slot.portions <= limit }
                        .minWithOrNull(compareBy<RationRecipe> { uses[it.id] ?: 0 }.thenBy { abs(kcalOf(it) - target) })
                    if (pick == null) {
                        limit++
                        raised = true
                    }
                }
                grid[d][s] = pick.id
                uses[pick.id] = (uses[pick.id] ?: 0) + slot.portions
            }
        }

        // 3. Reheat slots copy their cook slot.
        val result = schedule.mapIndexed { d, day ->
            day.mapIndexed { s, slot ->
                if (slot.mode == RationSlotMode.REHEAT) {
                    val sourceIndex = schedule[slot.sourceDay].indexOfFirst { it.meal == slot.meal }
                    grid[slot.sourceDay][sourceIndex]!!
                } else {
                    grid[d][s] ?: error("Slot without dishes: ${slot.meal}")
                }
            }
        }
        return RationRepairResult(result, repaired, raised, limit)
    }
}
