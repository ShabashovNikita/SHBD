package app.shbd.services.ration

import app.shbd.models.Allergen
import app.shbd.models.CookingTime
import app.shbd.models.RationMeal
import app.shbd.models.RationPlan
import app.shbd.models.RationSurveyAnswers
import app.shbd.services.ai.AiError
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Whole pipeline on the real catalog with a fake model and a fake store. */
class RationMenuServiceTest {
    private val catalog = RationTestFixtures.realCatalog
    private val monday = LocalDate.of(2026, 11, 2)
    private val saved = mutableListOf<RationPlan>()
    private val prompts = mutableListOf<String>()

    private fun service(reply: (String) -> String) = RationMenuService(
        catalog = { catalog },
        model = { prompts += it; reply(it) },
        save = { saved += it },
        responseLanguage = { "Russian" },
        now = { 1_000L },
    )

    /** A "model" that answers with the first candidate id of each meal on every day. */
    private val lazyModel: (String) -> String = { prompt ->
        val firstIds = mutableMapOf<String, String>()
        var meal: String? = null
        for (line in prompt.lines()) {
            if (line.endsWith(":") && RationMeal.entries.any { it.name == line.dropLast(1) }) meal = line.dropLast(1)
            else if (meal != null && line.contains(" | ")) firstIds.putIfAbsent(meal, line.substringBefore(" | "))
        }
        val day = firstIds.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
        "{\"days\":[" + (1..7).joinToString(",") { "{\"day\":$it,$day}" } + "],\"comment\":\"Меню готово\"}"
    }

    @Test
    fun success_savesPlanWithSevenDaysAndOnlySelectedMeals() = runBlocking {
        val answers = RationSurveyAnswers(
            meals = setOf(RationMeal.BREAKFAST, RationMeal.DINNER),
            allergens = setOf(Allergen.NUTS),
            stopFoods = listOf("грибы"),
            cookingTime = CookingTime.QUICK,
            cookDays = setOf(DayOfWeek.WEDNESDAY),
            repeatLimit = 2,
        )
        val plan = service(lazyModel).build(answers, 1_800, monday)
        assertEquals(listOf(plan), saved)
        assertEquals(7, plan.days.size)
        assertEquals("2026-11-02", plan.startDate)
        assertTrue(plan.days.all { d -> d.slots.map { it.meal } == listOf(RationMeal.BREAKFAST, RationMeal.DINNER) })
        assertEquals("Меню готово", plan.comment)
        assertEquals(1_000L, plan.createdAt)
        // Hard rules hold for every dish, whatever the model said.
        for (slot in plan.days.flatMap { it.slots }) {
            val recipe = catalog.recipe(slot.recipeId)!!
            assertTrue(recipe.minutes <= 20)
            assertTrue(recipe.items.none { catalog.product(it.productId)!!.allergens.contains(Allergen.NUTS) })
            assertTrue(recipe.items.none { it.productId == "mush" })
        }
        // The lazy model repeats one dish all week: the engine repaired the extra slots.
        assertTrue(plan.repairedSlots > 0)
        assertTrue(prompts.single().contains("in Russian"))
    }

    @Test
    fun noDishesForMeal_failsWithoutAskingModel() = runBlocking {
        val answers = RationSurveyAnswers(meals = setOf(RationMeal.DINNER), stopFoods = listOf("а", "о", "е", "и", "у"))
        try {
            service(lazyModel).build(answers, 1_800, monday)
            fail("expected NoDishes")
        } catch (e: RationPlanError.NoDishes) {
            assertEquals(setOf(RationMeal.DINNER), e.meals)
        }
        assertTrue(prompts.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun modelError_doesNotSave() = runBlocking {
        try {
            service { throw AiError.Timeout }.build(RationSurveyAnswers(), 1_800, monday)
            fail("expected AiError")
        } catch (e: AiError.Timeout) {
            // expected
        }
        assertTrue(saved.isEmpty())
    }

    @Test
    fun badAnswer_doesNotSave() = runBlocking {
        try {
            service { "nothing" }.build(RationSurveyAnswers(), 1_800, monday)
            fail("expected BadAnswer")
        } catch (e: RationPlanError.BadAnswer) {
            // expected
        }
        assertTrue(saved.isEmpty())
    }

    @Test
    fun answersHash_isStable() {
        val a = RationSurveyAnswers(allergens = setOf(Allergen.NUTS))
        assertEquals(RationMenuService.answersHash(a), RationMenuService.answersHash(a.copy()))
        assertTrue(RationMenuService.answersHash(a) != RationMenuService.answersHash(RationSurveyAnswers()))
    }
}
