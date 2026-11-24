package cl.gakuto.kizuna.patcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SpanishPatchCandidate(
    val title: String,
    val identifier: String,
    val filename: String,
    val patchUrl: String,
    val format: String,
    val sizeBytes: Long,
)

object PatchSearchClient {
    private const val MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024

    suspend fun searchSpanishPatches(gameTitle: String): List<SpanishPatchCandidate> = withContext(Dispatchers.IO) {
        val clean = gameTitle.replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), " ").replace(Regex("""\s+"""), " ").trim()
        if (clean.length < 3) return@withContext emptyList()

        val q = URLEncoder.encode("(\"$clean\" OR $clean) AND (spanish OR espanol OR traduccion) AND (ips OR bps OR patch)", "UTF-8")
        val searchUrl = "https://archive.org/advancedsearch.php?q=$q&fl[]=identifier&fl[]=title&rows=6&output=json"
        val jsonText = httpGetText(searchUrl) ?: return@withContext emptyList()
        val docs = JSONObject(jsonText).optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()

        val out = mutableListOf<SpanishPatchCandidate>()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val id = doc.optString("identifier")
            val itemTitle = doc.optString("title", id)
            if (id.isBlank()) continue
            val filesJson = httpGetText("https://archive.org/metadata/${URLEncoder.encode(id, "UTF-8")}/files") ?: continue
            val files = JSONObject(filesJson).optJSONArray("result") ?: continue
            for (fIdx in 0 until files.length()) {
                val f = files.optJSONObject(fIdx) ?: continue
                val name = f.optString("name")
                val lower = name.lowercase()
                if (!lower.endsWith(".bps") && !lower.endsWith(".ips") && !lower.endsWith(".zip")) continue
                val size = f.optString("size").toLongOrNull() ?: 0L
                if (size > MAX_DOWNLOAD_BYTES) continue
                val encId = URLEncoder.encode(id, "UTF-8")
                val encName = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                val fmt = when {
                    lower.endsWith(".bps") -> "BPS"
                    lower.endsWith(".ips") -> "IPS"
                    else -> "ZIP"
                }
                out += SpanishPatchCandidate(
                    title = itemTitle,
                    identifier = id,
                    filename = name.substringAfterLast('/'),
                    patchUrl = "https://archive.org/download/$encId/$encName",
                    format = fmt,
                    sizeBytes = size,
                )
                if (out.size >= 12) return@withContext out
            }
        }
        out
    }

    suspend fun downloadPatchBytes(urlString: String): ByteArray = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        require(url.protocol.equals("https", true)) {
            "La URL del parche debe usar HTTPS seguro."
        }
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GameVaultAndroid/0.6.0")
        }
        try {
            if (conn.responseCode !in 200..299) {
                error("Error HTTP ${conn.responseCode} al descargar el parche.")
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            require(bytes.size <= MAX_PATCH_BYTES) { "El parche supera el límite de 25 MB." }
            bytes
        } finally {
            conn.disconnect()
        }
    }

    private const val MAX_PATCH_BYTES = 25 * 1024 * 1024

    private fun httpGetText(urlString: String): String? = runCatching {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GameVaultAndroid/0.6.0")
        }
        try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
