package app.shbd.data

import app.shbd.models.AIProvider
import app.shbd.models.AiProfile
import app.shbd.services.ondevice.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Auto-created empty cloud profiles (the old "Google Gemini" default) are dropped; set-up ones stay. */
class AiProfilesCleanupTest {

    private fun profile(provider: AIProvider, model: String? = null, baseUrl: String? = null) = AiProfile(
        id = UUID.randomUUID(),
        name = provider.name,
        provider = provider,
        model = model,
        baseUrl = baseUrl,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun emptyInactiveCloudProfile_isUnused() {
        val gemini = profile(AIProvider.GEMINI)
        val embedded = embeddedAiProfile()
        val unused = unusedAutoProfiles(listOf(gemini, embedded), embedded.id.toString()) { false }
        assertEquals(listOf(gemini), unused)
    }

    @Test
    fun setUpProfiles_activeProfileAndOnDevice_stay() {
        val withKey = profile(AIProvider.GEMINI)
        val withModel = profile(AIProvider.OPENAI, model = "gpt-4o-mini")
        val withUrl = profile(AIProvider.OLLAMA, baseUrl = "http://192.168.1.2:11434")
        val activeEmpty = profile(AIProvider.ANTHROPIC)
        val onDevice = profile(AIProvider.ON_DEVICE)
        val unused = unusedAutoProfiles(
            listOf(withKey, withModel, withUrl, activeEmpty, onDevice),
            activeEmpty.id.toString(),
        ) { it.id == withKey.id }
        assertEquals(emptyList<AiProfile>(), unused)
    }

    @Test
    fun embeddedProfile_isTheLocalModel() {
        val p = embeddedAiProfile()
        assertEquals(AIProvider.ON_DEVICE, p.provider)
        assertEquals(ModelCatalog.default.modelId, p.model)
        assertEquals(ModelCatalog.default.displayName, p.name)
    }
}
