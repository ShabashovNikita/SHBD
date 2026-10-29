package app.shbd.data

import androidx.datastore.preferences.core.edit
import app.shbd.models.AIProvider
import app.shbd.models.AiProfile
import app.shbd.models.AiProfilePreset
import app.shbd.models.SpeechProvider
import app.shbd.services.ondevice.ModelCatalog
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer

/**
 * The embedded model (Gemma 4 E2B on the device) is the default AI; a cloud
 * provider set up in Profile → AI Provider is the alternative. Voice input is
 * always the system recognizer. [migrateToEmbeddedAiImpl] moves an installed
 * app onto the embedded model once; saved cloud profiles stay selectable.
 */
internal object EmbeddedAi {
    /**
     * Forces voice input to the system recognizer. Always true in the app; JVM
     * unit tests of the cloud speech clients run with
     * `-Dshbd.embeddedAi.enforce=false` (see app/build.gradle.kts).
     */
    @Volatile
    var enforced: Boolean = System.getProperty("shbd.embeddedAi.enforce") != "false"

    val modelId: String get() = ModelCatalog.default.modelId

    /** [base] retargeted at the embedded model; id and name are kept. */
    fun profile(base: AiProfile): AiProfile =
        base.copy(provider = AIProvider.ON_DEVICE, model = modelId, baseUrl = null, visionModel = null)
}

/** Result of [embeddedProfiles]: the rewritten list and the id to make active. */
internal data class EmbeddedProfiles(val profiles: List<AiProfile>, val activeId: String)

/**
 * Pure part of the migration: an active ON_DEVICE profile stays; otherwise the
 * first ON_DEVICE profile becomes active, or a fresh embedded profile is
 * appended. Cloud profiles are kept unchanged so they can be picked again.
 */
internal fun embeddedProfiles(
    profiles: List<AiProfile>,
    activeId: String?,
    now: Instant = Instant.now(),
): EmbeddedProfiles {
    val active = profiles.firstOrNull { it.id.toString() == activeId }
    if (active?.provider == AIProvider.ON_DEVICE) return EmbeddedProfiles(profiles, active.id.toString())
    profiles.firstOrNull { it.provider == AIProvider.ON_DEVICE }?.let {
        return EmbeddedProfiles(profiles, it.id.toString())
    }
    val fresh = embeddedAiProfile(now)
    return EmbeddedProfiles(profiles + fresh, fresh.id.toString())
}

private val embeddedProfileListSerializer = ListSerializer(AiProfile.serializer())

/**
 * One-shot move of an installed app onto the embedded model: an ON_DEVICE/E2B
 * profile becomes active, fallback disabled, speech → system recognizer.
 * Chat history and the diary are untouched. Returns true when it ran.
 */
internal suspend fun PreferencesStore.migrateToEmbeddedAiImpl(): Boolean {
    var ran = false
    dataStore.edit { prefs ->
        if (prefs[Keys.EMBEDDED_AI_MIGRATED] == true) return@edit
        val result = embeddedProfiles(
            decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON]),
            prefs[Keys.ACTIVE_AI_PROFILE_ID],
        )
        prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(embeddedProfileListSerializer, result.profiles)
        prefs[Keys.ACTIVE_AI_PROFILE_ID] = result.activeId
        prefs[Keys.FALLBACK_ENABLED] = false
        prefs[Keys.SELECTED_SPEECH_PROVIDER] = SpeechProvider.NATIVE.name
        prefs[Keys.EMBEDDED_AI_MIGRATED] = true
        ran = true
    }
    return ran
}

/**
 * "Install the local model" in Coach: makes an ON_DEVICE/E2B profile active
 * (the existing one, or a new one appended) without touching cloud profiles.
 */
internal suspend fun PreferencesStore.useEmbeddedModelImpl() {
    dataStore.edit { prefs ->
        val result = embeddedProfiles(
            decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON]),
            prefs[Keys.ACTIVE_AI_PROFILE_ID],
        )
        prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(embeddedProfileListSerializer, result.profiles)
        prefs[Keys.ACTIVE_AI_PROFILE_ID] = result.activeId
    }
}

/** A fresh profile on the embedded model, named after it — the default for new installs. */
internal fun embeddedAiProfile(now: Instant = Instant.now()): AiProfile = EmbeddedAi.profile(
    AiProfile(id = UUID.randomUUID(), name = ModelCatalog.default.displayName, provider = AIProvider.ON_DEVICE, createdAt = now),
)

/**
 * Profiles the app created on its own and the user never set up: inactive
 * cloud profiles with no key, no picked model and no endpoint (e.g. the
 * "Google Gemini" default seeded before the embedded model became default).
 */
internal fun unusedAutoProfiles(
    profiles: List<AiProfile>,
    activeId: String?,
    hasKey: (AiProfile) -> Boolean,
): List<AiProfile> = profiles.filter {
    it.id.toString() != activeId &&
        it.provider != AIProvider.ON_DEVICE &&
        it.model.isNullOrBlank() &&
        it.baseUrl.isNullOrBlank() &&
        !hasKey(it)
}

