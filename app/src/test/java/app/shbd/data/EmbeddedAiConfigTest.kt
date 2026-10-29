package app.shbd.data

import androidx.datastore.preferences.core.edit
import app.shbd.models.AIProvider
import app.shbd.models.AiProfile
import app.shbd.models.ChatMessage
import app.shbd.models.SpeechProvider
import app.shbd.services.ondevice.ModelCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * The embedded model is the default AI and a cloud profile the alternative:
 * [PreferencesStore.aiCallConfig] follows the active profile, the one-shot
 * startup migration activates an embedded profile without losing cloud ones,
 * and voice input is always the system recognizer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class EmbeddedAiConfigTest {
    private val context = RuntimeEnvironment.getApplication()
    private fun prefs() = PreferencesStore(context)

    private val gemini = AiProfile(
        id = UUID.randomUUID(),
        name = "Gemini",
        provider = AIProvider.GEMINI,
        model = "gemini-flash",
        visionModel = "gemini-vision",
        createdAt = Instant.ofEpochMilli(1_700_000_000_000),
    )
    private val openAiFallback = AiProfile(
        id = UUID.randomUUID(),
        name = "OpenAI",
        provider = AIProvider.CUSTOM_OPENAI,
        baseUrl = "https://example.org/v1",
        createdAt = Instant.ofEpochMilli(1_700_000_000_000),
    )

    @Before
    fun setUp() {
        EmbeddedAi.enforced = true
        runBlocking {
            prefs().dataStore.edit {
                it.remove(Keys.AI_PROFILES_JSON)
                it.remove(Keys.ACTIVE_AI_PROFILE_ID)
                it.remove(Keys.FALLBACK_AI_PROFILE_ID)
                it.remove(Keys.FALLBACK_ENABLED)
                it.remove(Keys.SELECTED_SPEECH_PROVIDER)
                it.remove(Keys.EMBEDDED_AI_MIGRATED)
            }
            prefs().mutateProfiles { it += gemini; it += openAiFallback }
            prefs().setActiveAiProfileId(gemini.id.toString())
            prefs().setFallbackAiProfileId(openAiFallback.id.toString())
        }
    }

    @After
    fun tearDown() {
        EmbeddedAi.enforced = false
    }

    @Test
    fun aiCallConfig_followsActiveCloudProfile() = runBlocking {
        val cfg = prefs().aiCallConfig()
        assertEquals(AIProvider.GEMINI, cfg.provider)
        assertEquals("gemini-flash", cfg.selectedModel)
    }

    @Test
    fun speechProvider_isAlwaysNative() = runBlocking {
        val prefs = prefs()
        prefs.setSelectedSpeechProvider(SpeechProvider.values().first { it != SpeechProvider.NATIVE })
        assertEquals(SpeechProvider.NATIVE, prefs.selectedSpeechProvider.first())
    }

    @Test
    fun useEmbeddedModel_activatesOnDevice_keepsCloudProfile() = runBlocking {
        val prefs = prefs()
        prefs.useEmbeddedModel()
        val active = prefs.activeAiProfile.first()!!
        assertEquals(AIProvider.ON_DEVICE, active.provider)
        assertEquals(ModelCatalog.E2B.modelId, active.model)
        assertTrue(prefs.aiProfiles.first().any { it.id == gemini.id && it.provider == AIProvider.GEMINI })
        // A second call reuses the same embedded profile.
        prefs.useEmbeddedModel()
        assertEquals(1, prefs.aiProfiles.first().count { it.provider == AIProvider.ON_DEVICE })
    }

    @Test
    fun migration_activatesEmbedded_keepsGemini_disablesFallback_setsNativeSpeech() = runBlocking {
        val prefs = prefs()
        prefs.setSelectedSpeechProvider(SpeechProvider.values().first { it != SpeechProvider.NATIVE })
        assertTrue(prefs.migrateToEmbeddedAi())

        val active = prefs.activeAiProfile.first()!!
        assertEquals(AIProvider.ON_DEVICE, active.provider)
        assertEquals(ModelCatalog.E2B.modelId, active.model)
        assertEquals(gemini, prefs.aiProfiles.first().first { it.id == gemini.id })
        assertFalse(prefs.fallbackEnabled.first())
        assertEquals(SpeechProvider.NATIVE, prefs.selectedSpeechProvider.first())
    }

    @Test
    fun migration_runsOnce() = runBlocking {
        val prefs = prefs()
        assertTrue(prefs.migrateToEmbeddedAi())
        prefs.setFallbackAiProfileId(openAiFallback.id.toString())
        prefs.setActiveAiProfileId(gemini.id.toString())
        assertFalse(prefs.migrateToEmbeddedAi())
        assertEquals(gemini.id, prefs.activeAiProfile.first()?.id)
    }

    @Test
    fun embeddedProfiles_emptyList_createsEmbeddedProfile() {
        val result = embeddedProfiles(emptyList(), null)
        val only = result.profiles.single()
        assertEquals(AIProvider.ON_DEVICE, only.provider)
        assertEquals(ModelCatalog.E2B.modelId, only.model)
        assertEquals(only.id.toString(), result.activeId)
    }

    @Test
    fun embeddedProfiles_noOnDevice_appendsEmbeddedAndKeepsOthers() {
        val result = embeddedProfiles(listOf(gemini, openAiFallback), gemini.id.toString())
        assertEquals(listOf(gemini, openAiFallback), result.profiles.take(2))
        val added = result.profiles.last()
        assertEquals(AIProvider.ON_DEVICE, added.provider)
        assertEquals(added.id.toString(), result.activeId)
    }

    @Test
    fun embeddedProfiles_existingOnDevice_isReused() {
        val local = gemini.copy(id = UUID.randomUUID(), provider = AIProvider.ON_DEVICE, model = ModelCatalog.E2B.modelId)
        val result = embeddedProfiles(listOf(gemini, local), gemini.id.toString())
        assertEquals(listOf(gemini, local), result.profiles)
        assertEquals(local.id.toString(), result.activeId)
    }

    @Test
    fun migration_keepsChatHistory() = runBlocking {
        val prefs = prefs()
        val history = listOf(ChatMessage(role = ChatMessage.Role.USER, content = "Что съесть на ужин?"))
        prefs.setChatHistory(history)
        prefs.migrateToEmbeddedAi()
        assertEquals(history.map { it.content }, prefs.chatHistory.first().map { it.content })
    }
}
