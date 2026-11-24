package cl.gakuto.kizuna.catalog.minerva

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MinervaDirectProviderTest {
    @Test fun snesMappingAndExactFilenameResolveDeterministically() {
        val resolved = resolver(search(exactResult()), detail()).resolve(request())
        assertEquals("snes", resolved.platformId)
        assertEquals(COLLECTION, resolved.collection)
        assertEquals(FILE, resolved.canonicalFileName)
        assertEquals(6, resolved.torrentFileIndex)
        assertEquals(TORRENT_PATH, resolved.torrentFilePath)
    }

    @Test fun unknownPlatformIsRejected() {
        val error = assertThrows(MinervaFailureException::class.java) {
            resolver("[]", detail()).resolve(request().copy(platformId = "n64"))
        }
        assertEquals(MinervaFailure.MINERVA_COLLECTION_NOT_FOUND, error.failure)
    }

    @Test fun exactFilenameWinsAndWrongRevisionOrRegionIsNotSubstituted() {
        val fuzzy = exactResult().replace("v1.061", "v1.03").replace("\"id\":2866663", "\"id\":1")
        val pal = exactResult().replace("NTSC", "PAL").replace("\"id\":2866663", "\"id\":2")
        val duplicateCollection = exactResult().replace(
            "Nintendo - Super Nintendo Entertainment System (Aftermarket)",
            "Nintendo - Super Nintendo Entertainment System",
        ).replace("\"id\":2866663", "\"id\":3")
        val resolved = resolver(search(fuzzy, pal, duplicateCollection, exactResult()), detail()).resolve(request())
        assertEquals(2866663L, resolved.recordId)

        val missing = assertThrows(MinervaFailureException::class.java) {
            resolver(search(fuzzy, pal), detail()).resolve(request())
        }
        assertEquals(MinervaFailure.TARGET_FILE_NOT_FOUND, missing.failure)
    }

    @Test fun exactHashWinsAndMismatchIsRejected() {
        assertEquals(SHA256, resolver(search(exactResult()), detail()).resolve(request()).sha256)
        val error = assertThrows(MinervaFailureException::class.java) {
            resolver(search(exactResult()), detail()).resolve(request().copy(expectedSha256 = "0".repeat(64)))
        }
        assertEquals(MinervaFailure.PAYLOAD_VERIFICATION_FAILED, error.failure)
    }

    @Test fun exactlyOneTorrentFileIsActive() {
        val files = listOf(
            TorrentFileDescriptor(5, "other.zip", 10),
            TorrentFileDescriptor(6, TORRENT_PATH, SIZE),
            TorrentFileDescriptor(7, "another.zip", 20),
        )
        val plan = MinervaTorrentSelection.exact(files, 6, TORRENT_PATH, SIZE)
        assertEquals(listOf(false, true, false), plan.active)
        assertEquals(1, plan.active.count { it })
    }

    @Test fun wrongTorrentIndexPathOrSizeIsRejected() {
        val files = listOf(TorrentFileDescriptor(6, TORRENT_PATH, SIZE))
        assertThrows(MinervaFailureException::class.java) { MinervaTorrentSelection.exact(files, 5, TORRENT_PATH, SIZE) }
        assertThrows(MinervaFailureException::class.java) { MinervaTorrentSelection.exact(files, 6, "wrong/$FILE", SIZE) }
        assertThrows(MinervaFailureException::class.java) { MinervaTorrentSelection.exact(files, 6, TORRENT_PATH, SIZE + 1) }
    }

    @Test fun stableTorrentCoordinatesProduceStableCandidateIdentity() {
        val first = resolver(search(exactResult()), detail(trackers = "&tr=udp%3A%2F%2Fa.invalid")).resolve(request()).toCandidate()
        val second = resolver(search(exactResult()), detail(trackers = "&tr=udp%3A%2F%2Fb.invalid")).resolve(request()).toCandidate()
        assertEquals(first.identity, second.identity)
        assertNotEquals(first.source.value, second.source.value)
    }

    @Test fun unknownPeersAreDistinctFromObservedZeroAndFailureIsRetained() {
        val unknown = MinervaSpikeDiagnostics("minerva", "snes", COLLECTION)
        val zero = unknown.copy(connectedPeers = 0, reportedSeeders = 44, failureStage = "swarm", failureReason = MinervaFailure.NO_PEERS_OBSERVED)
        assertNull(unknown.connectedPeers)
        assertEquals(0, zero.connectedPeers)
        assertEquals(44, zero.reportedSeeders)
        assertEquals(MinervaFailure.NO_PEERS_OBSERVED, zero.failureReason)
        assertTrue(zero.reportedSeeders != zero.connectedPeers)
    }

    private fun resolver(search: String, detail: String) = MinervaCatalogResolver(
        http = MinervaHttpClient { url -> if (url.contains("/search?")) search else detail },
        nowMillis = sequenceOf(100L, 125L).iterator()::next,
    )

    private fun request() = MinervaResolveRequest("snes", FILE, "World", SHA256)
    private fun search(vararg entries: String) = entries.joinToString(",", "[", "]")
    private fun exactResult() = """{"id":2866663,"full_path":"./$PATH","magnet":"magnet:?xt=urn:btih:$INFOHASH","so_id":6}"""
    private fun detail(trackers: String = "") = """
        <script>window.rom = {"id":2866663,"full_path":"./$PATH","file_name":"$FILE","size":$SIZE,"md5":"2c706355fc1d892fb887e45d43f065b1","sha1":"ca2f3eb6b0fc21e7d71528cfaf64eff62d7496c6","sha256":"$SHA256","crc32":"fb442ac9","torrents":"Minerva_Myrient_v0.3/test.torrent","so_id":6,"magnet":"magnet:?xt=urn:btih:$INFOHASH","region":"World","console":"Super Nintendo Entertainment System","manufacturer":"Nintendo","group":"No-Intro"};</script>
        <a id="magnet" href="magnet:?xt=urn:btih:$INFOHASH&amp;dn=Minerva_Myrient$trackers"></a>
    """.trimIndent()

    companion object {
        const val FILE = "240p Test Suite (World) (v1.061) (NTSC) (Program) (Aftermarket) (Unl).zip"
        const val COLLECTION = "No-Intro/Nintendo - Super Nintendo Entertainment System (Aftermarket)"
        const val PATH = "$COLLECTION/$FILE"
        const val TORRENT_PATH = "Minerva_Myrient/$PATH"
        const val SIZE = 113_992L
        const val INFOHASH = "811e19de482dc7769e1efdee9f04e3eef5f51ad5"
        const val SHA256 = "b91452606f5959b55309343676e60e49ffbd1edc0c9679c1772f274b98460d5a"
    }
}
