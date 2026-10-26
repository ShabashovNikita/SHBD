package app.shbd.services.ai

import app.shbd.models.AIProvider
import app.shbd.services.grounding.GroundingTools
import app.shbd.ui.home.FoodAnalysisProgress
import okhttp3.OkHttpClient

/** Release stub — real tool loop is debug/test only. */
object GroundedToolLoop {
    const val MAX_TOOL_ROUNDS = 4

    fun systemPrompt(): String = ""

    data class LoopResult(
        val finalize: GroundingTools.FinalizePayload,
        val roundsUsed: Int,
        val tools: GroundingTools,
    )

    suspend fun run(
        client: OkHttpClient,
        provider: AIProvider,
        model: String,
        baseUrl: String,
        apiKey: String?,
        maxTokens: Int,
        tools: GroundingTools,
        userMessage: String,
        imageBytesList: List<ByteArray>,
        onProgress: (FoodAnalysisProgress) -> Unit = {},
    ): LoopResult {
        throw UnsupportedOperationException("GroundedToolLoop unavailable in release")
    }
}
