package cl.gakuto.kizuna.launch

import android.content.Context
import androidx.test.InstrumentationRegistry
import cl.gakuto.kizuna.data.AuthorizedSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RomDownloadTaskPersistenceTest {
    @Test
    fun schema4TaskDecodesAsOneCandidateWithoutLosingRecoveryState() {
        val restored = RomDownloadTaskStore.decodeForTest(SCHEMA_4_TASK)

        assertNotNull(restored)
        restored!!
        assertEquals("task-existing", restored.taskId)
        assertEquals("staging-existing", restored.stagingId)
        assertEquals(1234L, restored.downloaded)
        assertEquals(8192L, restored.total)
        assertEquals(1, restored.sourcePlan.candidates.size)
        assertEquals(restored.source, restored.sourcePlan.selectedSource)
        assertEquals(listOf(1), restored.completedDiscs.map { it.discIndex })
        assertEquals("legacy-signature", restored.completedDiscs.single().sourceSignature)
        assertEquals(listOf(1, 2), restored.downloadSet!!.items.map { it.discIndex })
        assertEquals(restored.downloadSet.items[0].source, restored.downloadSet.items[0].sourcePlan.selectedSource)
        assertEquals(true, restored.unsafeLegacyPartial)
        assertEquals(emptyList<HttpCandidateRecovery>(), restored.httpRecoveries)
    }

    @Test
    fun schema6TaskRoundTripPreservesPlanValidatorAndCandidateRecovery() {
        val first = source("https://one.invalid/game.chd")
        val second = source("https://two.invalid/game.chd")
        val plan = DownloadSourcePlan(
            logicalIdentity = LogicalDownloadIdentity.forTask("game-id", "USA", first.expectedFileName, first.expectedSize),
            candidates = listOf(DownloadSourceCandidate.from(first), DownloadSourceCandidate.from(second)),
            selectedCandidateIndex = 0,
        )
        val snapshot = RomDownloadTaskSnapshot(
            taskId = "task-round-trip",
            gameId = "game-id",
            source = first,
            downloadSet = null,
            biosSources = listOf("bios.bin" to source("content://bios/bios.bin", "bios.bin")),
            resolvedRegion = "USA",
            externalLauncher = false,
            stagingId = "staging-round-trip",
            status = RomDownloadStatus.RUNNING,
            stage = "Downloading",
            downloaded = 2048L,
            total = 4096L,
            bytesPerSecond = 100L,
            peers = 0,
            fileName = "game.chd",
            error = null,
            storedUri = null,
            storedDisplayName = null,
            startedAt = 10L,
            updatedAt = 20L,
            sourcePlan = plan,
            httpRecoveries = listOf(
                HttpCandidateRecovery(
                    owner = HttpPartialOwner(plan.logicalIdentity.value, plan.selectedCandidate.identity),
                    relativePath = "http/${plan.logicalIdentity.value}/${plan.selectedCandidate.identity}/game.chd",
                    representation = HttpRepresentationState(
                        strongETag = "\"edition-1\"",
                        weakETag = null,
                        lastModified = "Sun, 06 Nov 1994 08:49:37 GMT",
                        lastModifiedStrong = false,
                        totalLength = 4096L,
                        effectiveUrl = "https://cdn.invalid/game.chd",
                    ),
                ),
            ),
        )

        val restored = RomDownloadTaskStore.decodeForTest(RomDownloadTaskStore.encodeForTest(snapshot))!!

        assertEquals(snapshot, restored)
        assertEquals(plan.candidates.map { it.identity }, restored.sourcePlan.candidates.map { it.identity })
        assertEquals(0, restored.sourcePlan.selectedCandidateIndex)
        assertEquals(first, restored.source)
        assertEquals("\"edition-1\"", restored.httpRecoveries.single().representation.strongETag)
        assertEquals(false, restored.unsafeLegacyPartial)

        assertThrows(IllegalArgumentException::class.java) {
            restored.copy(source = second)
        }
        assertThrows(IllegalArgumentException::class.java) {
            restored.copy(sourcePlan = DownloadSourcePlan.single(plan.logicalIdentity, second))
        }
    }

    @Test
    fun schema5DecodesWithoutInventingValidatorEvidence() {
        val source = source("https://one.invalid/game.chd")
        val snapshot = RomDownloadTaskSnapshot(
            taskId = "schema-5-task",
            gameId = "schema-5-game",
            source = source,
            downloadSet = null,
            biosSources = emptyList(),
            resolvedRegion = "USA",
            externalLauncher = false,
            stagingId = "schema-5-staging",
            status = RomDownloadStatus.RUNNING,
            stage = "Downloading",
            downloaded = 512L,
            total = 4096L,
            bytesPerSecond = 0L,
            peers = 0,
            fileName = "game.chd",
            error = null,
            storedUri = null,
            storedDisplayName = null,
            startedAt = 1L,
            updatedAt = 2L,
        )
        val schema5 = RomDownloadTaskStore.encodeForTest(snapshot).replace("\"schema\":6", "\"schema\":5")

        val restored = RomDownloadTaskStore.decodeForTest(schema5)!!

        assertEquals(snapshot.taskId, restored.taskId)
        assertEquals(snapshot.stagingId, restored.stagingId)
        assertEquals(snapshot.sourcePlan, restored.sourcePlan)
        assertEquals(true, restored.unsafeLegacyPartial)
        assertEquals(emptyList<HttpCandidateRecovery>(), restored.httpRecoveries)
    }

    @Test
    fun aggregateBytesExcludeInactiveAndQuarantinedCandidatePartials() {
        @Suppress("DEPRECATION")
        val context: Context = InstrumentationRegistry.getTargetContext()
        val taskStore = RomDownloadTaskStore(context)
        val first = source("https://one.invalid/game.chd")
        val second = source("https://two.invalid/game.chd")
        val plan = DownloadSourcePlan(
            LogicalDownloadIdentity.forTask("aggregate-game", "USA", "game.chd", 4096L),
            listOf(DownloadSourceCandidate.from(first), DownloadSourceCandidate.from(second)),
        )
        val snapshot = RomDownloadTaskSnapshot(
            taskId = "aggregate-task",
            gameId = "aggregate-game",
            source = first,
            downloadSet = null,
            biosSources = emptyList(),
            resolvedRegion = "USA",
            externalLauncher = false,
            stagingId = "aggregate-${System.nanoTime()}",
            status = RomDownloadStatus.RUNNING,
            stage = "Downloading",
            downloaded = 0L,
            total = 4096L,
            bytesPerSecond = 0L,
            peers = 0,
            fileName = "game.chd",
            error = null,
            storedUri = null,
            storedDisplayName = null,
            startedAt = 1L,
            updatedAt = 2L,
            sourcePlan = plan,
            httpRecoveries = plan.candidates.mapIndexed { index, candidate ->
                val owner = HttpPartialOwner(plan.logicalIdentity.value, candidate.identity)
                HttpCandidateRecovery(
                    owner,
                    candidatePartialRelativePath(owner, "game.chd"),
                    HttpRepresentationState(strongETag = "\"v$index\"", totalLength = 4096L),
                    quarantined = index == 1,
                )
            },
        )
        val files = SessionFileStore(context, snapshot.stagingId)
        files.candidateFile(snapshot.httpRecoveries[0].owner, "game.chd").writeBytes(ByteArray(3))
        files.candidateFile(snapshot.httpRecoveries[1].owner, "game.chd").writeBytes(ByteArray(7))

        try {
            assertEquals(3L, taskStore.partialBytes(snapshot))
        } finally {
            files.deleteAll()
        }
    }

    @Test
    fun biosRecoveryPersistsInsideParentTaskWithoutStandaloneBiosTask() {
        val context: Context = InstrumentationRegistry.getTargetContext()
        val taskStore = RomDownloadTaskStore(context)
        val suffix = System.nanoTime().toString()
        val parentId = "parent-arcade-$suffix"
        val biosGameId = "romgi-bios-neogeo-$suffix"
        val parentSource = source("https://example.invalid/game.zip", "game.zip")
        val biosSource = source("https://example.invalid/neogeo.zip", "neogeo.zip")
        val parent = taskStore.createOrReuse(
            gameId = parentId,
            source = parentSource,
            biosSources = listOf("neogeo" to biosSource),
            resolvedRegion = "World",
        )
        val biosPlan = DownloadSourcePlan.single(
            LogicalDownloadIdentity.forTask(biosGameId, null, biosSource.expectedFileName, biosSource.expectedSize),
            biosSource,
        )
        val scope = TransferRecoveryScope(parent.gameId, biosPlan)
        val owner = HttpPartialOwner(biosPlan.logicalIdentity.value, biosPlan.selectedCandidate.identity)
        val recovery = HttpCandidateRecovery(
            owner,
            candidatePartialRelativePath(owner, "neogeo.zip"),
            HttpRepresentationState(strongETag = "\"bios-v1\"", totalLength = biosSource.expectedSize),
        )

        try {
            assertEquals(null, taskStore.get(biosGameId))
            taskStore.httpRecoveryAccess(scope.durableTaskGameId).persist(recovery)
            val recreatedStore = RomDownloadTaskStore(context)
            assertEquals(recovery, recreatedStore.get(parentId)!!.httpRecoveries.single())
            assertEquals(null, recreatedStore.get(biosGameId))
        } finally {
            taskStore.deletePartial(parentId)
            taskStore.clear(parentId)
            taskStore.clear(biosGameId)
        }
    }

    @Test
    fun replacementGenerationIsSafeAcrossBothProcessDeathBoundaries() {
        val context: Context = InstrumentationRegistry.getTargetContext()
        val directory = File(context.cacheDir, "http-replacement-${System.nanoTime()}").apply { mkdirs() }
        val target = File(directory, "game.chd")
        val oldBytes = "representation-v1".toByteArray()
        val owner = HttpPartialOwner(
            LogicalDownloadIdentity.from("instrumented-replacement", "game").value,
            DownloadSourceCandidate.from(source("https://example.invalid/game.chd")).identity,
        )
        val oldRecovery = HttpCandidateRecovery(
            owner,
            candidatePartialRelativePath(owner, "game.chd"),
            HttpRepresentationState(strongETag = "\"v1\"", totalLength = 4096L),
        )
        val newRecovery = oldRecovery.copy(
            representation = HttpRepresentationState(strongETag = "\"v2\"", totalLength = 4096L),
        )
        var durableMetadata = oldRecovery
        target.writeBytes(oldBytes)

        try {
            val prepared = prepareHttpReplacementTarget(target)

            assertEquals(oldRecovery, durableMetadata)
            assertEquals(0L, target.length())
            assertEquals(true, prepared.preservedPrevious!!.readBytes().contentEquals(oldBytes))

            durableMetadata = newRecovery

            assertEquals(newRecovery, durableMetadata)
            assertEquals(0L, target.length())
            assertEquals(false, target.readBytes().contentEquals(oldBytes))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun source(value: String, name: String = "game.chd") = AuthorizedSource(
        value = value,
        expectedFileName = name,
        expectedSize = 4096L,
    )

    companion object {
        private val SCHEMA_4_TASK = """
            {
              "schema":4,
              "taskId":"task-existing",
              "gameId":"game-existing",
              "source":{"value":"https://example.invalid/disc1.chd","expectedFileName":"disc1.chd","torrentFileIndex":null,"torrentFilePath":null,"expectedSize":4096,"sourceLabel":"ROMgi","requiresIa":false},
              "downloadSet":{"parentTitle":"Game","editionTitle":"Game (USA)","region":"USA","format":"CHD","artworkUrl":null,"items":[
                {"discIndex":1,"discLabel":"Disc 1","source":{"value":"https://example.invalid/disc1.chd","expectedFileName":"disc1.chd","torrentFileIndex":null,"torrentFilePath":null,"expectedSize":4096,"sourceLabel":"ROMgi","requiresIa":false}},
                {"discIndex":2,"discLabel":"Disc 2","source":{"value":"https://example.invalid/disc2.chd","expectedFileName":"disc2.chd","torrentFileIndex":null,"torrentFilePath":null,"expectedSize":4096,"sourceLabel":"ROMgi","requiresIa":false}}
              ]},
              "completedDiscs":[{"discIndex":1,"sourceSignature":"legacy-signature","preparedFileName":"disc1.chd","preparedBytes":4096}],
              "biosSources":[],
              "resolvedRegion":"USA",
              "externalLauncher":false,
              "stagingId":"staging-existing",
              "status":"RUNNING",
              "stage":"Downloading",
              "downloaded":1234,
              "total":8192,
              "bytesPerSecond":64,
              "peers":0,
              "fileName":"Game.m3u",
              "error":null,
              "storedUri":null,
              "storedDisplayName":null,
              "startedAt":10,
              "updatedAt":20
            }
        """.trimIndent()
    }
}
