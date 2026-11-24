package cl.gakuto.kizuna.launch

import android.app.Application
import cl.gakuto.kizuna.catalog.M3uPlaylist
import cl.gakuto.kizuna.data.AuthorizedSource
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [25])
class Ps1DiscRecoveryTest {
    @Test fun legacyPartialWithoutRecoveryMetadataKeepsExplicitControlsReachable() {
        val context = RuntimeEnvironment.getApplication()
        val tasks = RomDownloadTaskStore(context)
        val source = AuthorizedSource("https://example.invalid/legacy.zip", expectedFileName = "legacy.zip")
        val task = tasks.createOrReuse("ps1-legacy-fixture", source, emptyList(), "USA")
        val bytes = SessionFileStore(context, task.stagingId).file("legacy.zip.part")
        bytes.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(tasks.hasPartial(task.gameId))
        val failure = runCatching { tasks.createOrReuse(task.gameId,
            source.copy(value = "https://other.invalid/legacy.zip"), emptyList(), "USA") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertArrayEquals(byteArrayOf(1, 2, 3), bytes.readBytes())
        assertEquals(task, tasks.get(task.gameId))
    }

    @Test fun sameBasenameDiscsSurviveFailureAndTaskStoreReconstruction() {
        val context = RuntimeEnvironment.getApplication()
        val tasks = RomDownloadTaskStore(context)
        val items = (1..2).map { index ->
            RomDownloadSet.singleSourceItem("Fixture", "Fixture (USA)", "USA", "CHD", index, "Disc $index",
                AuthorizedSource("https://example.invalid/disc$index.zip", expectedFileName = "disc$index.zip", expectedSize = 10000L))
        }
        val set = RomDownloadSet("Fixture", "Fixture (USA)", "USA", "CHD", null, items)
        val task = tasks.createOrReuse("ps1-fixture", items.first().source, emptyList(), "USA", downloadSet = set)
        val staging = SessionFileStore(context, task.stagingId)
        val source = staging.file("game.chd")
        source.writeBytes(ByteArray(8192) { 1 })
        val first = staging.promoteDiscForCheckpoint(source, 1)
        tasks.markDiscComplete(task.gameId, items[0], first)
        assertTrue("Download bytes remain recoverable", source.isFile)

        val firstOwner = HttpPartialOwner(items[0].sourcePlan.logicalIdentity.value,
            items[0].sourcePlan.selectedCandidate.identity)
        val retainedTransport = staging.candidateFile(firstOwner, "disc1.zip")
        retainedTransport.writeBytes(ByteArray(10000) { 4 })
        tasks.httpRecoveryAccess(task.gameId).persist(HttpCandidateRecovery(firstOwner,
            retainedTransport.relativeTo(staging.directory).invariantSeparatorsPath,
            HttpRepresentationState(strongETag = "\"disc1\"", totalLength = 10000L)))
        assertEquals("Retained transport plus expanded checkpoint count as one acquired disc",
            10000L, tasks.partialBytes(tasks.get(task.gameId)!!))
        first.writeBytes(byteArrayOf(0))
        assertEquals("Invalid checkpoint falls back to the preserved transport bytes",
            10000L, tasks.partialBytes(tasks.get(task.gameId)!!))
        first.writeBytes(ByteArray(8192) { 1 })

        val partial = staging.candidateFile(HttpPartialOwner(items[1].sourcePlan.logicalIdentity.value,
            items[1].sourcePlan.selectedCandidate.identity), "disc2.zip")
        partial.writeBytes(byteArrayOf(9, 8, 7))
        tasks.markFailed(task.gameId, "Synthetic interruption on disc 2")
        val restarted = RomDownloadTaskStore(context)
        val resumed = restarted.createOrReuse(task.gameId, items[0].source, emptyList(), "USA", downloadSet = set)
        assertEquals(task.taskId, resumed.taskId)
        assertEquals(task.stagingId, resumed.stagingId)
        assertEquals(first.name, restarted.completedDisc(task.gameId, items[0])!!.preparedFileName)
        assertArrayEquals(byteArrayOf(9, 8, 7), partial.readBytes())

        source.writeBytes(ByteArray(8192) { 2 })
        val second = staging.promoteDiscForCheckpoint(source, 2)
        restarted.markDiscComplete(task.gameId, items[1], second)
        assertNotEquals(first.name, second.name)
        assertTrue(first.readBytes().all { it == 1.toByte() })
        assertTrue(second.readBytes().all { it == 2.toByte() })
        source.writeBytes(ByteArray(8192) { 3 })
        val replacement = staging.promoteDiscForCheckpoint(source, 2)
        assertNotEquals(second.name, replacement.name)
        assertTrue("Previous generation survives re-preparation", second.readBytes().all { it == 2.toByte() })
        val playlist = File(staging.directory, set.playlistName)
        playlist.writeText(M3uPlaylist.content(listOf(first.name, second.name)))
        RomPreparedPayloadValidator.validate(playlist)
        assertEquals(listOf(1, 2), restarted.get(task.gameId)!!.completedDiscs.map { it.discIndex })
        assertNull(restarted.completedDisc(task.gameId, items[1].copy(source = items[1].source.copy(
            value = "https://other.invalid/disc2.zip"), sourcePlan = DownloadSourcePlan.single(
                items[1].sourcePlan.logicalIdentity, items[1].source.copy(value = "https://other.invalid/disc2.zip")))))

        val oldTask = restarted.get(task.gameId)
        val different = items[0].source.copy(value = "https://other.invalid/disc1.zip")
        val changedItem = RomDownloadSet.singleSourceItem("Fixture", "Fixture (USA)", "USA", "CHD", 1, "Disc 1", different)
        val error = runCatching {
            restarted.createOrReuse(task.gameId, different, emptyList(), "USA",
                downloadSet = set.copy(items = listOf(changedItem, items[1])))
        }.exceptionOrNull()
        assertTrue("Changing the candidate needs an explicit partial decision", error is IllegalArgumentException)
        assertEquals(oldTask, restarted.get(task.gameId))
        assertTrue(first.isFile && second.isFile && replacement.isFile)
        assertArrayEquals(byteArrayOf(9, 8, 7), partial.readBytes())
    }
}
