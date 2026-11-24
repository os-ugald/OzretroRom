package cl.gakuto.kizuna.catalog.sourceindex

import cl.gakuto.kizuna.data.AuthorizedSource
import cl.gakuto.kizuna.launch.DownloadSourceCandidate
import java.io.InputStream
import java.security.MessageDigest
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import org.json.JSONArray
import org.json.JSONObject

internal const val OZ_SOURCE_INDEX_SCHEMA_V1 = 1

internal enum class SourceHashScope { ROM_CONTENT, DISTRIBUTED_ARTIFACT, UNKNOWN }
internal enum class ReleaseIdentityStatus { VERIFIED_CONTENT, PROVISIONAL_METADATA_KEY }
internal enum class ArtifactContentRelation { WRAPPED, CONTENT_IDENTICAL, UNKNOWN }

internal data class SourceHashEvidence(
    val scope: SourceHashScope,
    val crc32: String?,
    val md5: String?,
    val sha1: String?,
    val sha256: String?,
)

internal data class SourceIndexGame(
    val gameId: String,
    val platform: String,
    val canonicalTitle: String,
    val canonicalTitleKey: String,
    val aliases: List<String>,
)

internal data class SourceIndexClassificationEvidence(val source: String, val status: String, val value: String)
internal data class SourceIndexTranslation(
    val sourceLanguage: String?,
    val targetLanguages: List<String>,
    val status: String,
)
internal data class SourceIndexRegionalExclusivity(val status: String, val region: String?)
internal data class SourceIndexReleaseFacets(
    val regions: List<String>,
    val languages: List<String>,
    val publicationClass: String,
    val modificationClass: String,
    val translation: SourceIndexTranslation,
    val regionalExclusivity: SourceIndexRegionalExclusivity,
    val evidenceStatus: String,
    val classificationEvidence: List<SourceIndexClassificationEvidence>,
)

internal data class SourceIndexRelease(
    val releaseId: String,
    val gameId: String,
    val platform: String,
    val identityStatus: ReleaseIdentityStatus,
    val canonicalReleaseKey: String,
    val region: String,
    val revision: String?,
    val contentIdentity: SourceHashEvidence,
    val releaseType: String,
    val headerState: String,
    val facets: SourceIndexReleaseFacets,
)

internal data class SourceIndexCandidate(
    val candidateId: String,
    val releaseId: String,
    val provider: String,
    val collection: String,
    val providerItemId: String,
    val artifactFileName: String,
    val artifactSize: Long,
    val artifactHashes: SourceHashEvidence,
    val artifactContentRelation: ArtifactContentRelation,
    val infoHash: String,
    val fileIndex: Int,
    val exactFullPath: String,
    val torrentArtifactStrategy: String?,
    val torrentArtifactPath: String?,
    val trackerUrls: List<String>,
    val providerClassifications: List<String>,
) {
    private fun magnet(): String = buildString {
        append("magnet:?xt=urn:btih:").append(infoHash)
        trackerUrls.forEach { tracker ->
            append("&tr=").append(URLEncoder.encode(tracker, Charsets.UTF_8.name()))
        }
    }

    fun toDownloadCandidate(): DownloadSourceCandidate = DownloadSourceCandidate(
        identity = candidateId,
        source = AuthorizedSource(
            value = magnet(),
            expectedFileName = artifactFileName,
            expectedSize = artifactSize,
            torrentFileIndex = fileIndex,
            torrentFilePath = exactFullPath,
            sourceLabel = provider.replaceFirstChar(Char::uppercase),
        ),
    )
}

internal data class SourceIndexMatch(
    val game: SourceIndexGame,
    val release: SourceIndexRelease,
    val candidates: List<SourceIndexCandidate>,
)

