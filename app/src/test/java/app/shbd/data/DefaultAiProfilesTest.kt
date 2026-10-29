package app.shbd.data

import app.shbd.models.AIProvider
import app.shbd.models.AiProfile
import app.shbd.models.AiProfilePreset
import app.shbd.services.ondevice.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Two default profiles: "Local model" (embedded) and "Remote model" (Gemini on its default model). */
class DefaultAiProfilesTest {

    private val local = "Local model"
    private val remote = "Remote model"

    private fun profile(provider: AIProvider, name: String = provider.name) = AiProfile(
        id = UUID.randomUUID(),
        name = name,
        provider = provider,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun emptyList_getsLocalAndRemote() {
        val seeded = defaultAiProfiles(emptyList(), local, remote)
        assertEquals(listOf(AIProvider.ON_DEVICE, AIProvider.GEMINI), seeded.map { it.provider })
        assertEquals(listOf(local, remote), seeded.map { it.name })
        assertEquals(ModelCatalog.default.modelId, seeded[0].model)
        assertEquals(AIProvider.GEMINI.defaultModel, seeded[1].model)
        assertEquals(listOf(AiProfilePreset.LOCAL, AiProfilePreset.REMOTE), seeded.map { it.preset })
    }

    @Test
    fun alreadySeededProfiles_getTaggedOnce() {
        val mine = profile(AIProvider.ON_DEVICE, "Phone")
        val localDefault = profile(AIProvider.ON_DEVICE, local)
        val remoteDefault = profile(AIProvider.GEMINI, remote)
        val tagged = markAiProfilePresets(listOf(mine, localDefault, remoteDefault), local, remote)
        assertEquals(listOf(null, AiProfilePreset.LOCAL, AiProfilePreset.REMOTE), tagged.map { it.preset })
        // A second pass changes nothing, and a user-added twin name stays untagged.
        val twin = profile(AIProvider.ON_DEVICE, local)
        val again = markAiProfilePresets(tagged + twin, local, remote)
        assertEquals(tagged + twin, again)
    }

    @Test
    fun autoNamedEmbeddedProfile_isRenamedAndKeepsItsId() {
        val embedded = embeddedAiProfile()
        val seeded = defaultAiProfiles(listOf(embedded), local, remote)
        assertEquals(embedded.id, seeded[0].id)
        assertEquals(local, seeded[0].name)
        assertEquals(remote, seeded[1].name)
        // The remote profile has a model, so the empty-profile cleanup never drops it.
        assertEquals(emptyList<AiProfile>(), unusedAutoProfiles(seeded, embedded.id.toString()) { false })
    }

    @Test
    fun existingCloudProfile_noSecondRemote() {
        val openAi = profile(AIProvider.OPENAI, "My OpenAI")
        val seeded = defaultAiProfiles(listOf(openAi), local, remote)
        assertEquals(listOf(openAi.id), seeded.filter { it.provider != AIProvider.ON_DEVICE }.map { it.id })
        assertEquals(listOf(local), seeded.filter { it.provider == AIProvider.ON_DEVICE }.map { it.name })
    }

    @Test
    fun bothPresent_userNamesKept() {
        val mine = profile(AIProvider.ON_DEVICE, "Phone")
        val gemini = profile(AIProvider.GEMINI, "Work")
        assertEquals(listOf(mine, gemini), defaultAiProfiles(listOf(mine, gemini), local, remote))
    }
}
