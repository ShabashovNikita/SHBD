package app.shbd.services.ration

import app.shbd.models.RationCatalog
import app.shbd.models.RationRecipe

/** Nutrition and price of one portion (factor 1). Pantry products are free. */
data class RationPortion(
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val costRub: Double,
)

object RationRecipeMath {
    fun portion(recipe: RationRecipe, catalog: RationCatalog): RationPortion {
        var kcal = 0.0
        var protein = 0.0
        var fat = 0.0
        var carbs = 0.0
        var cost = 0.0
        for (item in recipe.items) {
            val p = catalog.product(item.productId) ?: continue
            val k = item.grams / 100.0
            kcal += p.kcal * k
            protein += p.protein * k
            fat += p.fat * k
            carbs += p.carbs * k
            if (!p.pantry) cost += p.priceRubPerKg * item.grams / 1000.0
        }
        return RationPortion(kcal, protein, fat, carbs, cost)
    }
}
