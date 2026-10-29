package app.shbd.services.ondevice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app offers exactly one embedded model (Gemma 4 E2B). */
class ModelCatalogTest {

    @Test
    fun entries_containOnlyE2B() {
        assertEquals(listOf(ModelCatalog.E2B), ModelCatalog.entries)
        assertEquals(ModelCatalog.E2B, ModelCatalog.default)
    }

    @Test
    fun forModelId_retiredE4B_resolvesToE2B() {
        assertEquals(ModelCatalog.E2B, ModelCatalog.forModelId(ModelCatalog.E4B.modelId))
        assertEquals(ModelCatalog.E2B, ModelCatalog.forModelId(null))
    }

    @Test
    fun forVersion_retiredE4B_isUnknown() {
        assertNull(ModelCatalog.forVersion(ModelCatalog.E4B.version))
        assertEquals(ModelCatalog.E2B, ModelCatalog.forVersion(ModelCatalog.E2B.version))
    }

    @Test
    fun allKnown_coversRetiredFilesForCleanup() {
        assertTrue(ModelCatalog.allKnown.contains(ModelCatalog.E4B))
        assertTrue(ModelCatalog.allKnown.contains(ModelCatalog.E2B))
    }
}
