package app.shbd.models

import java.time.DayOfWeek
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/*
 * Weekly meal-plan survey (Ration tab). Enums hold ids only; labels live in
 * ui/ration/RationSurveyLabels.kt. The answers are the input for the future
 * menu engine, which will prompt the active AI model (Coach / AI provider).
 */

enum class RationMeal { BREAKFAST, LUNCH, DINNER, SNACK }

enum class Allergen { NUTS, PEANUTS, GLUTEN, LACTOSE, EGGS, FISH, SEAFOOD, SOY, SESAME, CITRUS, HONEY }

enum class Dislike { MUSHROOMS, ONION, GARLIC, OLIVES, EGGPLANT, CILANTRO, SPICY, SUGAR, PORK, RED_MEAT, TOFU }

enum class CookingTime { NONE, QUICK, NORMAL, LOVE }

enum class KitchenTool { STOVE, OVEN, MICROWAVE, MULTICOOKER, AIR_FRYER, BLENDER, STEAMER, GRILL_PAN }

enum class Cuisine {
    ANY, HOME, EUROPEAN, ITALIAN, MEDITERRANEAN, ASIAN, JAPANESE, CAUCASIAN, CENTRAL_ASIAN,
    MEXICAN, MIDDLE_EASTERN, INDIAN,
}

enum class BudgetLevel { VERY_ECONOMY, ECONOMY, BALANCED, COMFORT, FREE }

enum class RationSurveyStep { INTRO, MEALS, BUDGET, RESTRICTIONS, COOKING, KITCHEN, CUISINES, DELIVERY, SUMMARY }

/**
 * Enum sets are stored as name lists. `coerceInputValues` does not reach
 * collection elements, so an unknown name (an option a later release removed)
 * is dropped here instead of failing the whole survey blob.
 */
abstract class LenientEnumSetSerializer<E : Enum<E>>(private val values: List<E>) : KSerializer<Set<E>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Set<E>) =
        encoder.encodeSerializableValue(delegate, value.map { it.name })

    override fun deserialize(decoder: Decoder): Set<E> {
        val names = if (decoder is JsonDecoder) {
            decoder.decodeJsonElement().jsonArray.mapNotNull { (it as? JsonPrimitive)?.content }
        } else {
            decoder.decodeSerializableValue(delegate)
        }
        return names.mapNotNullTo(LinkedHashSet()) { name -> values.firstOrNull { it.name == name } }
    }
}

object RationMealSetSerializer : LenientEnumSetSerializer<RationMeal>(RationMeal.entries)
object AllergenSetSerializer : LenientEnumSetSerializer<Allergen>(Allergen.entries)
object DislikeSetSerializer : LenientEnumSetSerializer<Dislike>(Dislike.entries)
object DayOfWeekSetSerializer : LenientEnumSetSerializer<DayOfWeek>(DayOfWeek.entries)
object KitchenToolSetSerializer : LenientEnumSetSerializer<KitchenTool>(KitchenTool.entries)
object CuisineSetSerializer : LenientEnumSetSerializer<Cuisine>(Cuisine.entries)

@Serializable
data class RationSurveyAnswers(
    @Serializable(with = RationMealSetSerializer::class)
    val meals: Set<RationMeal> = setOf(RationMeal.BREAKFAST, RationMeal.LUNCH, RationMeal.DINNER),
    /** Slider amount; kept while [budgetUnlimited] is on so switching it off restores it. */
    val weeklyBudgetRub: Int = DEFAULT_BUDGET_RUB,
    val budgetUnlimited: Boolean = false,
    @Serializable(with = AllergenSetSerializer::class)
    val allergens: Set<Allergen> = emptySet(),
    @Serializable(with = DislikeSetSerializer::class)
    val dislikes: Set<Dislike> = emptySet(),
    val stopFoods: List<String> = emptyList(),
    val cookingTime: CookingTime = CookingTime.NORMAL,
    /** Times one dish may appear per week; kept while cookingTime is NONE. */
    val repeatLimit: Int = 2,
    @Serializable(with = DayOfWeekSetSerializer::class)
    val cookDays: Set<DayOfWeek> = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
    @Serializable(with = KitchenToolSetSerializer::class)
    val kitchen: Set<KitchenTool> = setOf(KitchenTool.STOVE, KitchenTool.OVEN, KitchenTool.MICROWAVE),
    @Serializable(with = CuisineSetSerializer::class)
    val cuisines: Set<Cuisine> = setOf(Cuisine.ANY),
    /** Remembered only; delivery suggestions arrive in a later change. */
    val deliveryWanted: Boolean = false,
) {
    val cooks: Boolean get() = cookingTime != CookingTime.NONE

    /** Clamps every field into its allowed range (used on read and before save). */
    fun normalized(): RationSurveyAnswers = copy(
        weeklyBudgetRub = normalizeBudget(weeklyBudgetRub),
        stopFoods = stopFoods.fold(emptyList()) { acc, food -> addStopFood(acc, food) },
        repeatLimit = repeatLimit.coerceIn(REPEAT_RANGE),
        cuisines = when {
            cuisines.isEmpty() -> setOf(Cuisine.ANY)
            Cuisine.ANY in cuisines && cuisines.size > 1 -> cuisines - Cuisine.ANY
            else -> cuisines
        },
    )

    companion object {
        const val MIN_BUDGET_RUB = 1_000
        const val MAX_BUDGET_RUB = 15_000
        const val BUDGET_STEP_RUB = 100
        const val DEFAULT_BUDGET_RUB = 3_000
        const val STOP_FOOD_MAX_LENGTH = 40
        val REPEAT_RANGE = 1..3

        fun normalizeBudget(rub: Int): Int {
            val clamped = rub.coerceIn(MIN_BUDGET_RUB, MAX_BUDGET_RUB)
            return ((clamped + BUDGET_STEP_RUB / 2) / BUDGET_STEP_RUB) * BUDGET_STEP_RUB
        }

        fun budgetLevel(rub: Int): BudgetLevel = when {
            rub < 1_500 -> BudgetLevel.VERY_ECONOMY
            rub < 2_600 -> BudgetLevel.ECONOMY
            rub < 4_000 -> BudgetLevel.BALANCED
            rub < 6_000 -> BudgetLevel.COMFORT
            else -> BudgetLevel.FREE
        }

        /** Trims, caps at [STOP_FOOD_MAX_LENGTH]; blank or a case-insensitive repeat is not added. */
        fun addStopFood(list: List<String>, text: String): List<String> {
            val food = text.trim().take(STOP_FOOD_MAX_LENGTH).trim()
            if (food.isEmpty() || list.any { it.equals(food, ignoreCase = true) }) return list
            return list + food
        }

        /** "Any" excludes concrete cuisines; clearing the last one selects "Any" again. */
        fun toggleCuisine(set: Set<Cuisine>, cuisine: Cuisine): Set<Cuisine> = when {
            cuisine == Cuisine.ANY -> setOf(Cuisine.ANY)
            cuisine in set -> (set - cuisine).ifEmpty { setOf(Cuisine.ANY) }
            else -> set - Cuisine.ANY + cuisine
        }
    }
}