/**
 * One-shot cleanup: drops [unusedAutoProfiles] and the fallback slot (there is
 * no fallback provider any more). Keeps at least one profile.
 */
internal suspend fun PreferencesStore.cleanUpAiProfilesImpl(keyStore: KeyStore) {
    dataStore.edit { prefs ->
        if (prefs[Keys.AI_PROFILES_CLEANED_UP] == true) return@edit
        val profiles = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON])
        val unused = unusedAutoProfiles(profiles, prefs[Keys.ACTIVE_AI_PROFILE_ID]) {
            !keyStore.profileApiKey(it.id).isNullOrBlank()
        }.map { it.id }.toSet()
        val kept = profiles.filterNot { it.id in unused }
        if (kept.isNotEmpty() && unused.isNotEmpty()) {
            prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(embeddedProfileListSerializer, kept)
        }
        prefs.remove(Keys.FALLBACK_AI_PROFILE_ID)
        prefs[Keys.FALLBACK_ENABLED] = false
        prefs[Keys.AI_PROFILES_CLEANED_UP] = true
    }
}

/** The cloud alternative seeded next to the local one: Gemini on its default model, no key. */
internal fun remoteAiProfile(name: String, now: Instant = Instant.now()): AiProfile = AiProfile(
    id = UUID.randomUUID(),
    name = name,
    provider = AIProvider.GEMINI,
    model = AIProvider.GEMINI.defaultModel,
    createdAt = now,
)

/**
 * The two default profiles: a local one (embedded model) and a remote one
 * (Gemini). Appends whichever is missing and renames the auto-named embedded
 * profile ("Gemma 4 E2B") to [localName]; other profiles stay as they are.
 */
internal fun defaultAiProfiles(
    profiles: List<AiProfile>,
    localName: String,
    remoteName: String,
    now: Instant = Instant.now(),
): List<AiProfile> {
    val autoLocalName = ModelCatalog.default.displayName
    val renamed = profiles.map {
        if (it.provider == AIProvider.ON_DEVICE && it.name == autoLocalName) it.copy(name = localName) else it
    }
    val local = if (renamed.none { it.provider == AIProvider.ON_DEVICE }) {
        listOf(embeddedAiProfile(now).copy(name = localName))
    } else emptyList()
    val remote = if (renamed.none { it.provider != AIProvider.ON_DEVICE }) {
        listOf(remoteAiProfile(remoteName, now))
    } else emptyList()
    return markAiProfilePresets(renamed + local + remote, localName, remoteName)
}

/**
 * Tags the default profiles: the first on-device profile named [localName] gets
 * [AiProfilePreset.LOCAL], the first cloud one named [remoteName] gets
 * [AiProfilePreset.REMOTE] — unless some profile already carries that preset.
 */
internal fun markAiProfilePresets(
    profiles: List<AiProfile>,
    localName: String,
    remoteName: String,
): List<AiProfile> {
    val localId = if (profiles.any { it.preset == AiProfilePreset.LOCAL }) null
    else profiles.firstOrNull { it.provider == AIProvider.ON_DEVICE && it.name == localName }?.id
    val remoteId = if (profiles.any { it.preset == AiProfilePreset.REMOTE }) null
    else profiles.firstOrNull { it.provider != AIProvider.ON_DEVICE && it.name == remoteName }?.id
    return profiles.map {
        when (it.id) {
            localId -> it.copy(preset = AiProfilePreset.LOCAL)
            remoteId -> it.copy(preset = AiProfilePreset.REMOTE)
            else -> it
        }
    }
}

/**
 * One-shot: [defaultAiProfiles] and AI features back on (the switch is gone).
 * The active profile is kept; a profile deleted later is not re-created. An app
 * seeded before presets existed only gets its default profiles tagged.
 */
internal suspend fun PreferencesStore.seedDefaultAiProfilesImpl(localName: String, remoteName: String) {
    dataStore.edit { prefs ->
        if (prefs[Keys.AI_PROFILE_PRESETS_MARKED] == true) return@edit
        val alreadySeeded = prefs[Keys.AI_DEFAULT_PROFILES_SEEDED] == true
        val profiles = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON])
        val seeded = if (alreadySeeded) markAiProfilePresets(profiles, localName, remoteName)
        else defaultAiProfiles(profiles, localName, remoteName)
        if (seeded != profiles) {
            prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(embeddedProfileListSerializer, seeded)
        }
        if (!alreadySeeded) {
            val activeId = prefs[Keys.ACTIVE_AI_PROFILE_ID]
            if (seeded.none { it.id.toString() == activeId }) {
                prefs[Keys.ACTIVE_AI_PROFILE_ID] = seeded.first { it.provider == AIProvider.ON_DEVICE }.id.toString()
            }
            prefs.remove(Keys.AI_FEATURES_ENABLED)
            prefs[Keys.AI_DEFAULT_PROFILES_SEEDED] = true
        }
        prefs[Keys.AI_PROFILE_PRESETS_MARKED] = true
    }
}
