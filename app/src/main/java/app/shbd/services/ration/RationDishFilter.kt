package app.shbd.services.ration

import app.shbd.models.CookingTime
import app.shbd.models.Cuisine
import app.shbd.models.RationCatalog
import app.shbd.models.RationMeal
import app.shbd.models.RationRecipe
import app.shbd.models.RationSurveyAnswers
import java.util.Locale

/** Dishes the survey allows, per selected meal; [emptyMeals] are selected meals left with none. */
data class RationFilterResult(
    val allowed: Map<RationMeal, List<RationRecipe>>,
    val emptyMeals: Set<RationMeal>,
)

/**
 * Hard survey rules. Nothing — the AI model included — may put a dish into the
 * menu that fails them: allergens, dislikes, the stop list, cooking time,
 * kitchen tools; then the cuisine choice narrows the list.
 */
object RationDishFilter {
    const val MAX_QUICK_MINUTES = 20
    const val MAX_NORMAL_MINUTES = 60

    /** Below this many dishes of the chosen cuisines a meal also gets HOME dishes. */
    const val MIN_CUISINE_DISHES = 7

    fun allowed(catalog: RationCatalog, answers: RationSurveyAnswers): RationFilterResult {
        val stopWords = stopStems(answers.stopFoods)
        val allowed = RationMeal.entries.filter { it in answers.meals }.associateWith { meal ->
            val passing = catalog.recipes.filter { it.meal == meal && passesHardRules(it, catalog, answers, stopWords) }
            byCuisine(passing, answers.cuisines)
        }
        return RationFilterResult(allowed, allowed.filterValues { it.isEmpty() }.keys)
    }

    fun passesHardRules(
        recipe: RationRecipe,
        catalog: RationCatalog,
        answers: RationSurveyAnswers,
        stopWords: List<String> = stopStems(answers.stopFoods),
    ): Boolean {
        val products = recipe.items.map { catalog.product(it.productId) ?: return false }
        if (products.any { p -> p.allergens.any { it in answers.allergens } }) return false
        if (products.any { p -> p.dislikes.any { it in answers.dislikes } }) return false
        if (stopWords.isNotEmpty()) {
            val names = listOf(normalize(recipe.name)) + products.map { normalize(it.name) }
            if (stopWords.any { word -> names.any { word in it } }) return false
        }
        val timeOk = when (answers.cookingTime) {
            CookingTime.NONE -> recipe.noCook
            CookingTime.QUICK -> recipe.minutes <= MAX_QUICK_MINUTES
            CookingTime.NORMAL -> recipe.minutes <= MAX_NORMAL_MINUTES
            CookingTime.LOVE -> true
        }
        if (!timeOk) return false
        return if (answers.kitchen.isEmpty()) recipe.noCook else answers.kitchen.containsAll(recipe.tools)
    }

    private fun byCuisine(recipes: List<RationRecipe>, cuisines: Set<Cuisine>): List<RationRecipe> {
        if (Cuisine.ANY in cuisines || cuisines.isEmpty()) return recipes
        val chosen = recipes.filter { it.cuisine in cuisines }
        if (chosen.size >= MIN_CUISINE_DISHES || Cuisine.HOME in cuisines) return chosen
        return chosen + recipes.filter { it.cuisine == Cuisine.HOME }
    }

    /**
     * Stop-list words cut to a stem: up to two trailing vowels / «й» / «ь» go, so
     * «сельдерей» also finds «с сельдереем» and «грибы» finds «грибами».
     */
    fun stopStems(stopFoods: List<String>): List<String> = stopFoods.map { food ->
        var stem = normalize(food)
        repeat(2) { if (stem.length > 3 && stem.last() in STEM_ENDINGS) stem = stem.dropLast(1) }
        stem
    }.filter { it.isNotEmpty() }

    private const val STEM_ENDINGS = "аеиоуыэюяйь"

    /** Case- and «ё»-insensitive form for the stop-list substring match. */
    fun normalize(text: String): String = text.trim().lowercase(Locale.ROOT).replace('ё', 'е')
}
