package app.shbd.services.ration

import app.shbd.models.RationMeal
import app.shbd.models.RationSlotMode
import app.shbd.models.RationSurveyAnswers
import java.time.LocalDate

/**
 * One slot of the week grid before dishes are chosen. A COOK slot cooks
 * [portions]; a REHEAT slot eats the dish cooked on [sourceDay].
 */
data class RationScheduledSlot(
    val day: Int,
    val meal: RationMeal,
    val mode: RationSlotMode,
    val portions: Int,
    val sourceDay: Int = day,
)

/** Batch cooking: fixed before the model is asked, so it only picks COOK slots. */
object RationCookSchedule {
    const val DAYS = 7
    val BATCH_MEALS = setOf(RationMeal.LUNCH, RationMeal.DINNER)

    fun dates(start: LocalDate): List<LocalDate> = (0 until DAYS).map { start.plusDays(it.toLong()) }

    /** [DAYS] lists of slots in meal order, starting with [start]. */
    fun build(start: LocalDate, answers: RationSurveyAnswers): List<List<RationScheduledSlot>> {
        val meals = RationMeal.entries.filter { it in answers.meals }
        val dates = dates(start)
        val batching = answers.cooks && answers.cookDays.isNotEmpty()
        val limit = answers.repeatLimit.coerceAtLeast(1)
        // Days covered by an earlier cook day: day -> source day.
        val reheatOf = mutableMapOf<Int, Int>()
        val batchPortions = mutableMapOf<Int, Int>()
        if (batching) {
            for (day in 0 until DAYS) {
                if (day in reheatOf || dates[day].dayOfWeek !in answers.cookDays) continue
                val untilNext = (1..DAYS).first { dates[day].plusDays(it.toLong()).dayOfWeek in answers.cookDays }
                val n = minOf(untilNext, limit, DAYS - day)
                batchPortions[day] = n
                for (d in day + 1 until day + n) reheatOf[d] = day
            }
        }
        return (0 until DAYS).map { day ->
            meals.map { meal ->
                val source = reheatOf[day]
                when {
                    meal !in BATCH_MEALS || !batching -> RationScheduledSlot(day, meal, RationSlotMode.COOK, 1)
                    source != null -> RationScheduledSlot(day, meal, RationSlotMode.REHEAT, 0, source)
                    else -> RationScheduledSlot(day, meal, RationSlotMode.COOK, batchPortions[day] ?: 1)
                }
            }
        }
    }
}
