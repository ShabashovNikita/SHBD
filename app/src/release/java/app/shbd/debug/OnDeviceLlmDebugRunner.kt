package app.shbd.debug

import app.shbd.AppContainer

/** Release stub — LiteRT-LM is debugImplementation only. */
internal object OnDeviceLlmDebugRunner {
    suspend fun run(container: AppContainer, config: OnDeviceLlmDebugConfig) = Unit
}
