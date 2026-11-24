package cl.gakuto.kizuna.hub

import cl.gakuto.kizuna.catalog.CatalogSearchItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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

data class HubSseEvent(
    val id: String?,
    val event: String,
    val data: String,
)

data class HubSyncChange(
    val revision: Long,
    val entityType: String,
    val entityId: String,
    val op: String,
    val payloadJson: String,
    val updatedAt: String,
    val sourceDevice: String,
)

data class HubSyncDeltaResponse(
    val latestRevision: Long,
    val hasMore: Boolean,
    val serverTime: String,
    val changes: List<HubSyncChange>,
)

data class HubSyncOutboundChange(
    val entityId: String,
    val entityType: String = "game_state",
    val op: String = "upsert",
    val favorite: Boolean? = null,
    val playCount: Int? = null,
    val lastPlayedAt: String? = null,
    val quickResumeJson: String? = null,
    val customMetadataJson: String? = null,
    val updatedAt: String? = null,
)

data class HubSyncPushResult(
    val applied: Int,
    val latestRevision: Long,
    val serverTime: String,
)

/**
 * LAN client for Game Vault PC Hub built on OkHttp with connection pooling, transparent gzip,
 * SSE real-time events, and RFC-1918 / .local private subnet enforcement.
 */
object GameVaultHubClient {
    private const val SYNTHETIC_LAN_SUFFIX = ".lan.ozvault.local"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val LOCAL_HOST_REGEX = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*\\.local\\.?$")

    private data class PreparedUrl(
        val requestUrl: String,
        val hostHeader: String?,
    )

    internal fun parseRfc1918Octets(rawHost: String): IntArray? {
        val parts = rawHost.trim().lowercase().split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (i in 0..3) {
            val v = parts[i].toIntOrNull() ?: return null
            if (v !in 0..255) return null
            octets[i] = v
        }
        val valid = when (octets[0]) {
            10 -> true
            192 -> octets[1] == 168
            172 -> octets[1] in 16..31
            else -> false
        }
        return if (valid) octets else null
    }

    internal fun decodeSyntheticLanHost(hostname: String): String? {
        val clean = hostname.trim().lowercase().trimEnd('.')
        if (!clean.endsWith(SYNTHETIC_LAN_SUFFIX)) return null
        val label = clean.removeSuffix(SYNTHETIC_LAN_SUFFIX)
        if (label.isBlank() || '.' in label) return null
        val candidateIp = label.replace('-', '.')
        return if (parseRfc1918Octets(candidateIp) != null) candidateIp else null
    }

    fun isAllowedLanHost(rawHost: String): Boolean {
        val host = rawHost.trim().lowercase().trimEnd('.')
        if (host.isBlank()) return false
        if (host == "localhost" || host == "127.0.0.1") return true
        if (parseRfc1918Octets(host) != null) return true
        decodeSyntheticLanHost(host)?.let { return true }
        return LOCAL_HOST_REGEX.matches("$host.")
    }

