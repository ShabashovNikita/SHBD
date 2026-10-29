package app.shbd.services.ration

import app.shbd.models.RationCatalog
import app.shbd.models.RationRecipe
import app.shbd.models.RationShoppingItem
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Shopping list of the week; [pantry] items are listed apart and cost nothing. */
data class RationShopping(
    val items: List<RationShoppingItem>,
    val pantry: List<RationShoppingItem>,
) {
    val costRub: Int get() = items.sumOf { it.costRub }
}

object RationShoppingList {
    const val EGG_ID = "egg"

    /**
     * [eaten]: every eaten portion as (recipe, portion factor of its day).
     * Grams = recipe grams × factor, summed per product, rounded up to a pack.
     */
    fun build(catalog: RationCatalog, eaten: List<Pair<RationRecipe, Double>>): RationShopping {
        val grams = linkedMapOf<String, Double>()
        for ((recipe, factor) in eaten) {
            for (item in recipe.items) {
                grams[item.productId] = (grams[item.productId] ?: 0.0) + item.grams * factor
            }
        }
        val rows = grams.mapNotNull { (id, g) ->
            val product = catalog.product(id) ?: return@mapNotNull null
            val pieceGrams = product.unitGrams?.takeIf { product.unit == "шт" && it > 0 }
            val (rounded, pieces) = if (pieceGrams != null) {
                var count = ceil(g / pieceGrams).toInt().coerceAtLeast(1)
                if (id == EGG_ID) count = roundUp(count, 10)
                count * pieceGrams to count
            } else {
                roundGrams(g) to null
            }
            RationShoppingItem(
                productId = id,
                name = product.name,
                aisle = product.aisle,
                grams = rounded,
                pieces = pieces,
                costRub = if (product.pantry) 0 else (product.priceRubPerKg * rounded / 1000.0).roundToInt(),
            ) to product.pantry
        }
        val order = compareBy<RationShoppingItem>({ it.aisle.ordinal }, { it.name })
        return RationShopping(
            items = rows.filter { !it.second }.map { it.first }.sortedWith(order),
            pantry = rows.filter { it.second }.map { it.first }.sortedWith(order),
        )
    }

    /** ≥ 1 kg → 100 g; ≤ 120 g → 10 g; otherwise 50 g; always up. */
    fun roundGrams(grams: Double): Int {
        val g = ceil(grams - 1e-6).toInt().coerceAtLeast(1)
        return when {
            g >= 1000 -> roundUp(g, 100)
            g <= 120 -> roundUp(g, 10)
            else -> roundUp(g, 50)
        }
    }

    private fun roundUp(value: Int, step: Int): Int = ((value + step - 1) / step) * step
}
