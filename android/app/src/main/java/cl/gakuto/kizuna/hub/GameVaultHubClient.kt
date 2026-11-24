package cl.gakuto.kizuna.hub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

data class HubHealthInfo(
    val romsCount: Int,
    val minervaEntries: Int,
    val cachedCovers: Int,
)

data class HubRemoteGame(
    val entryId: String,
    val title: String,
    val cleanTitle: String,
    val consoleKey: String,
    val sizeBytes: Long,
    val extension: String,
    val region: String,
    val description: String,
    val coverUrl: String?,
    val hasSpanishPatch: Boolean,
)

/**
 * Optional client for Game Vault PC Hub on the user's private LAN.
 *
 * Plain HTTP is strictly gated to RFC-1918 private LAN addresses (192.168.*, 10.*, 172.16-31.*, localhost)
 * over raw sockets, leaving AndroidManifest usesCleartextTraffic="false" completely intact.
 */
object GameVaultHubClient {
    fun isAllowedLanHost(rawHost: String): Boolean {
        val host = rawHost.trim().lowercase()
        if (host == "localhost" || host == "127.0.0.1") return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return when (octets[0]) {
            10 -> true
            192 -> octets[1] == 168
            172 -> octets[1] in 16..31
            else -> false
        }
    }

    private fun normalizeBaseUrl(rawUrl: String): URI {
        val clean = rawUrl.trim().trimEnd('/')
        require(clean.isNotBlank()) { "Escribe la URL de tu servidor Game Vault (ej. http://192.168.100.173:17894)." }
        val withScheme = if ("://" !in clean) "http://$clean" else clean
        val uri = URI(withScheme)
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host.orEmpty()
        if (scheme == "http") {
            require(isAllowedLanHost(host)) {
                "Por seguridad, HTTP solo se permite en IPs privadas de tu red local (192.168.x.x / 10.x.x.x / 172.16-31.x.x)."
            }
        } else {
            require(scheme == "https") { "Usa una URL http:// LAN o https://." }
        }
        return uri
    }

    suspend fun testHealth(baseUrl: String): HubHealthInfo = withContext(Dispatchers.IO) {
        val body = requestText(baseUrl, "/health", "GET")
        val obj = JSONObject(body)
        HubHealthInfo(
            romsCount = obj.optInt("roms_count", 0),
            minervaEntries = obj.optInt("minerva_entries", 0),
            cachedCovers = obj.optInt("cached_covers", 0),
        )
    }

