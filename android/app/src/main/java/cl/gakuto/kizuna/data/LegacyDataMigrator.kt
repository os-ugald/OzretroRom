package cl.gakuto.kizuna.data

import android.content.Context
import androidx.room.withTransaction
import cl.gakuto.kizuna.util.TextNormalizer
import org.json.JSONArray
import org.json.JSONObject

class LegacyDataMigrator(
    private val context: Context,
    private val db: OzDatabase,
) {
    suspend fun migrateIfNeeded() {
        val migrationPrefs = context.getSharedPreferences("oz_retrorom", Context.MODE_PRIVATE)
        if (migrationPrefs.getBoolean(KEY_DONE, false)) return
        val legacy = context.getSharedPreferences("oz_retrorom_collections", Context.MODE_PRIVATE)
        val favorites = legacy.getStringSet("favorites", emptySet()).orEmpty()
        val mine = legacy.getStringSet("my_collection", emptySet()).orEmpty()
        val imported = parseImported(legacy.getString("imported_games", null))

        db.withTransaction {
            if (imported.isNotEmpty()) db.gameDao().insertAll(imported)
            val memberships = buildList {
                favorites.forEach { add(CollectionMembershipEntity("favorites", it)) }
                mine.forEach { add(CollectionMembershipEntity("my-collection", it)) }
            }
            if (memberships.isNotEmpty()) db.collectionDao().addMemberships(memberships)
        }
        migrationPrefs.edit().putBoolean(KEY_DONE, true).apply()
    }

    private fun parseImported(raw: String?): List<GameEntity> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = if (raw.trimStart().startsWith("[")) JSONObject().put("games", JSONArray(raw)) else JSONObject(raw)
            val games = root.optJSONArray("games") ?: JSONArray()
            (0 until games.length()).mapNotNull { index ->
                val item = games.optJSONObject(index) ?: return@mapNotNull null
                val title = item.optString("title").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val platformId = item.optString("platform", "custom").ifBlank { "custom" }
                val id = item.optString("id").takeIf { it.isNotBlank() }
                    ?: "imported-${TextNormalizer.slug("$platformId-$title")}" 
                GameEntity(
                    id = id,
                    title = title,
                    normalizedTitle = TextNormalizer.normalize(title),
                    platform = item.optString("platformName", platformId),
                    systemId = platformId,
                    year = item.optInt("year").takeIf { it > 0 },
                    region = "Unknown",
                    thumbnailRepo = null,
                    source = "imported-v0.3",
                    launchValidated = LegacyLaunchValidationPolicy.forImported(
                        platformId,
                        item.optBoolean("oneTapSupported", true),
                    ),
                    coreId = item.optString("defaultCore").takeIf { it.isNotBlank() },
                    virtualExtension = virtualExtension(platformId),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun virtualExtension(platform: String): String = when (platform.lowercase()) {
        "arcade", "arcade-fbneo" -> "zip"
        "snes" -> "sfc"
        "gba" -> "gba"
        "n64" -> "z64"
        "megadrive", "genesis" -> "md"
        "ps1" -> "chd"
        "ps2" -> "iso"
        "n3ds", "3ds" -> "3ds"
        else -> "ozr"
    }

    companion object {
        private const val KEY_DONE = "legacy_collections_migrated"
    }
}

internal object LegacyLaunchValidationPolicy {
    fun forImported(platformId: String, oneTapSupported: Boolean): Boolean =
        if (platformId == "gba") false else oneTapSupported
}
