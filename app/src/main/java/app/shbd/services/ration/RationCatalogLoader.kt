package app.shbd.services.ration

import app.shbd.models.RationCatalog
import kotlinx.serialization.json.Json

/**
 * Parses assets/ration/catalog.json. Strict on enum names: an unknown tag is a
 * catalog bug caught by RationCatalogTest, not something to hide from users.
 */
object RationCatalogLoader {
    const val ASSET_PATH = "ration/catalog.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun load(text: String): RationCatalog = json.decodeFromString(RationCatalog.serializer(), text)
}
