package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.util.TextNormalizer
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Unified SteamGridDB API client for manual artwork selection and batch sync.
 */
object SteamGridDbClient {
    private const val BASE_URL = "https://www.steamgriddb.com/api/v2"

    data class GameMatch(val id: Int, val name: String, val verified: Boolean)
    data class Asset(val id: Int, val url: String, val thumb: String, val style: String, val score: Int)

    fun searchGames(query: String, apiKey: String): List<GameMatch> {
        if (query.isBlank() || apiKey.isBlank()) return emptyList()
        val data = requestData("/search/autocomplete/${encodePath(query)}", apiKey)
        return (0 until data.length()).mapNotNull { i ->
            data.optJSONObject(i)?.let { item ->
                val id = item.optInt("id", -1)
                val name = item.optString("name")
                if (id > 0 && name.isNotBlank()) GameMatch(id, name, item.optBoolean("verified", false)) else null
            }
        }
    }

    /** Styles: grids, icons, heroes, logos */
    fun fetchAssets(gameId: Int, style: String, apiKey: String): List<Asset> {
        if (gameId <= 0 || apiKey.isBlank()) return emptyList()
        // SteamGridDB uses plural endpoints: /grids/game/{id}, /icons/game/{id}, etc.
        val data = requestData("/$style/game/$gameId?nsfw=false&humor=false", apiKey)
        return (0 until data.length()).mapNotNull { i ->
            val item = data.optJSONObject(i) ?: return@mapNotNull null
            val url = item.optString("url")
            if (!url.startsWith("https://")) return@mapNotNull null
            Asset(
                id = item.optInt("id"),
                url = url,
                thumb = item.optString("thumb").takeIf { it.isNotBlank() } ?: url,
                style = item.optString("style"),
                score = item.optInt("score", 0)
            )
        }
    }

    private fun requestData(path: String, apiKey: String): JSONArray {
        val connection = URL("$BASE_URL$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 8_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "OzRetroRom/0.6.0-beta.4 SteamGridDB-Client")
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                if (code == 401 || code == 403) throw IOException("API Key de SteamGridDB inválida.")
                if (code == 429) throw IOException("SteamGridDB: Límite de solicitudes alcanzado.")
                return JSONArray()
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            if (!json.optBoolean("success", false)) return JSONArray()
            return when (val data = json.opt("data")) {
                is JSONArray -> data
                is JSONObject -> JSONArray().put(data)
                else -> JSONArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encodePath(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    fun searchableTitle(title: String): String = title
        .replace(Regex("\\s*\\([^)]*(USA|Europe|Japan|World|Asia|Rev|Disc|Disk|Beta|Proto)[^)]*\\)", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
