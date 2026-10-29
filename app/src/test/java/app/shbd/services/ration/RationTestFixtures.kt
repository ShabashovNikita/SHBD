package app.shbd.services.ration

import app.shbd.models.Allergen
import app.shbd.models.Cuisine
import app.shbd.models.Dislike
import app.shbd.models.KitchenTool
import app.shbd.models.RationCatalog
import app.shbd.models.RationItem
import app.shbd.models.RationMeal
import app.shbd.models.RationProduct
import app.shbd.models.RationRecipe
import app.shbd.models.ShopAisle
import java.io.File

/** Small hand-made catalog for rule tests, plus the real asset for integration tests. */
internal object RationTestFixtures {
    fun product(
        id: String,
        name: String = id,
        aisle: ShopAisle = ShopAisle.OTHER,
        price: Double = 300.0,
        kcal: Double = 150.0,
        allergens: List<Allergen> = emptyList(),
        dislikes: List<Dislike> = emptyList(),
        pantry: Boolean = false,
        unit: String? = null,
        unitGrams: Int? = null,
    ) = RationProduct(id, name, aisle, price, kcal, 10.0, 5.0, 10.0, allergens, dislikes, pantry, unit, unitGrams)

    fun recipe(
        id: String,
        meal: RationMeal,
        items: List<Pair<String, Int>>,
        name: String = id,
        cuisine: Cuisine = Cuisine.HOME,
        minutes: Int = 30,
        tools: List<KitchenTool> = listOf(KitchenTool.STOVE),
    ) = RationRecipe(id, name, meal, cuisine, minutes, tools, tools.isEmpty(), items.map { RationItem(it.first, it.second) })

    /** The catalog shipped in the APK (unit tests run with the module dir as cwd). */
    val realCatalog: RationCatalog by lazy {
        val file = listOf(File("src/main/assets/ration/catalog.json"), File("app/src/main/assets/ration/catalog.json")).first { it.exists() }
        RationCatalogLoader.load(file.readText())
    }
}
