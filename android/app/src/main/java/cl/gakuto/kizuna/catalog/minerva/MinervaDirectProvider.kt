package cl.gakuto.kizuna.catalog.minerva

import cl.gakuto.kizuna.data.AuthorizedSource
import cl.gakuto.kizuna.launch.DownloadSourceCandidate
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

internal const val MINERVA_PROVIDER = "minerva"
internal const val MINERVA_BASE_URL = "https://minerva-archive.org"

internal data class MinervaResolveRequest(
    val platformId: String,
    val canonicalFileName: String,
    val region: String,
    val expectedSha256: String? = null,
)

internal data class MinervaResolvedObject(
    val provider: String,
    val platformId: String,
    val collection: String,
    val recordId: Long,
    val canonicalFileName: String,
    val region: String,
    val size: Long,
    val crc32: String?,
    val md5: String?,
    val sha1: String?,
    val sha256: String?,
    val torrentInfoHash: String,
    val torrentFileIndex: Int,
    val torrentFilePath: String,
    val magnetUri: String,
    val torrentArtifactPath: String,
    val catalogLatencyMs: Long,
) {
    fun toCandidate(): DownloadSourceCandidate {
        val source = AuthorizedSource(
            value = magnetUri,
            expectedFileName = canonicalFileName,
            torrentFileIndex = torrentFileIndex,
            torrentFilePath = torrentFilePath,
            expectedSize = size,
            sourceLabel = "MiNERVA",
        )
        return DownloadSourceCandidate(
            identity = stableMinervaIdentity(
                provider,
                collection,
                torrentInfoHash.lowercase(),
                torrentFileIndex.toString(),
                torrentFilePath,
                size.toString(),
            ),
            source = source,
        )
    }
}

internal fun interface MinervaHttpClient {
    fun get(url: String): String
}

internal class MinervaCatalogResolver(
    private val http: MinervaHttpClient = MinervaHttpClient(::httpGet),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun resolve(request: MinervaResolveRequest): MinervaResolvedObject {
        val platform = PLATFORM_CONFIGS[request.platformId]
            ?: throw MinervaFailureException(MinervaFailure.MINERVA_COLLECTION_NOT_FOUND, "Unsupported platform")
        val started = nowMillis()
        val query = request.canonicalFileName.substringBeforeLast('.')
        val searchUrl = buildString {
            append(MINERVA_BASE_URL).append("/v1/api/rom/search?query=").append(urlEncode(query))
            append("&group=").append(urlEncode(platform.group))
            append("&console=").append(urlEncode(platform.console))
            append("&region=").append(urlEncode(request.region))
            append("&offset=0")
        }
        val search = runCatching { JSONArray(http.get(searchUrl)) }.getOrElse {
            throw MinervaFailureException(MinervaFailure.MINERVA_CATALOG_UNAVAILABLE, "Search unavailable", it)
        }
        val expectedFullPath = "./${platform.group}/${platform.collection}/${request.canonicalFileName}"
        val exact = (0 until search.length()).map(search::getJSONObject).filter { item ->
            item.getString("full_path") == expectedFullPath
        }
        if (exact.size != 1) {
            throw MinervaFailureException(
                if (exact.isEmpty()) MinervaFailure.TARGET_FILE_NOT_FOUND else MinervaFailure.MINERVA_COLLECTION_NOT_FOUND,
                "Expected one exact catalog record; found ${exact.size}",
            )
        }
        val recordId = exact.single().getLong("id")
        val detailHtml = runCatching { http.get("$MINERVA_BASE_URL/rom?id=$recordId") }.getOrElse {
            throw MinervaFailureException(MinervaFailure.MINERVA_CATALOG_UNAVAILABLE, "Record unavailable", it)
        }
        val detail = extractDetailJson(detailHtml)?.let(::JSONObject)
            ?: throw MinervaFailureException(MinervaFailure.MINERVA_CATALOG_UNAVAILABLE, "Record metadata missing")
        val fileName = detail.getString("file_name")
        val fullPath = detail.getString("full_path").removePrefix("./")
        val expectedPrefix = "${platform.group}/${platform.collection}/"
        if (fileName != request.canonicalFileName || fullPath != expectedPrefix + fileName ||
            detail.getString("console") != platform.console || detail.getString("region") != request.region
        ) {
            throw MinervaFailureException(MinervaFailure.TARGET_FILE_NOT_FOUND, "Record identity mismatch")
        }
        val sha256 = detail.optString("sha256").takeIf(String::isNotBlank)
        if (request.expectedSha256 != null && !request.expectedSha256.equals(sha256, ignoreCase = true)) {
            throw MinervaFailureException(MinervaFailure.PAYLOAD_VERIFICATION_FAILED, "Catalog hash mismatch")
        }
        val magnet = MAGNET_HREF.find(detailHtml)?.groupValues?.get(1)?.replace("&amp;", "&")
            ?: throw MinervaFailureException(MinervaFailure.MINERVA_TORRENT_UNAVAILABLE, "Magnet missing")
        val infoHash = magnet.substringAfter("urn:btih:", "").substringBefore('&').lowercase()
        if (!infoHash.matches(Regex("[a-f0-9]{40}"))) {
            throw MinervaFailureException(MinervaFailure.MINERVA_TORRENT_UNAVAILABLE, "Invalid infohash")
        }
        val torrentRoot = magnet.substringAfter("&dn=", "").substringBefore('&')
            .takeIf(String::isNotBlank)?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
            ?: throw MinervaFailureException(MinervaFailure.MINERVA_TORRENT_UNAVAILABLE, "Torrent root missing")
        return MinervaResolvedObject(
            provider = MINERVA_PROVIDER,
            platformId = platform.id,
            collection = "${platform.group}/${platform.collection}",
            recordId = recordId,
            canonicalFileName = fileName,
            region = detail.getString("region"),
            size = detail.getLong("size"),
            crc32 = detail.optional("crc32"),
            md5 = detail.optional("md5"),
            sha1 = detail.optional("sha1"),
            sha256 = sha256,
            torrentInfoHash = infoHash,
            torrentFileIndex = detail.getInt("so_id"),
            torrentFilePath = "$torrentRoot/$fullPath",
            magnetUri = magnet,
            torrentArtifactPath = detail.getString("torrents"),
            catalogLatencyMs = (nowMillis() - started).coerceAtLeast(0L),
        )
    }

    private data class PlatformConfig(val id: String, val group: String, val console: String, val collection: String)

    private companion object {
        val PLATFORM_CONFIGS = mapOf(
            "snes" to PlatformConfig(
                "snes",
                "No-Intro",
                "Super Nintendo Entertainment System",
                "Nintendo - Super Nintendo Entertainment System (Aftermarket)",
            ),
        )
        val MAGNET_HREF = Regex("<a\\s+id=\"magnet\"\\s+href=\"([^\"]+)\"")
    }
}

