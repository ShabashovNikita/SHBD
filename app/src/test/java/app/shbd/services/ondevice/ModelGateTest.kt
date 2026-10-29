package app.shbd.services.ondevice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Photo / text analysis and goal recalculation ask for the model instead of starting. */
class ModelGateTest {

    private class Probe {
        var analyzed = 0
        var modelRequired: ModelRequiredKind? = null
    }

    private fun run(availability: AiAvailability, probe: Probe): Boolean = runBlocking {
        runIfModelPresent(
            availability = { availability },
            onMissing = { probe.modelRequired = ModelRequiredKind.FOOD },
            action = { probe.analyzed++ },
        )
    }

    @Test
    fun modelMissing_skipsAnalysis_andAsksForModel() {
        val probe = Probe()
        assertFalse(run(AiAvailability.MODEL_MISSING, probe))
        assertEquals(0, probe.analyzed)
        assertEquals(ModelRequiredKind.FOOD, probe.modelRequired)
    }

    @Test
    fun ready_runsAnalysis_withoutDialog() {
        val probe = Probe()
        assertTrue(run(AiAvailability.READY, probe))
        assertEquals(1, probe.analyzed)
        assertEquals(null, probe.modelRequired)
    }

    @Test
    fun disabled_isNotAModelPrompt() {
        // The service reports it itself (AiError.Disabled); no model dialog.
        val probe = Probe()
        assertTrue(run(AiAvailability.DISABLED, probe))
        assertEquals(null, probe.modelRequired)
    }
}
