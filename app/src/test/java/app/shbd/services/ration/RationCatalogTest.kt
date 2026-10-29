package app.shbd.services.ration

import app.shbd.models.Allergen
import app.shbd.models.Cuisine
import app.shbd.models.Dislike
import app.shbd.models.RationMeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Integrity of the shipped assets/ration/catalog.json. */
class RationCatalogTest {
    private val catalog = RationTestFixtures.realCatalog

    @Test
    fun loads_withExpectedSize() {
        assertTrue("products: ${catalog.products.size}", catalog.products.size >= 90)
        assertTrue("recipes: ${catalog.recipes.size}", catalog.recipes.size >= 130)
    }

    @Test
    fun idsAreUnique() {
        assertEquals(catalog.products.size, catalog.products.map { it.id }.toSet().size)
        assertEquals(catalog.recipes.size, catalog.recipes.map { it.id }.toSet().size)
    }

    @Test
    fun everyRecipeProductExists_andRecipesAreComplete() {
        for (r in catalog.recipes) {
            assertTrue(r.id, r.items.isNotEmpty())
            assertTrue(r.id, r.minutes > 0)
            assertTrue(r.id, r.name.isNotBlank())
            for (item in r.items) {
                assertTrue("${r.id} → ${item.productId}", catalog.product(item.productId) != null)
                assertTrue("${r.id} → ${item.productId}", item.grams > 0)
            }
            assertEquals("noCook ⇔ no tools: ${r.id}", r.tools.isEmpty(), r.noCook)
        }
    }

    @Test
    fun everyCuisineHasThreeLunchOrDinnerRecipes() {
        for (cuisine in Cuisine.entries - Cuisine.ANY) {
            val count = catalog.recipes.count { it.cuisine == cuisine && (it.meal == RationMeal.LUNCH || it.meal == RationMeal.DINNER) }
            assertTrue("$cuisine: $count", count >= 3)
        }
    }

    @Test
    fun everyMealHasRecipes() {
        for (meal in RationMeal.entries) assertTrue(meal.name, catalog.recipes.any { it.meal == meal })
    }

    @Test
    fun everyAllergenAndDislikeIsTagged() {
        for (a in Allergen.entries) assertTrue(a.name, catalog.products.any { a in it.allergens })
        for (d in Dislike.entries) assertTrue(d.name, catalog.products.any { d in it.dislikes })
    }

    @Test
    fun productsHavePricesAndNutrition() {
        for (p in catalog.products) {
            assertTrue(p.id, p.priceRubPerKg > 0)
            assertTrue(p.id, p.kcal >= 0)
            if (p.unit != null) assertTrue(p.id, (p.unitGrams ?: 0) > 0)
        }
    }
}