internal data class TorrentFileDescriptor(val index: Int, val path: String, val size: Long)
internal data class TorrentSelectionPlan(val targetIndex: Int, val active: List<Boolean>)

internal object MinervaTorrentSelection {
    fun exact(
        files: List<TorrentFileDescriptor>,
        expectedIndex: Int,
        expectedPath: String,
        expectedSize: Long,
    ): TorrentSelectionPlan {
        val indexed = files.firstOrNull { it.index == expectedIndex }
            ?: throw MinervaFailureException(MinervaFailure.TARGET_FILE_NOT_FOUND, "Torrent index missing")
        if (indexed.path != expectedPath || indexed.size != expectedSize) {
            throw MinervaFailureException(MinervaFailure.TARGET_FILE_NOT_FOUND, "Torrent coordinate mismatch")
        }
        return TorrentSelectionPlan(expectedIndex, files.map { it.index == expectedIndex })
    }
}

internal enum class MinervaFailure {
    MINERVA_CATALOG_UNAVAILABLE,
    MINERVA_COLLECTION_NOT_FOUND,
    MINERVA_TORRENT_UNAVAILABLE,
    TORRENT_METADATA_TIMEOUT,
    TARGET_FILE_NOT_FOUND,
    TRACKER_UNREACHABLE,
    NO_PEERS_OBSERVED,
    TRANSFER_TIMEOUT,
    TRANSFER_FAILED,
    PAYLOAD_VERIFICATION_FAILED,
    SUCCESS,
}

internal class MinervaFailureException(
    val failure: MinervaFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal data class MinervaSpikeDiagnostics(
    val provider: String = MINERVA_PROVIDER,
    val platform: String,
    val collection: String,
    val trackerCount: Int? = null,
    val trackerResponses: Int? = null,
    val reportedSeeders: Int? = null,
    val reportedLeechers: Int? = null,
    val connectedPeers: Int? = null,
    val failureStage: String? = null,
    val failureReason: MinervaFailure? = null,
)

private fun JSONObject.optional(name: String): String? = optString(name).takeIf(String::isNotBlank)
private fun extractDetailJson(html: String): String? {
    val marker = html.indexOf("window.rom")
    if (marker < 0) return null
    val start = html.indexOf('{', marker)
    if (start < 0) return null
    val end = html.indexOf("};", start)
    if (end < 0) return null
    return html.substring(start, end + 1)
}
private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
private fun stableMinervaIdentity(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
    .digest(parts.joinToString("|") { "${it.length}:$it" }.toByteArray())
    .joinToString("") { "%02x".format(it) }

private fun httpGet(url: String): String {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "OzRetroRom-MinervaSpike/0.1")
        require(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
