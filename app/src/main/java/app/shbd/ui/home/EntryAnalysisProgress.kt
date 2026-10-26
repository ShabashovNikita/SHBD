package app.shbd.ui.home

import app.shbd.services.ai.FoodAnalysis
import app.shbd.services.ai.PartialFoodAnalysis
import app.shbd.services.grounding.GroundedFoodEntryService

enum class EntryAnalysisPhase {
    Preparing,
    /** Still-image barcode decode + Open Food Facts soft context (photo AI). */
    LookingUpBarcode,
    CallingAi,
    Parsing,
    /** Grounded entry: model recognition of components (no nutrients yet). */
    Recognizing,
    SearchingHistory,
    SearchingUsda,
    Resolving,
}

/** How a preview was produced — streaming chunks vs final parse. */
enum class AnalysisPreviewSource {
    Streaming,
    FinalParse,
}

sealed class FoodAnalysisProgress {
    data class Phase(val phase: EntryAnalysisPhase) : FoodAnalysisProgress()

    /**
     * Validated fields observed while the provider response is still arriving.
     * Only emitted when streaming is available; never invents incomplete values.
     */
    data class Partial(
        val partial: PartialFoodAnalysis,
        val source: AnalysisPreviewSource = AnalysisPreviewSource.Streaming,
    ) : FoodAnalysisProgress()

    /** Primary parse done; units may still be pending. */
    data class Parsed(val analysis: FoodAnalysis, val unitsPending: Boolean) : FoodAnalysisProgress()

    /** Final result after any unit fallback. */
    data class Complete(val analysis: FoodAnalysis) : FoodAnalysisProgress()
}

/** Pending grounded-entry state waiting for candidate / portion confirmation. */
data class PendingGroundedReview(
    val result: GroundedFoodEntryService.GroundedResult,
    val description: String?,
    val imageBytes: ByteArray?,
)
