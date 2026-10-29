package app.shbd.services.ration

import app.shbd.models.CookingTime
import app.shbd.models.RationCatalog
import app.shbd.models.RationMeal
import app.shbd.models.RationSurveyAnswers
import app.shbd.services.ration.RationTestFixtures.product
import app.shbd.services.ration.RationTestFixtures.recipe
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RationAiPlannerTest {
    private val catalog = RationCatalog(
        products = listOf(product("p")),
        recipes = (1..40).map { recipe("b$it", RationMeal.BREAKFAST, listOf("p" to 100)) } +
            (1..10).map { recipe("d$it", RationMeal.DINNER, listOf("p" to 200)) } +
            recipe("forbidden", RationMeal.DINNER, listOf("p" to 100)),
    )
    private val monday = LocalDate.of(2026, 11, 2)
    private val answers = RationSurveyAnswers(meals = setOf(RationMeal.BREAKFAST, RationMeal.DINNER), cookDays = emptySet())
    private val allowed = mapOf(
        RationMeal.BREAKFAST to catalog.recipes.filter { it.meal == RationMeal.BREAKFAST },
        RationMeal.DINNER to catalog.recipes.filter { it.meal == RationMeal.DINNER && it.id != "forbidden" },
    )

    private fun request(seed: Long = 7L, language: String? = "Russian") = RationPlannerRequest(
        catalog = catalog,
        allowed = allowed,
        schedule = RationCookSchedule.build(monday, answers),
        dates = RationCookSchedule.dates(monday),
        calorieGoal = 1_800,
        budgetRub = null,
        repeatLimit = 2,
        cookingTime = CookingTime.NORMAL,
        seed = seed,
        responseLanguage = language,
    )

    @Test
    fun prompt_onlyAllowedIds_atMost25PerMeal() {
        val prompt = RationAiPlanner.buildPrompt(request())
        assertFalse(prompt.contains("forbidden"))
        val breakfastLines = prompt.lines().count { Regex("^b\\d+ \\|").containsMatchIn(it) }
        assertEquals(RationAiPlanner.MAX_CANDIDATES, breakfastLines)
        assertEquals(10, prompt.lines().count { Regex("^d\\d+ \\|").containsMatchIn(it) })
        assertTrue(prompt.contains("1800 kcal"))
        assertTrue(prompt.contains("unlimited"))
        assertTrue(prompt.contains("in Russian"))
        // Same seed → same prompt.
        assertEquals(prompt, RationAiPlanner.buildPrompt(request()))
    }

    @Test
    fun parse_jsonInsideProse_caseInsensitiveKeys() {
        val text = "Here is the menu:\n```json\n{\"days\":[{\"day\":1,\"breakfast\":\"b1\",\"DINNER\":\"d1\",\"extra\":1}," +
            "{\"day\":2,\"Breakfast\":\"b2\"}],\"comment\":\"Хорошо\"}\n```\nEnjoy!"
        val answer = RationAiPlanner.parse(text)!!
        assertEquals(mapOf(RationMeal.BREAKFAST to "b1", RationMeal.DINNER to "d1"), answer.days[0])
        assertEquals(mapOf(RationMeal.BREAKFAST to "b2"), answer.days[1])
        assertTrue(answer.days[2].isEmpty())
        assertEquals("Хорошо", answer.comment)
    }

    @Test
    fun parse_noJson_isNull() {
        assertNull(RationAiPlanner.parse("Sorry, I cannot help"))
        assertNull(RationAiPlanner.parse("{\"menu\":[]}"))
    }

    @Test
    fun commentIsCappedAt300() {
        val answer = RationAiPlanner.parse("{\"days\":[],\"comment\":\"${"a".repeat(500)}\"}")!!
        assertEquals(300, answer.comment.length)
    }

    @Test
    fun badAnswerTwice_retriesOnceWithSuffix_thenBadAnswer() = runBlocking {
        val prompts = mutableListOf<String>()
        val planner = RationAiPlanner { prompts += it; "no json here" }
        try {
            planner.plan(request())
            fail("expected BadAnswer")
        } catch (e: RationPlanError.BadAnswer) {
            // expected
        }
        assertEquals(2, prompts.size)
        assertTrue(prompts[1].endsWith(RationAiPlanner.RETRY_SUFFIX))
    }

    @Test
    fun secondAttemptSucceeds() = runBlocking {
        var calls = 0
        val planner = RationAiPlanner { if (++calls == 1) "oops" else "{\"days\":[{\"day\":1,\"BREAKFAST\":\"b1\"}],\"comment\":\"ok\"}" }
        val answer = planner.plan(request())
        assertEquals(2, calls)
        assertEquals("b1", answer.days[0][RationMeal.BREAKFAST])
    }
}
