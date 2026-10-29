package app.shbd.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/*
 * Built-in product and recipe catalog of the weekly menu (assets/ration/catalog.json).
 * Tags reuse the survey enums from RationSurvey.kt; names are Russian in every
 * UI language. Prices are average Russian retail, approximate.
 */

/** Store aisle for the grouped shopping list. */
enum class ShopAisle { PROTEIN, DAIRY, VEG, FRUIT, GRAIN, OTHER }

@Serializable
data class RationProduct(
    val id: String,
    val name: String,
    val aisle: ShopAisle,
    val priceRubPerKg: Double,
    /** Per 100 g. */
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val allergens: List<Allergen> = emptyList(),
    val dislikes: List<Dislike> = emptyList(),
    /** Usually at home (oil, sugar, stock): listed apart and not counted in the week cost. */
    val pantry: Boolean = false,
    /** "шт" for products bought by the piece (eggs); [unitGrams] is one piece. */
    val unit: String? = null,
    val unitGrams: Int? = null,
)

/** One ingredient of a recipe: grams per portion. Stored as `["egg", 130]`. */
@Serializable(with = RationItemSerializer::class)
data class RationItem(val productId: String, val grams: Int)

object RationItemSerializer : KSerializer<RationItem> {
    private val delegate = ListSerializer(JsonElement.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: RationItem) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("RationItem needs JSON")
        json.encodeJsonElement(JsonArray(listOf(JsonPrimitive(value.productId), JsonPrimitive(value.grams))))
    }

    override fun deserialize(decoder: Decoder): RationItem {
        val json = decoder as? JsonDecoder ?: throw SerializationException("RationItem needs JSON")
        val array = json.decodeJsonElement().jsonArray
        if (array.size != 2) throw SerializationException("RationItem must be [productId, grams]")
        return RationItem(array[0].jsonPrimitive.content, array[1].jsonPrimitive.int)
    }
}

@Serializable
data class RationRecipe(
    val id: String,
    val name: String,
    val meal: RationMeal,
    val cuisine: Cuisine = Cuisine.HOME,
    val minutes: Int,
    val tools: List<KitchenTool> = emptyList(),
    /** No cooking at all; true exactly when [tools] is empty. */
    val noCook: Boolean = false,
    val items: List<RationItem>,
)

@Serializable
data class RationCatalog(
    val version: Int = 1,
    val products: List<RationProduct>,
    val recipes: List<RationRecipe>,
) {
    private val productsById: Map<String, RationProduct> by lazy { products.associateBy { it.id } }
    private val recipesById: Map<String, RationRecipe> by lazy { recipes.associateBy { it.id } }

    fun product(id: String): RationProduct? = productsById[id]

    fun recipe(id: String): RationRecipe? = recipesById[id]
}
