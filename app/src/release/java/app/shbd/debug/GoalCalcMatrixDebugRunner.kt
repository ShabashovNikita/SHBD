package app.shbd.debug

import app.shbd.AppContainer

/** Release stub — the goal matrix is debug-only (model is debugImplementation). */
internal object GoalCalcMatrixDebugRunner {
    suspend fun run(container: AppContainer, scenarios: String, repeatCount: Int = 1, tier: String = "auto", provider: String = "on_device", model: String = "") = Unit
}
