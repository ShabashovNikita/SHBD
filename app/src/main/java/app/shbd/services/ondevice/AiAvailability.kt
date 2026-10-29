package app.shbd.services.ondevice

import app.shbd.models.AIProvider

/**
 * Whether AI features can run right now on the active model (embedded or a
 * cloud provider). One source of truth for the Coach tab, Profile → AI and
 * the add-food menu. Device support for the embedded model is a separate
 * flag ([app.shbd.AppContainer.onDeviceSupported]): a cloud model works anywhere.
 */
enum class AiAvailability {
    /** The user turned AI features off in Profile → AI. */
    DISABLED,

    /** No model is usable: the embedded one is not downloaded, or the cloud one has no key / URL. */
    MODEL_MISSING,

    READY;

    /** AI entry points (photo / text analysis) are shown; tapping may still ask for the model. */
    val entryPointsVisible: Boolean get() = this != DISABLED
}

/**
 * Pure mapping, unit-tested; the live flow is [app.shbd.AppContainer.aiAvailability].
 * [remoteConfigured] matters only for a cloud [provider]; [downloadState] only for ON_DEVICE.
 */
fun aiAvailability(
    enabled: Boolean,
    provider: AIProvider,
    remoteConfigured: Boolean,
    downloadState: OnDeviceDownloadState,
): AiAvailability = when {
    !enabled -> AiAvailability.DISABLED
    provider == AIProvider.ON_DEVICE ->
        if (downloadState is OnDeviceDownloadState.Downloaded) AiAvailability.READY else AiAvailability.MODEL_MISSING
    remoteConfigured -> AiAvailability.READY
    else -> AiAvailability.MODEL_MISSING
}

/** A cloud provider can be called: it has a key when it needs one and a non-empty base URL. */
fun remoteConfigured(provider: AIProvider, apiKey: String?, customBaseUrl: String?): Boolean {
    if (provider == AIProvider.ON_DEVICE) return false
    if (provider.requiresApiKey && apiKey.isNullOrBlank()) return false
    val url = customBaseUrl?.takeIf { it.isNotBlank() } ?: provider.baseUrl
    return url.isNotBlank()
}

/** Which feature asked for the missing model; picks the dialog text. */
enum class ModelRequiredKind { FOOD, GOALS }

/**
 * Gate for a feature that needs the model: with [AiAvailability.MODEL_MISSING]
 * calls [onMissing] and skips [action] (no draft, no analyzing screen);
 * otherwise runs [action]. Returns true when [action] ran.
 */
suspend fun runIfModelPresent(
    availability: suspend () -> AiAvailability,
    onMissing: () -> Unit,
    action: suspend () -> Unit,
): Boolean {
    if (availability() == AiAvailability.MODEL_MISSING) {
        onMissing()
        return false
    }
    action()
    return true
}
