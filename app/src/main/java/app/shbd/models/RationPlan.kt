package app.shbd.models

import kotlinx.serialization.Serializable

/*
 * Saved weekly menu (DataStore key "rationPlan"). Dish and product names are
 * copied in, so an old plan still shows after the catalog changes.
 */

enum class RationSlotMode { COOK, REHEAT }

@Serializable
data class RationPlanSlot(
    val meal: RationMeal,
    val recipeId: String,
    val recipeName: String,
    val mode: RationSlotMode = RationSlotMode.COOK,
    /** Portions cooked in this slot (batch cooking); 0 for a reheat slot. */
    val portions: Int = 1,
    /** One eaten portion, already scaled by the day's portion factor. */
    val kcal: Int = 0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val costRub: Int = 0,
)

@Serializable
data class RationPlanDay(
    /** ISO date (yyyy-MM-dd). */
    val date: String,
    val slots: List<RationPlanSlot> = emptyList(),
    /** Portion factor of this day: the day lands on the calorie goal, never above it. */
    val portionFactor: Double = 1.0,
) {
    val kcal: Int get() = slots.sumOf { it.kcal }
}

@Serializable
data class RationShoppingItem(
    val productId: String,
    val name: String,
    val aisle: ShopAisle = ShopAisle.OTHER,
    /** Rounded to a pack size. */
    val grams: Int,
    /** Pieces for products bought by the piece (eggs), else null. */
    val pieces: Int? = null,
    /** 0 for pantry items. */
    val costRub: Int = 0,
)

@Serializable
data class RationPlan(
    val createdAt: Long = 0L,
    /** ISO date of day 1. */
    val startDate: String = "",
    /** Hash of the survey answers the plan was built from. */
    val answersHash: Int = 0,
    val calorieGoal: Int = 0,
    /** No goal in the profile: [calorieGoal] is the 2 000 kcal default. */
    val calorieGoalAssumed: Boolean = false,
    /** Average of the day factors. */
    val portionFactor: Double = 1.0,
    val days: List<RationPlanDay> = emptyList(),
    val shopping: List<RationShoppingItem> = emptyList(),
    val pantry: List<RationShoppingItem> = emptyList(),
    val weekCostRub: Int = 0,
    /** Null when the budget is unlimited. */
    val budgetRub: Int? = null,
    val overBudgetRub: Int = 0,
    /** Too few allowed dishes: dishes repeat more often than the survey limit. */
    val repeatsRaised: Boolean = false,
    /** Slots the engine had to fill itself (bad / missing / forbidden model picks). */
    val repairedSlots: Int = 0,
    val comment: String = "",
)
