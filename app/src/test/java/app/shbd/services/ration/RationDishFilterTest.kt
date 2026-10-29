package app.shbd.services.ration

import app.shbd.models.Allergen
import app.shbd.models.CookingTime
import app.shbd.models.Cuisine
import app.shbd.models.Dislike
import app.shbd.models.KitchenTool
import app.shbd.models.RationCatalog
import app.shbd.models.RationMeal
import app.shbd.models.RationSurveyAnswers
import app.shbd.services.ration.RationTestFixtures.product
import app.shbd.services.ration.RationTestFixtures.recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RationDishFilterTest {
    private val catalog = RationCatalog(
        products = listOf(
            product("nuts", "Орехи грецкие", allergens = listOf(Allergen.NUTS)),
            product("pork", "Свинина", dislikes = listOf(Dislike.PORK)),
            product("celery", "Сельдерей стеблевой"),
            product("rice", "Рис"),
            product("chicken", "Курица"),
        ),
        recipes = listOf(
            recipe("oat_nuts", RationMeal.BREAKFAST, listOf("nuts" to 20)),
            recipe("pork_rice", RationMeal.LUNCH, listOf("pork" to 100, "rice" to 60)),
            recipe("celery_soup", RationMeal.LUNCH, listOf("chicken" to 80), name = "Суп с Сельдереем"),
            recipe("celery_salad", RationMeal.DINNER, listOf("celery" to 80), tools = emptyList(), minutes = 5),
            recipe("chicken_rice", RationMeal.DINNER, listOf("chicken" to 120, "rice" to 60), minutes = 15),
            recipe("chicken_oven", RationMeal.DINNER, listOf("chicken" to 120), tools = listOf(KitchenTool.OVEN), minutes = 40),
            recipe("long_stew", RationMeal.DINNER, listOf("chicken" to 120), minutes = 90),
            recipe("pasta_it", RationMeal.DINNER, listOf("rice" to 80), cuisine = Cuisine.ITALIAN, minutes = 25),
            recipe("snack", RationMeal.SNACK, listOf("rice" to 30), tools = emptyList(), minutes = 3),
        ),
    )

    private val all = RationSurveyAnswers(
        meals = RationMeal.entries.toSet(),
        cookingTime = CookingTime.LOVE,
        kitchen = KitchenTool.entries.toSet(),
    )

    private fun ids(answers: RationSurveyAnswers): Set<String> =
        RationDishFilter.allowed(catalog, answers).allowed.values.flatten().map { it.id }.toSet()

    @Test
    fun nutAllergy_removesNutDishes() {
        val ids = ids(all.copy(allergens = setOf(Allergen.NUTS)))
        assertFalse("oat_nuts" in ids)
        assertTrue("chicken_rice" in ids)
    }

    @Test
    fun dislikePork_removesPorkDishes() {
        assertFalse("pork_rice" in ids(all.copy(dislikes = setOf(Dislike.PORK))))
    }

    @Test
    fun stopList_matchesDishAndProductNames_ignoringCaseAndEndings() {
        val ids = ids(all.copy(stopFoods = listOf("сельдерей")))
        assertFalse("celery_soup" in ids)
        assertFalse("celery_salad" in ids)
        assertTrue("chicken_rice" in ids)
        // «ё» and «е» are the same letter for the stop list.
        assertEquals("елка", RationDishFilter.normalize("Ёлка"))
    }

    @Test
    fun quick_upTo20Minutes_normal_upTo60() {
        val quick = RationDishFilter.allowed(catalog, all.copy(cookingTime = CookingTime.QUICK)).allowed.values.flatten()
        assertTrue(quick.all { it.minutes <= 20 })
        val normal = ids(all.copy(cookingTime = CookingTime.NORMAL))
        assertTrue("chicken_oven" in normal)
        assertFalse("long_stew" in normal)
        assertTrue("long_stew" in ids(all))
    }

    @Test
    fun noCooking_orNoTools_onlyNoCookDishes() {
        val none = RationDishFilter.allowed(catalog, all.copy(cookingTime = CookingTime.NONE)).allowed.values.flatten()
        assertTrue(none.isNotEmpty() && none.all { it.noCook })
        val noTools = RationDishFilter.allowed(catalog, all.copy(kitchen = emptySet())).allowed.values.flatten()
        assertTrue(noTools.all { it.noCook })
    }

    @Test
    fun noOven_noOvenDishes() {
        val ids = ids(all.copy(kitchen = setOf(KitchenTool.STOVE, KitchenTool.MICROWAVE)))
        assertFalse("chicken_oven" in ids)
        assertTrue("chicken_rice" in ids)
    }

    @Test
    fun italian_addsHomeWhenFewerThanSeven() {
        val dinner = RationDishFilter.allowed(catalog, all.copy(cuisines = setOf(Cuisine.ITALIAN))).allowed[RationMeal.DINNER].orEmpty()
        assertTrue(dinner.any { it.cuisine == Cuisine.ITALIAN })
        assertTrue(dinner.all { it.cuisine == Cuisine.ITALIAN || it.cuisine == Cuisine.HOME })
        assertTrue(dinner.any { it.cuisine == Cuisine.HOME })
    }

    @Test
    fun anyCuisine_keepsAll() {
        val dinner = RationDishFilter.allowed(catalog, all).allowed[RationMeal.DINNER].orEmpty()
        assertEquals(5, dinner.size)
    }

    @Test
    fun unselectedMealsAreAbsent_emptyMealsReported() {
        val result = RationDishFilter.allowed(catalog, all.copy(meals = setOf(RationMeal.BREAKFAST, RationMeal.DINNER), allergens = setOf(Allergen.NUTS)))
        assertEquals(setOf(RationMeal.BREAKFAST, RationMeal.DINNER), result.allowed.keys)
        assertEquals(setOf(RationMeal.BREAKFAST), result.emptyMeals)
    }

    @Test
    fun realCatalog_italianNormal_italianOrHomeOnly() {
        val answers = RationSurveyAnswers(cuisines = setOf(Cuisine.ITALIAN))
        val allowed = RationDishFilter.allowed(RationTestFixtures.realCatalog, answers).allowed
        val main = allowed[RationMeal.LUNCH].orEmpty() + allowed[RationMeal.DINNER].orEmpty()
        assertTrue(main.any { it.cuisine == Cuisine.ITALIAN })
        assertTrue(allowed.values.flatten().all { it.cuisine == Cuisine.ITALIAN || it.cuisine == Cuisine.HOME })
    }

    @Test
    fun realCatalog_mushroomStopWordCatchesChampignons() {
        val answers = RationSurveyAnswers(stopFoods = listOf("грибы"))
        val catalog = RationTestFixtures.realCatalog
        val allowed = RationDishFilter.allowed(catalog, answers).allowed.values.flatten()
        assertTrue(allowed.none { r -> r.items.any { it.productId == "mush" } })
    }
}
