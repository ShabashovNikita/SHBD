package app.shbd.services.ration

import app.shbd.models.RationCatalog
import app.shbd.models.RationMeal
import app.shbd.models.ShopAisle
import app.shbd.services.ration.RationTestFixtures.product
import app.shbd.services.ration.RationTestFixtures.recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RationShoppingListTest {
    private val catalog = RationCatalog(
        products = listOf(
            product("chicken_f", "Куриная грудка", ShopAisle.PROTEIN, price = 349.0),
            product("egg", "Яйцо куриное", ShopAisle.DAIRY, price = 129.0, unit = "шт", unitGrams = 55),
            product("rice", "Рис", ShopAisle.GRAIN, price = 129.0),
            product("oil", "Масло растительное", price = 149.0, pantry = true),
        ),
        recipes = listOf(
            recipe("a", RationMeal.LUNCH, listOf("chicken_f" to 150, "rice" to 60, "oil" to 8)),
            recipe("b", RationMeal.DINNER, listOf("chicken_f" to 150, "egg" to 110)),
        ),
    )

    @Test
    fun productInTwoDishes_oneRowWithSum() {
        val list = RationShoppingList.build(catalog, listOf(catalog.recipe("a")!! to 1.0, catalog.recipe("b")!! to 1.0))
        val chicken = list.items.filter { it.productId == "chicken_f" }
        assertEquals(1, chicken.size)
        assertTrue(chicken[0].grams >= 300)
        assertEquals(ShopAisle.PROTEIN, chicken[0].aisle)
        assertEquals("Куриная грудка", chicken[0].name)
    }

    @Test
    fun rounding() {
        assertEquals(1_300, RationShoppingList.roundGrams(1_210.0))
        assertEquals(1_000, RationShoppingList.roundGrams(1_000.0))
        assertEquals(120, RationShoppingList.roundGrams(113.0))
        assertEquals(10, RationShoppingList.roundGrams(3.0))
        assertEquals(150, RationShoppingList.roundGrams(121.0))
        assertEquals(350, RationShoppingList.roundGrams(301.0))
    }

    @Test
    fun eggs_roundedToTen() {
        val list = RationShoppingList.build(catalog, listOf(catalog.recipe("b")!! to 3.0))
        val eggs = list.items.single { it.productId == "egg" }
        assertEquals(10, eggs.pieces)
        assertEquals(550, eggs.grams)
    }

    @Test
    fun pantry_separateAndFree() {
        val list = RationShoppingList.build(catalog, listOf(catalog.recipe("a")!! to 1.0))
        assertTrue(list.items.none { it.productId == "oil" })
        val oil = list.pantry.single()
        assertEquals("oil", oil.productId)
        assertEquals(0, oil.costRub)
        assertEquals(list.items.sumOf { it.costRub }, list.costRub)
    }

    @Test
    fun groupedByAisle_andCostFromRoundedGrams() {
        val list = RationShoppingList.build(catalog, listOf(catalog.recipe("a")!! to 1.0, catalog.recipe("b")!! to 1.0))
        assertEquals(list.items.sortedBy { it.aisle.ordinal }.map { it.aisle }, list.items.map { it.aisle })
        val chicken = list.items.single { it.productId == "chicken_f" }
        assertEquals(Math.round(349.0 * chicken.grams / 1000.0).toInt(), chicken.costRub)
    }

    @Test
    fun dayFactorsScaleGrams() {
        val list = RationShoppingList.build(catalog, listOf(catalog.recipe("a")!! to 1.5, catalog.recipe("a")!! to 1.5))
        // 60 g rice × (1.5 + 1.5) = 180 g → 200 g (50 g step).
        assertEquals(200, list.items.single { it.productId == "rice" }.grams)
    }
}