    private val lanDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val decodedIp = decodeSyntheticLanHost(hostname)
            if (decodedIp != null) {
                val octets = parseRfc1918Octets(decodedIp)
                    ?: throw IOException("Host LAN RFC-1918 inválido: $hostname")
                val bytes = byteArrayOf(
                    octets[0].toByte(),
                    octets[1].toByte(),
                    octets[2].toByte(),
                    octets[3].toByte(),
                )
                return listOf(InetAddress.getByAddress(hostname, bytes))
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    private val lanGuardInterceptor = Interceptor { chain ->
        val req = chain.request()
        if (req.url.scheme.equals("http", ignoreCase = true)) {
            val logicalHost = decodeSyntheticLanHost(req.url.host) ?: req.url.host
            if (!isAllowedLanHost(logicalHost)) {
                throw IOException("Tráfico HTTP bloqueado fuera de red privada LAN: $logicalHost")
            }
        }
        chain.proceed(req)
    }

    private val sharedPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

    internal val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(sharedPool)
        .dns(lanDns)
        .addNetworkInterceptor(lanGuardInterceptor)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    internal val sseClient: OkHttpClient = httpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private fun normalizeBaseUrl(rawUrl: String): URI {
        val clean = rawUrl.trim().trimEnd('/')
        require(clean.isNotBlank()) { "Escribe la URL de tu servidor Game Vault (ej. http://192.168.100.173:17894)." }
        val withScheme = if ("://" !in clean) "http://$clean" else clean
        val uri = URI(withScheme)
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host.orEmpty()
        if (scheme == "http") {
            require(isAllowedLanHost(host)) {
                "Por seguridad, HTTP solo se permite en IPs privadas de tu red local (192.168.x.x / 10.x.x.x / 172.16-31.x.x) o .local."
            }
        } else {
            require(scheme == "https") { "Usa una URL http:// LAN o https://." }
        }
        return uri
    }

    private fun prepareRequestUrl(baseUri: URI, path: String): PreparedUrl {
        val scheme = baseUri.scheme.lowercase()
        val rawHost = baseUri.host ?: throw IOException("Host inválido")
        val prefix = baseUri.rawPath?.trimEnd('/').orEmpty()
        val suffix = if (path.startsWith("/")) path else "/$path"
        val port = baseUri.port
        val portSuffix = if (port > 0) ":$port" else ""

        // Rewrite RFC-1918 IPv4 literals to *.lan.ozvault.local so OkHttp cleartext passes
        // Android NetworkSecurityPolicy while <base-config cleartextTrafficPermitted="false"> holds.
        val octets = if (scheme == "http") parseRfc1918Octets(rawHost) else null
        return if (octets != null) {
            val syntheticHost = "${octets[0]}-${octets[1]}-${octets[2]}-${octets[3]}$SYNTHETIC_LAN_SUFFIX"
            PreparedUrl(
                requestUrl = "$scheme://$syntheticHost$portSuffix$prefix$suffix",
                hostHeader = "$rawHost$portSuffix",
            )
        } else {
            PreparedUrl(
                requestUrl = "$scheme://$rawHost$portSuffix$prefix$suffix",
                hostHeader = null,
            )
        }
    }

    private fun buildRequest(baseUrl: String, path: String, method: String, jsonBody: String? = null): Request {
        val baseUri = normalizeBaseUrl(baseUrl)
        val prepared = prepareRequestUrl(baseUri, path)
        val builder = Request.Builder()
            .url(prepared.requestUrl)
            .header("User-Agent", "GameVaultAndroid/0.6.0")
        if (prepared.hostHeader != null) {
            builder.header("Host", prepared.hostHeader)
        }
        when (method.uppercase()) {
            "GET" -> builder.get()
            else -> builder.method(method.uppercase(), (jsonBody ?: "").toRequestBody(JSON_MEDIA_TYPE))
        }
        return builder.build()
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
    ): List<CatalogSearchItem> = withContext(Dispatchers.IO) {
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
    ): List<CatalogSearchItem> {
        val trimmed = body.trim()
        val rows = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> JSONObject(trimmed).optJSONArray("results")
            else -> null
        } ?: return emptyList()
        val out = ArrayList<CatalogSearchItem>(rows.length())
        for (i in 0 until rows.length()) {
            val o = rows.optJSONObject(i) ?: continue
            val rawArchive = o.optString("archiveUrl").ifBlank { o.optString("archive_url") }
            val archiveUrl = rawArchive.takeIf { it.isNotBlank() }?.let { raw ->
                if (raw.startsWith("http")) raw else "$base$raw"
            }.orEmpty()
            val plat = o.optString("platform").ifBlank { o.optString("consoleKey") }.ifBlank { o.optString("console") }.lowercase()
            val title = o.optString("title")
            val sizeBytes = o.optLong("size", -1L).let { if (it >= 0L) it else o.optLong("size_bytes", 0L) }
            out += CatalogSearchItem(
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

    suspend fun fetchSyncDelta(baseUrl: String, sinceRevision: Long = 0L): HubSyncDeltaResponse = withContext(Dispatchers.IO) {
        val body = requestText(baseUrl, "/api/v1/sync?since=${sinceRevision.coerceAtLeast(0L)}", "GET")
        parseSyncDeltaJson(body)
    }

    suspend fun pushSyncDelta(
        baseUrl: String,
        deviceId: String,
        changes: List<HubSyncOutboundChange>,
    ): HubSyncPushResult = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        for (ch in changes) {
            val o = JSONObject()
                .put("entityId", ch.entityId)
                .put("entityType", ch.entityType)
                .put("op", ch.op)
            if (ch.favorite != null) o.put("favorite", ch.favorite)
            if (ch.playCount != null) o.put("playCount", ch.playCount)
            if (!ch.lastPlayedAt.isNullOrBlank()) o.put("lastPlayedAt", ch.lastPlayedAt)
            if (!ch.quickResumeJson.isNullOrBlank()) {
                runCatching { JSONObject(ch.quickResumeJson) }.getOrNull()?.let { o.put("quickResume", it) }
            }
            if (!ch.customMetadataJson.isNullOrBlank()) {
                runCatching { JSONObject(ch.customMetadataJson) }.getOrNull()?.let { o.put("customMetadata", it) }
            }
            if (!ch.updatedAt.isNullOrBlank()) o.put("updatedAt", ch.updatedAt)
            arr.put(o)
        }
        val payload = JSONObject()
            .put("deviceId", deviceId.ifBlank { "android" })
            .put("changes", arr)
            .toString()
        val body = requestText(baseUrl, "/api/v1/sync", "POST", payload)
        val obj = JSONObject(body)
        HubSyncPushResult(
            applied = obj.optInt("applied", 0),
            latestRevision = obj.optLong("latestRevision", 0L),
            serverTime = obj.optString("serverTime", ""),
        )
    }

    internal fun parseSyncDeltaJson(body: String): HubSyncDeltaResponse {
        val obj = JSONObject(body)
        val arr = obj.optJSONArray("changes") ?: JSONArray()
        val list = ArrayList<HubSyncChange>(arr.length())
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val payloadObj = c.optJSONObject("payload")
            list += HubSyncChange(
                revision = c.optLong("revision", 0L),
                entityType = c.optString("entityType", "game_state"),
                entityId = c.optString("entityId"),
                op = c.optString("op", "upsert"),
                payloadJson = payloadObj?.toString() ?: c.optString("payload", "{}"),
                updatedAt = c.optString("updatedAt", ""),
                sourceDevice = c.optString("sourceDevice", "pc-hub"),
            )
        }
        return HubSyncDeltaResponse(
            latestRevision = obj.optLong("latestRevision", 0L),
            hasMore = obj.optBoolean("hasMore", false),
            serverTime = obj.optString("serverTime", ""),
            changes = list,
        )
    }

    internal fun parseSseFrame(lines: List<String>): HubSseEvent? {
        if (lines.isEmpty()) return null
        var id: String? = null
        var eventName = "message"
        val dataLines = mutableListOf<String>()
        for (line in lines) {
            if (line.isEmpty() || line.startsWith(":")) continue
            val colon = line.indexOf(':')
            val field = if (colon >= 0) line.substring(0, colon) else line
            val rawVal = if (colon >= 0) line.substring(colon + 1) else ""
            val value = if (rawVal.startsWith(" ")) rawVal.substring(1) else rawVal
            when (field) {
                "id" -> id = value
                "event" -> if (value.isNotBlank()) eventName = value
                "data" -> dataLines += value
            }
        }
        if (dataLines.isEmpty() && id == null && eventName == "message") return null
        return HubSseEvent(id = id, event = eventName, data = dataLines.joinToString("\n"))
    }

    fun streamEvents(
        baseUrl: String,
        lastEventId: String? = null,
        once: Boolean = false,
    ): Flow<HubSseEvent> = flow {
        val baseUri = normalizeBaseUrl(baseUrl)
        val suffix = if (once) "/api/v1/events?once=1" else "/api/v1/events"
        val prepared = prepareRequestUrl(baseUri, suffix)
        val reqBuilder = Request.Builder()
            .url(prepared.requestUrl)
            .header("User-Agent", "GameVaultAndroid/0.6.0")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("Accept-Encoding", "identity")
            .get()
        if (prepared.hostHeader != null) {
            reqBuilder.header("Host", prepared.hostHeader)
        }
        if (!lastEventId.isNullOrBlank()) {
            reqBuilder.header("Last-Event-ID", lastEventId.trim())
        }
        val call = sseClient.newCall(reqBuilder.build())
        val ctxJob = currentCoroutineContext()[Job]
        val cancelHandle = ctxJob?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("SSE Hub respondió HTTP ${resp.code}")
                val source = resp.body?.source() ?: return@use
                val frameLines = mutableListOf<String>()
                while (currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        parseSseFrame(frameLines)?.let { emit(it) }
                        frameLines.clear()
                    } else {
                        frameLines += line
                    }
                }
                if (frameLines.isNotEmpty()) {
                    parseSseFrame(frameLines)?.let { emit(it) }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            cancelHandle?.dispose()
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun requestText(
        baseUrl: String,
        path: String,
        method: String,
        jsonBody: String? = null,
    ): String {
        val request = buildRequest(baseUrl, path, method, jsonBody)
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Servidor Hub respondió HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    private fun streamToOutput(
        baseUrl: String,
        path: String,
        output: OutputStream,
        onProgress: (Long, Long) -> Unit,
    ): String {
        val request = buildRequest(baseUrl, path, "GET", null)
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Servidor Hub LAN respondió HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Respuesta vacía del servidor Hub")
            val total = body.contentLength()
            val buf = ByteArray(32 * 1024)
            var copied = 0L
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    copied += n
                    onProgress(copied, total)
                }
            }
            return filenameFromDisposition(resp.header("Content-Disposition")) ?: path.substringAfterLast('/')
        }
    }

    private fun filenameFromDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val match = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE).find(header)
        return match?.groupValues?.getOrNull(1)?.trim()
    }
}