/** Persisted survey: answers plus the open step; [completed] stays true while editing answers. */
@Serializable
data class RationSurveyState(
    val answers: RationSurveyAnswers = RationSurveyAnswers(),
    val step: RationSurveyStep = RationSurveyStep.INTRO,
    val completed: Boolean = false,
) {
    fun normalized(): RationSurveyState {
        // A finished survey with a lost step lands on the summary, an unfinished one never does.
        val fixedStep = when {
            step == RationSurveyStep.SUMMARY && !completed -> RationSurveyStep.INTRO
            step == RationSurveyStep.INTRO && completed -> RationSurveyStep.SUMMARY
            else -> step
        }
        return copy(answers = answers.normalized(), step = fixedStep)
    }

    fun start(): RationSurveyState = copy(step = RationSurveyStep.MEALS)

    /** Next step, or the summary (survey completed) after the last question. */
    fun next(): RationSurveyState {
        if (!RationSurveySteps.canProceed(step, answers)) return this
        val target = RationSurveySteps.next(step, answers)
        return if (target == RationSurveyStep.SUMMARY) finish() else copy(step = target ?: step)
    }

    fun back(): RationSurveyState = RationSurveySteps.previous(step, answers)?.let { copy(step = it) } ?: this

    fun finish(): RationSurveyState = copy(step = RationSurveyStep.SUMMARY, completed = true)

    fun editAnswers(): RationSurveyState = copy(step = RationSurveyStep.MEALS)
}

/** "Step N of M" for a question step. */
data class RationStepPosition(val number: Int, val total: Int)

/** Pure step order: KITCHEN is skipped when the user does not cook. */
object RationSurveySteps {
    private val questions = listOf(
        RationSurveyStep.MEALS,
        RationSurveyStep.BUDGET,
        RationSurveyStep.RESTRICTIONS,
        RationSurveyStep.COOKING,
        RationSurveyStep.KITCHEN,
        RationSurveyStep.CUISINES,
        RationSurveyStep.DELIVERY,
    )

    fun visibleSteps(answers: RationSurveyAnswers): List<RationSurveyStep> =
        if (answers.cooks) questions else questions - RationSurveyStep.KITCHEN

    /** Computed from the current answers, so changing "Cooking" re-routes the very next tap. */
    fun next(step: RationSurveyStep, answers: RationSurveyAnswers): RationSurveyStep? = when (step) {
        RationSurveyStep.INTRO -> RationSurveyStep.MEALS
        RationSurveyStep.SUMMARY -> null
        else -> visibleSteps(answers).firstOrNull { it.ordinal > step.ordinal } ?: RationSurveyStep.SUMMARY
    }

    /** Previous question step; null on the first question, the intro and the summary. */
    fun previous(step: RationSurveyStep, answers: RationSurveyAnswers): RationSurveyStep? = when (step) {
        RationSurveyStep.INTRO, RationSurveyStep.SUMMARY -> null
        else -> visibleSteps(answers).lastOrNull { it.ordinal < step.ordinal }
    }

    fun position(step: RationSurveyStep, answers: RationSurveyAnswers): RationStepPosition? {
        val visible = visibleSteps(answers)
        val index = visible.indexOf(step)
        return if (index < 0) null else RationStepPosition(index + 1, visible.size)
    }

    fun isLast(step: RationSurveyStep, answers: RationSurveyAnswers): Boolean =
        visibleSteps(answers).last() == step

    fun canProceed(step: RationSurveyStep, answers: RationSurveyAnswers): Boolean = when (step) {
        RationSurveyStep.MEALS -> answers.meals.isNotEmpty()
        else -> true
    }
}
