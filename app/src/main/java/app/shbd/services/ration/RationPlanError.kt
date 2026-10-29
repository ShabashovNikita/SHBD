package app.shbd.services.ration

import app.shbd.models.RationMeal

/** Why a menu was not built. AI transport errors arrive as [app.shbd.services.ai.AiError] instead. */
sealed class RationPlanError(message: String) : Exception(message) {
    /** The model twice answered without a readable JSON menu. */
    object BadAnswer : RationPlanError("The model did not return a menu")

    /** The survey leaves these selected meals without a single dish; the model is not asked. */
    class NoDishes(val meals: Set<RationMeal>) : RationPlanError("No dishes for $meals")
}