/** Separate read-only source domain; it never opens or mutates Oz's Room database. */
internal class OzSourceIndexReader private constructor(
    val platform: String,
    private val games: List<SourceIndexGame>,
    private val gamesById: Map<String, SourceIndexGame>,
    private val releases: List<SourceIndexRelease>,
    private val candidatesByRelease: Map<String, List<SourceIndexCandidate>>,
) {
    val gameCount: Int get() = games.size
    val releaseCount: Int get() = releases.size
    val candidateCount: Int get() = candidatesByRelease.values.sumOf(List<SourceIndexCandidate>::size)

    fun games(): List<SourceIndexGame> = games

    fun searchGames(normalizedQuery: String): List<SourceIndexGame> {
        val query = normalizedQuery.trim()
        if (query.isEmpty()) return games
        return games.filter { game ->
            game.canonicalTitleKey.contains(query) || game.aliases.any { it.contains(query, ignoreCase = true) }
        }
    }

    fun game(gameId: String): SourceIndexGame? = gamesById[gameId]

    fun releasesForGame(gameId: String): List<SourceIndexRelease> = releases
        .filter { it.gameId == gameId }
        .sortedWith(compareBy(SourceIndexRelease::region, SourceIndexRelease::revision, SourceIndexRelease::releaseId))

    fun candidatesForRelease(releaseId: String): List<SourceIndexCandidate> =
        candidatesByRelease[releaseId].orEmpty().sortedBy(SourceIndexCandidate::candidateId)

    fun findExact(sha256: String, scope: SourceHashScope, region: String): SourceIndexMatch? {
        if (scope == SourceHashScope.UNKNOWN) return null
        if (scope == SourceHashScope.ROM_CONTENT) {
            val release = releases.singleOrNull {
                it.region == region && it.contentIdentity.scope == SourceHashScope.ROM_CONTENT &&
                    it.contentIdentity.sha256?.equals(sha256, ignoreCase = true) == true
            } ?: return null
            return SourceIndexMatch(requireNotNull(gamesById[release.gameId]), release, candidatesByRelease[release.releaseId].orEmpty())
        }
        val matchingCandidates = candidatesByRelease.values.flatten().filter {
            it.artifactHashes.scope == SourceHashScope.DISTRIBUTED_ARTIFACT &&
                it.artifactHashes.sha256?.equals(sha256, ignoreCase = true) == true
        }
        val releaseIds = matchingCandidates.map { it.releaseId }.distinct()
        if (releaseIds.size != 1) return null
        val release = releases.singleOrNull { it.releaseId == releaseIds.single() && it.region == region } ?: return null
        return SourceIndexMatch(requireNotNull(gamesById[release.gameId]), release, matchingCandidates)
    }

    /** Exact identity query. It scans the existing parsed lists and creates no duplicate full index. */
    fun findByIds(gameId: String, releaseId: String, candidateId: String): SourceIndexMatch? {
        val game = gamesById[gameId] ?: return null
        val release = releases.singleOrNull { it.releaseId == releaseId && it.gameId == game.gameId } ?: return null
        val candidate = candidatesByRelease[release.releaseId].orEmpty()
            .singleOrNull { it.candidateId == candidateId } ?: return null
        return SourceIndexMatch(game, release, listOf(candidate))
    }

    companion object {
        fun openGzip(input: InputStream, expectedCompressedSha256: String? = null): OzSourceIndexReader {
            val compressed = input.use(InputStream::readBytes)
            expectedCompressedSha256?.let { expected ->
                require(sha256(compressed).equals(expected, ignoreCase = true)) { "Source index shard hash mismatch" }
            }
            val root = GZIPInputStream(compressed.inputStream()).bufferedReader().use { JSONObject(it.readText()) }
            require(root.getInt("schemaVersion") == OZ_SOURCE_INDEX_SCHEMA_V1) { "Unsupported source index schema" }
            val platform = root.requiredString("platform")
            val games = root.getJSONArray("games").objects().map { item ->
                SourceIndexGame(
                    gameId = item.requiredString("gameId"),
                    platform = item.requiredString("platform"),
                    canonicalTitle = item.requiredString("canonicalTitle"),
                    canonicalTitleKey = item.requiredString("canonicalTitleKey"),
                    aliases = item.optJSONArray("aliases")?.strings().orEmpty(),
                ).also { require(it.platform == platform) { "Cross-platform game in shard" } }
            }
            require(games.map { it.gameId }.distinct().size == games.size) { "Duplicate game identity in shard" }
            val gamesById = games.associateBy { it.gameId }
            val releases = root.getJSONArray("releases").objects().map { item ->
                val content = item.getJSONObject("contentIdentity").hashEvidence()
                val facets = item.getJSONObject("facets")
                val translation = facets.getJSONObject("translation")
                val exclusivity = facets.getJSONObject("regionalExclusivity")
                require(content.scope != SourceHashScope.DISTRIBUTED_ARTIFACT) { "Artifact hash cannot define release identity" }
                val identityStatus = item.enumValue<ReleaseIdentityStatus>("identityStatus")
                require((identityStatus == ReleaseIdentityStatus.VERIFIED_CONTENT) ==
                    (content.scope == SourceHashScope.ROM_CONTENT && content.hasHash())) {
                    "Release identity status contradicts content evidence"
                }
                SourceIndexRelease(
                    releaseId = item.requiredString("releaseId"), gameId = item.requiredString("gameId"),
                    platform = item.requiredString("platform"), region = item.requiredString("region"),
                    identityStatus = identityStatus, canonicalReleaseKey = item.requiredString("canonicalReleaseKey"),
                    revision = item.optionalString("revision"), contentIdentity = content,
                    releaseType = item.requiredString("releaseType"), headerState = item.requiredString("headerState"),
                    facets = SourceIndexReleaseFacets(
                        regions = facets.getJSONArray("regions").strings(),
                        languages = facets.getJSONArray("languages").strings(),
                        publicationClass = facets.requiredString("publicationClass"),
                        modificationClass = facets.requiredString("modificationClass"),
                        translation = SourceIndexTranslation(
                            sourceLanguage = translation.optionalString("sourceLanguage"),
                            targetLanguages = translation.getJSONArray("targetLanguages").strings(),
                            status = translation.requiredString("status"),
                        ),
                        regionalExclusivity = SourceIndexRegionalExclusivity(
                            status = exclusivity.requiredString("status"),
                            region = exclusivity.optionalString("region"),
                        ),
                        evidenceStatus = facets.requiredString("evidenceStatus"),
                        classificationEvidence = facets.getJSONArray("classificationEvidence").objects().map { evidence ->
                            SourceIndexClassificationEvidence(
                                source = evidence.requiredString("source"),
                                status = evidence.requiredString("status"),
                                value = evidence.requiredString("value"),
                            )
                        },
                    ),
                ).also { require(it.platform == platform) { "Cross-platform release in shard" } }
            }
            require(releases.all { it.gameId in gamesById }) { "Release references unknown game" }
            val candidates = root.getJSONArray("candidates").objects().map { item ->
                require(item.requiredString("transport") == "torrent") { "Unsupported source transport" }
                val torrent = item.getJSONObject("torrent")
                val artifact = item.getJSONObject("artifact")
                val artifactHashes = artifact.hashEvidence()
                val relation = artifact.enumValue<ArtifactContentRelation>("contentRelation")
                require(relation != ArtifactContentRelation.WRAPPED || artifactHashes.scope != SourceHashScope.ROM_CONTENT) {
                    "Wrapped artifact cannot carry ROM_CONTENT byte evidence"
                }
                SourceIndexCandidate(
                    candidateId = item.requiredString("candidateId"), releaseId = item.requiredString("releaseId"),
                    provider = item.requiredString("provider"), collection = item.requiredString("collection"),
                    providerItemId = item.requiredString("providerItemId"), artifactFileName = artifact.requiredString("fileName"),
                    artifactSize = artifact.getLong("size"), artifactHashes = artifactHashes,
                    artifactContentRelation = relation,
                    infoHash = torrent.requiredString("infoHash"), fileIndex = torrent.getInt("fileIndex"),
                    exactFullPath = torrent.requiredString("exactFullPath"),
                    torrentArtifactStrategy = torrent.optionalString("artifactStrategy"),
                    torrentArtifactPath = torrent.optionalString("artifactPath"),
                    trackerUrls = torrent.optJSONArray("trackerUrls")?.strings().orEmpty(),
                    providerClassifications = item.optJSONArray("providerClassifications")?.strings().orEmpty(),
                )
            }
            val releaseIds = releases.map { it.releaseId }.toSet()
            require(candidates.all { it.releaseId in releaseIds }) { "Candidate references unknown release" }
            val releasesById = releases.associateBy { it.releaseId }
            candidates.filter { it.artifactContentRelation == ArtifactContentRelation.CONTENT_IDENTICAL && it.artifactHashes.scope == SourceHashScope.ROM_CONTENT }
                .forEach { candidate ->
                    val release = requireNotNull(releasesById[candidate.releaseId])
                    require(release.identityStatus == ReleaseIdentityStatus.VERIFIED_CONTENT &&
                        candidate.artifactHashes.matches(release.contentIdentity)) {
                        "Content-identical artifact evidence contradicts canonical content"
                    }
                }
            return OzSourceIndexReader(platform, games, gamesById, releases, candidates.groupBy { it.releaseId })
        }

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)
private fun JSONObject.requiredString(name: String): String = getString(name).also {
    require(it.isNotBlank()) { "Missing required source index field: $name" }
}
private fun JSONObject.optionalString(name: String): String? = if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)
private fun JSONObject.hashEvidence(): SourceHashEvidence {
    val scope = runCatching { SourceHashScope.valueOf(requiredString("hashScope")) }
        .getOrElse { throw IllegalArgumentException("Unknown hash scope", it) }
    return SourceHashEvidence(scope, optionalString("crc32"), optionalString("md5"), optionalString("sha1"), optionalString("sha256"))
}
private fun SourceHashEvidence.hasHash(): Boolean = listOf(crc32, md5, sha1, sha256).any { !it.isNullOrBlank() }
private fun SourceHashEvidence.matches(other: SourceHashEvidence): Boolean {
    val pairs = listOf(crc32 to other.crc32, md5 to other.md5, sha1 to other.sha1, sha256 to other.sha256)
    val comparable = pairs.filter { (left, right) -> left != null && right != null }
    return comparable.isNotEmpty() && comparable.all { (left, right) -> left.equals(right, ignoreCase = true) }
}
private inline fun <reified T : Enum<T>> JSONObject.enumValue(name: String): T =
    runCatching { enumValueOf<T>(requiredString(name)) }.getOrElse { throw IllegalArgumentException("Unknown $name", it) }
