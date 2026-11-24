package cl.gakuto.kizuna.catalog.sourceindex

import cl.gakuto.kizuna.data.GameEntity
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OzSourceIndexReaderTest {
    @Test fun knownReleaseOpensQueriesAndMapsToExistingCandidate() {
        val compressed = gzip(SHARD)
        val openedAt = System.nanoTime()
        val reader = OzSourceIndexReader.openGzip(compressed.inputStream(), sha256(compressed))
        val openMs = (System.nanoTime() - openedAt) / 1_000_000.0
        val firstAt = System.nanoTime()
        val match = reader.findExact(SHA256, SourceHashScope.DISTRIBUTED_ARTIFACT, "World")
        val firstMicros = (System.nanoTime() - firstAt) / 1_000.0
        val nextAt = System.nanoTime()
        val again = reader.findExact(SHA256, SourceHashScope.DISTRIBUTED_ARTIFACT, "World")
        val nextMicros = (System.nanoTime() - nextAt) / 1_000.0
        println("SOURCE_INDEX_READER openMs=$openMs firstQueryUs=$firstMicros subsequentQueryUs=$nextMicros")
        assertEquals("snes", reader.platform)
        assertNotNull(match)
        assertEquals(match, again)
        val candidate = match!!.candidates.single().toDownloadCandidate()
        assertEquals("candidate-1", candidate.identity)
        assertEquals(6, candidate.source.torrentFileIndex)
        assertEquals(PATH, candidate.source.torrentFilePath)
        assertTrue(candidate.source.value.contains("tracker.example.invalid"))
        assertEquals("MINERVA_ASSET_PATH", match.candidates.single().torrentArtifactStrategy)
        assertEquals("verified/path.torrent", match.candidates.single().torrentArtifactPath)
    }

    @Test fun wrongRegionOrHashDoesNotSubstitute() {
        val reader = OzSourceIndexReader.openGzip(gzip(SHARD).inputStream())
        assertNull(reader.findExact(SHA256, SourceHashScope.DISTRIBUTED_ARTIFACT, "USA"))
        assertNull(reader.findExact("0".repeat(64), SourceHashScope.DISTRIBUTED_ARTIFACT, "World"))
        assertNull(reader.findExact(SHA256, SourceHashScope.ROM_CONTENT, "World"))
        assertNull(reader.findExact(SHA256, SourceHashScope.UNKNOWN, "World"))
    }

    @Test fun wrongShardHashUnsupportedSchemaAndMissingFieldFailClosed() {
        val compressed = gzip(SHARD)
        assertThrows(IllegalArgumentException::class.java) { OzSourceIndexReader.openGzip(compressed.inputStream(), "0".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { OzSourceIndexReader.openGzip(gzip(SHARD.replace("\"schemaVersion\":1", "\"schemaVersion\":2")).inputStream()) }
        assertThrows(Exception::class.java) { OzSourceIndexReader.openGzip(gzip(SHARD.replace("\"region\":\"World\",", "")).inputStream()) }
        assertThrows(IllegalArgumentException::class.java) {
            OzSourceIndexReader.openGzip(gzip(SHARD.replace("DISTRIBUTED_ARTIFACT", "ROM_CONTENT")).inputStream())
        }
    }

    @Test fun contentIdenticalRawArtifactMaySupplyVerifiedRomContentEvidence() {
        val raw = SHARD
            .replace("\"identityStatus\":\"PROVISIONAL_METADATA_KEY\"", "\"identityStatus\":\"VERIFIED_CONTENT\"")
            .replace("\"hashScope\":\"UNKNOWN\",\"crc32\":null,\"md5\":null,\"sha1\":null,\"sha256\":null", "\"hashScope\":\"ROM_CONTENT\",\"crc32\":null,\"md5\":null,\"sha1\":null,\"sha256\":\"$SHA256\"")
            .replace("\"contentRelation\":\"WRAPPED\",\"hashScope\":\"DISTRIBUTED_ARTIFACT\"", "\"contentRelation\":\"CONTENT_IDENTICAL\",\"hashScope\":\"ROM_CONTENT\"")
        val reader = OzSourceIndexReader.openGzip(gzip(raw).inputStream())
        val match = reader.findExact(SHA256, SourceHashScope.ROM_CONTENT, "World")
        assertNotNull(match)
        assertEquals(ReleaseIdentityStatus.VERIFIED_CONTENT, match!!.release.identityStatus)
        assertEquals(ArtifactContentRelation.CONTENT_IDENTICAL, match.candidates.single().artifactContentRelation)
    }

    @Test fun exactIdsResolveWithoutFacetOrProviderSubstitution() {
        val exactShard = SHARD
            .replace("game-1", SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID)
            .replace("release-1", SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID)
            .replace("candidate-1", SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID)
        val reader = OzSourceIndexReader.openGzip(gzip(exactShard).inputStream())
        val match = reader.findByIds(
            SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID,
            SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID,
            SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID,
        )

        assertNotNull(match)
        assertEquals("240p Test Suite", match!!.game.canonicalTitle)
        assertNull(reader.findByIds("unknown", SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID, SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID))
        assertNull(reader.findByIds(SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID, "unknown", SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID))
        assertNull(reader.findByIds(SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID, SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID, "unknown"))
    }

    @Test fun firstPlayCreatesOneExactCandidateAndNeverAdvances() {
        val exactShard = SHARD
            .replace("game-1", SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID)
            .replace("release-1", SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID)
            .replace("candidate-1", SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID)
        val reader = OzSourceIndexReader.openGzip(gzip(exactShard).inputStream())
        val selection = SnesSourceIndexCatalog.resolve(
            reader,
            game(),
            preferredRegion = "usa",
            releaseId = SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID,
            candidateId = SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID,
        )

        assertEquals(SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID, selection.game.gameId)
        assertEquals(SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID, selection.release.releaseId)
        assertEquals(SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID, selection.candidate.candidateId)
        assertEquals(1, selection.sourcePlan.candidates.size)
        assertEquals(0, selection.sourcePlan.selectedCandidateIndex)
        assertEquals(SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID, selection.sourcePlan.selectedCandidate.identity)
        assertTrue(selection.sourcePlan.selectedSource.value.contains("811e19de482dc7769e1efdee9f04e3eef5f51ad5"))
        assertEquals(6, selection.sourcePlan.selectedSource.torrentFileIndex)
        assertEquals(PATH, selection.sourcePlan.selectedSource.torrentFilePath)
    }

    @Test fun releaseFacetsRemainMetadataAndDoNotChangeExactIdentity() {
        val exactShard = SHARD
            .replace("game-1", SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID)
            .replace("release-1", SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID)
            .replace("candidate-1", SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID)
            .replace("\"publicationClass\":\"AFTERMARKET\"", "\"publicationClass\":\"HOMEBREW\"")
        val match = OzSourceIndexReader.openGzip(gzip(exactShard).inputStream()).findByIds(
            SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID,
            SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID,
            SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID,
        )!!

        assertEquals(SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID, match.game.gameId)
        assertEquals(SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID, match.release.releaseId)
        assertEquals(SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID, match.candidates.single().candidateId)
    }

    private fun game() = GameEntity(
        id = SnesSourceIndexCatalog.LEGACY_FIRST_PLAY_PROJECTION_ID,
        title = "240p Test Suite",
        normalizedTitle = "240p test suite",
        platform = "Super Nintendo",
        systemId = "snes",
        year = null,
        region = "World",
        thumbnailRepo = null,
        source = "oz-source-index",
        launchValidated = true,
        coreId = "snes9x_libretro_android.so",
        virtualExtension = "sfc",
        canonicalTitle = "240p Test Suite",
    )

    private fun gzip(value: String): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).bufferedWriter().use { it.write(value) }
    }.toByteArray()
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val SHA256 = "b91452606f5959b55309343676e60e49ffbd1edc0c9679c1772f274b98460d5a"
        const val PATH = "Minerva_Myrient/No-Intro/Nintendo - Super Nintendo Entertainment System (Aftermarket)/240p.zip"
        val SHARD = """{"schemaVersion":1,"platform":"snes","games":[{"gameId":"game-1","platform":"snes","canonicalTitle":"240p Test Suite","canonicalTitleKey":"240p test suite","aliases":[],"identityStatus":"PROVISIONAL_TITLE_KEY"}],"releases":[{"releaseId":"release-1","gameId":"game-1","platform":"snes","identityStatus":"PROVISIONAL_METADATA_KEY","canonicalReleaseKey":"240p-test-suite-world-v1.061-aftermarket-program","region":"World","revision":"v1.061","releaseType":"aftermarket","facets":{"regions":["World"],"languages":[],"publicationClass":"AFTERMARKET","modificationClass":"ORIGINAL","translation":{"sourceLanguage":null,"targetLanguages":[],"status":"UNKNOWN"},"regionalExclusivity":{"status":"UNKNOWN","region":null},"evidenceStatus":"PROVIDER_DERIVED","classificationEvidence":[{"source":"MINERVA_CANONICAL_FILENAME","status":"PROVIDER_DERIVED","value":"240p Test Suite (World)"}]},"headerState":"unknown","contentIdentity":{"hashScope":"UNKNOWN","crc32":null,"md5":null,"sha1":null,"sha256":null}}],"candidates":[{"candidateId":"candidate-1","releaseId":"release-1","provider":"minerva","transport":"torrent","providerItemId":"2866663","collection":"No-Intro/Nintendo - Super Nintendo Entertainment System (Aftermarket)","providerClassifications":["Aftermarket"],"artifact":{"fileName":"240p.zip","size":113992,"contentRelation":"WRAPPED","hashScope":"DISTRIBUTED_ARTIFACT","crc32":"fb442ac9","md5":null,"sha1":null,"sha256":"$SHA256"},"firstSeen":"2026-08-22T00:00:00Z","lastSeen":"2026-08-22T00:00:00Z","sourceFreshness":"verified","torrent":{"infoHash":"811e19de482dc7769e1efdee9f04e3eef5f51ad5","fileIndex":6,"exactFullPath":"$PATH","artifactStrategy":"MINERVA_ASSET_PATH","artifactPath":"verified/path.torrent","trackerUrls":["udp://tracker.example.invalid:80/announce"]}}]}"""
    }
}
