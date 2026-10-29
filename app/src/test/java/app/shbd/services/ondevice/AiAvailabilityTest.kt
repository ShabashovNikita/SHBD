package app.shbd.services.ondevice

import app.shbd.models.AIProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAvailabilityTest {

    private val allStates = listOf(
        OnDeviceDownloadState.NotDownloaded,
        OnDeviceDownloadState.Downloading(50),
        OnDeviceDownloadState.WaitingForNetwork(10),
        OnDeviceDownloadState.Verifying,
        OnDeviceDownloadState.Downloaded,
        OnDeviceDownloadState.Failed("x"),
    )

    @Test
    fun disabled_winsOverEverything() {
        for (provider in listOf(AIProvider.ON_DEVICE, AIProvider.GEMINI)) {
            for (configured in listOf(true, false)) {
                for (state in allStates) {
                    assertEquals(AiAvailability.DISABLED, aiAvailability(false, provider, configured, state))
                }
            }
        }
    }

    @Test
    fun onDevice_readyOnlyWhenDownloaded() {
        assertEquals(
            AiAvailability.READY,
            aiAvailability(true, AIProvider.ON_DEVICE, remoteConfigured = false, OnDeviceDownloadState.Downloaded),
        )
        for (state in allStates - OnDeviceDownloadState.Downloaded) {
            assertEquals(AiAvailability.MODEL_MISSING, aiAvailability(true, AIProvider.ON_DEVICE, true, state))
        }
    }

    @Test
    fun remote_readyWhenConfigured_whateverTheLocalFile() {
        for (state in allStates) {
            assertEquals(AiAvailability.READY, aiAvailability(true, AIProvider.GEMINI, true, state))
            assertEquals(AiAvailability.MODEL_MISSING, aiAvailability(true, AIProvider.GEMINI, false, state))
        }
    }

    @Test
    fun remoteConfigured_needsKeyOnlyWhenProviderRequiresIt() {
        assertFalse(remoteConfigured(AIProvider.GEMINI, apiKey = null, customBaseUrl = null))
        assertFalse(remoteConfigured(AIProvider.GEMINI, apiKey = "  ", customBaseUrl = null))
        assertTrue(remoteConfigured(AIProvider.GEMINI, apiKey = "key", customBaseUrl = null))
        // Ollama runs without a key on a local server.
        assertTrue(remoteConfigured(AIProvider.OLLAMA, apiKey = null, customBaseUrl = "http://192.168.1.2:11434"))
        assertFalse(remoteConfigured(AIProvider.ON_DEVICE, apiKey = "key", customBaseUrl = null))
    }

    @Test
    fun entryPointsVisible_unlessDisabled() {
        assertTrue(AiAvailability.READY.entryPointsVisible)
        assertTrue(AiAvailability.MODEL_MISSING.entryPointsVisible)
        assertFalse(AiAvailability.DISABLED.entryPointsVisible)
    }
}