    suspend fun fetchLibrary(baseUrl: String, limit: Int = 250): List<HubRemoteGame> = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(baseUrl).toString().trimEnd('/')
        val body = requestText(base, "/api/v1/library", "GET")
        val arr = JSONArray(body)
        val out = mutableListOf<HubRemoteGame>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("isSys", false)) continue
            val cov = o.optString("coverUrl").takeIf { it.isNotBlank() }?.let { path ->
                if (path.startsWith("http")) path else "$base$path"
            }
            out += HubRemoteGame(
                entryId = o.optString("entryId"),
                title = o.optString("title"),
                cleanTitle = o.optString("cleanTitle", o.optString("title")),
                consoleKey = o.optString("consoleKey", "other"),
                sizeBytes = o.optLong("sizeBytes", 0L),
                extension = o.optString("extension", "zip"),
                region = o.optString("region", ""),
                description = o.optString("description", ""),
                coverUrl = cov,
                hasSpanishPatch = o.optBoolean("hasSpanishPatch", false),
            )
            if (out.size >= limit) break
        }
        out
    }

    suspend fun searchCatalog(
        baseUrl: String,
        query: String,
        platform: String = "",
    ): List<cl.gakuto.kizuna.catalog.CatalogSearchItem> = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(baseUrl).toString().trimEnd('/')
        val qEnc = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        val pEnc = URLEncoder.encode(platform.trim().lowercase(), "UTF-8").replace("+", "%20")
        val path = "/api/v1/catalog/search?q=$qEnc" + if (pEnc.isNotBlank()) "&platform=$pEnc" else ""
        val body = requestText(base, path, "GET")
        parseCatalogSearchJson(body, base)
    }

    internal fun parseCatalogSearchJson(
        body: String,
        base: String,
    ): List<cl.gakuto.kizuna.catalog.CatalogSearchItem> {
        val trimmed = body.trim()
        val rows = when {
            trimmed.startsWith("[") -> org.json.JSONArray(trimmed)
            trimmed.startsWith("{") -> JSONObject(trimmed).optJSONArray("results")
            else -> null
        } ?: return emptyList()
        val out = ArrayList<cl.gakuto.kizuna.catalog.CatalogSearchItem>(rows.length())
        for (i in 0 until rows.length()) {
            val o = rows.optJSONObject(i) ?: continue
            val rawArchive = o.optString("archiveUrl").ifBlank { o.optString("archive_url") }
            val archiveUrl = rawArchive.takeIf { it.isNotBlank() }?.let { raw ->
                if (raw.startsWith("http")) raw else "$base$raw"
            }.orEmpty()
            val plat = o.optString("platform").ifBlank { o.optString("consoleKey") }.ifBlank { o.optString("console") }.lowercase()
            val title = o.optString("title")
            val sizeBytes = o.optLong("size", -1L).let { if (it >= 0L) it else o.optLong("size_bytes", 0L) }
            out += cl.gakuto.kizuna.catalog.CatalogSearchItem(
                id = o.optString("id"),
                title = title,
                edition = o.optString("edition"),
                platform = plat,
                filename = o.optString("filename"),
                size = sizeBytes,
                infoHash = o.optString("infoHash").ifBlank { o.optString("info_hash") },
                provider = o.optString("provider", "PC Hub"),
                archiveUrl = archiveUrl,
                arcadeCore = o.optString("arcadeCore").ifBlank { o.optString("arcade_core") },
                searchText = title.lowercase(),
            )
        }
        return out
    }

    suspend fun downloadRom(
        baseUrl: String,
        entryId: String,
        output: OutputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(entryId, "UTF-8")
        streamToOutput(baseUrl, "/api/v1/library/$encoded/download", output, onProgress)
    }

    suspend fun enqueueRemoteFtp(baseUrl: String, entryId: String, targetId: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("entryId", entryId).put("targetId", targetId).toString()
        requestText(baseUrl, "/api/v1/transfers", "POST", payload)
    }

    suspend fun triggerCoverSync(baseUrl: String): String = withContext(Dispatchers.IO) {
        requestText(baseUrl, "/api/v1/covers/sync", "POST", "{}")
    }

    suspend fun requestSagaAiAudit(
        baseUrl: String,
        sagaId: String,
        payloadJson: String = "{}",
    ): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(sagaId, "UTF-8").replace("+", "%20")
        requestText(baseUrl, "/api/v1/sagas/$encoded/ai-audit", "POST", payloadJson)
    }

    private fun requestText(
        baseUrl: String,
        path: String,
        method: String,
        jsonBody: String? = null,
    ): String {
        val baos = ByteArrayOutputStream()
        if (method == "GET") {
            streamToOutput(baseUrl, path, baos) { _, _ -> }
        } else {
            val baseUri = normalizeBaseUrl(baseUrl)
            if (baseUri.scheme.equals("https", true)) {
                val conn = (URL("${baseUri.toString().trimEnd('/')}$path").openConnection() as HttpsURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 8_000
                    readTimeout = 15_000
                    doOutput = jsonBody != null
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    if (jsonBody != null) conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                    if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                    conn.inputStream.copyTo(baos)
                } finally {
                    conn.disconnect()
                }
            } else {
                rawLanHttpRequest(baseUri, path, method, jsonBody, baos) { _, _ -> }
            }
        }
        return baos.toString("UTF-8")
    }

    private fun streamToOutput(
        baseUrl: String,
        path: String,
        output: OutputStream,
        onProgress: (Long, Long) -> Unit,
    ): String {
        val baseUri = normalizeBaseUrl(baseUrl)
        if (baseUri.scheme.equals("https", true)) {
            val fullUrl = "${baseUri.toString().trimEnd('/')}$path"
            val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 45_000
                instanceFollowRedirects = true
            }
            return try {
                if (conn.responseCode !in 200..299) throw IOException("Hub respondió HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                val buf = ByteArray(32 * 1024)
                var copied = 0L
                conn.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        copied += n
                        onProgress(copied, total)
                    }
                }
                filenameFromDisposition(conn.getHeaderField("Content-Disposition")) ?: path.substringAfterLast('/')
            } finally {
                conn.disconnect()
            }
        }
        return rawLanHttpRequest(baseUri, path, "GET", null, output, onProgress)
    }

    private fun rawLanHttpRequest(
        baseUri: URI,
        path: String,
        method: String,
        jsonBody: String?,
        output: OutputStream,
        onProgress: (Long, Long) -> Unit,
    ): String {
        val host = baseUri.host ?: throw IOException("Host inválido")
        val port = if (baseUri.port > 0) baseUri.port else 80
        val prefix = baseUri.path?.trimEnd('/').orEmpty()
        val reqPath = "$prefix$path"
        val sock = Socket()
        sock.connect(InetSocketAddress(host, port), 8_000)
        sock.soTimeout = 45_000
        return sock.use {
            val out = it.getOutputStream()
            val bodyBytes = jsonBody?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            val req = buildString {
                append("$method $reqPath HTTP/1.1\r\n")
                append("Host: $host:$port\r\n")
                append("User-Agent: GameVaultAndroid/0.6.0\r\n")
                append("Connection: close\r\n")
                if (bodyBytes.isNotEmpty()) {
                    append("Content-Type: application/json\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                }
                append("\r\n")
            }.toByteArray(Charsets.US_ASCII)
            out.write(req)
            if (bodyBytes.isNotEmpty()) out.write(bodyBytes)
            out.flush()

            val bis = BufferedInputStream(it.getInputStream(), 32 * 1024)
            val headerBytes = ByteArrayOutputStream()
            var state = 0
            while (true) {
                val b = bis.read()
                if (b < 0) break
                headerBytes.write(b)
                state = when {
                    b == '\r'.code && (state == 0 || state == 2) -> state + 1
                    b == '\n'.code && (state == 1 || state == 3) -> state + 1
                    b == '\r'.code -> 1
                    else -> 0
                }
                if (state == 4) break
            }
            val headerLines = headerBytes.toString("ISO_8859_1").split("\r\n").filter { line -> line.isNotBlank() }
            val statusLine = headerLines.firstOrNull() ?: throw IOException("Sin respuesta HTTP del servidor LAN")
            val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            if (code !in 200..299) throw IOException("Servidor Hub LAN respondió HTTP $code")

            val headers = headerLines.drop(1).associate { line ->
                val idx = line.indexOf(':')
                if (idx > 0) line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim()
                else line.lowercase() to ""
            }
            val contentLen = headers["content-length"]?.toLongOrNull() ?: -1L
            val chunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true

            if (chunked) {
                var totalCopied = 0L
                while (true) {
                    val sizeLine = readAsciiLine(bis) ?: break
                    val chunkSize = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: 0
                    if (chunkSize <= 0) break
                    var remaining = chunkSize
                    val buf = ByteArray(16 * 1024)
                    while (remaining > 0) {
                        val n = bis.read(buf, 0, minOf(buf.size, remaining))
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        remaining -= n
                        totalCopied += n
                        onProgress(totalCopied, -1L)
                    }
                    readAsciiLine(bis) // CRLF after chunk
                }
            } else {
                val buf = ByteArray(32 * 1024)
                var totalCopied = 0L
                while (true) {
                    val n = bis.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    totalCopied += n
                    onProgress(totalCopied, contentLen)
                }
            }
            filenameFromDisposition(headers["content-disposition"]) ?: path.substringAfterLast('/')
        }
    }

    private fun readAsciiLine(bis: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = bis.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun filenameFromDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val match = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE).find(header)
        return match?.groupValues?.getOrNull(1)?.trim()
    }
}
