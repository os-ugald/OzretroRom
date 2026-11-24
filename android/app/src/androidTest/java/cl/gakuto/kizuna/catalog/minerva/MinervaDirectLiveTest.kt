package cl.gakuto.kizuna.catalog.minerva

import android.content.Context
import androidx.test.InstrumentationRegistry
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.launch.AuthorizedSourceDownloader
import cl.gakuto.kizuna.launch.DownloadSourcePlan
import cl.gakuto.kizuna.launch.HttpRecoveryAccess
import cl.gakuto.kizuna.launch.LogicalDownloadIdentity
import cl.gakuto.kizuna.launch.SessionFileStore
import cl.gakuto.kizuna.util.DiagnosticLogger
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinervaDirectLiveTest {
    @Test
    fun resolvesAndSelectivelyDownloadsVerifiedSnesTestPayload() = runBlocking {
        @Suppress("DEPRECATION")
        val context: Context = InstrumentationRegistry.getTargetContext()
        val sessionId = "minerva-spike-${System.nanoTime()}"
        val store = SessionFileStore(context, sessionId)
        val diagnostics = DiagnosticLogger.startSession(context, sessionId)
        val resolved = MinervaCatalogResolver().resolve(
            MinervaResolveRequest("snes", FILE, "World", SHA256),
        )
        val candidate = resolved.toCandidate()
        val plan = DownloadSourcePlan(
            logicalIdentity = LogicalDownloadIdentity.from(
                "minerva-spike-v1",
                resolved.platformId,
                resolved.canonicalFileName,
                resolved.region,
                resolved.sha256,
            ),
            candidates = listOf(candidate),
        )
        val game = GameEntity(
            id = "minerva-spike-240p",
            title = "240p Test Suite",
            normalizedTitle = "240p test suite",
            platform = "Super Nintendo Entertainment System",
            systemId = "snes",
            year = null,
            region = "World",
            thumbnailRepo = null,
            source = "minerva",
            launchValidated = false,
            coreId = null,
            virtualExtension = "zip",
        )
        var metadataAt: Long? = null
        var firstPeerAt: Long? = null
        var firstByteAt: Long? = null
        var maxPeers = 0
        var downloadedBytes = 0L
        val transferStarted = System.currentTimeMillis()

        try {
            val file = AuthorizedSourceDownloader(context, store, diagnostics).prepare(
                game,
                plan,
                HttpRecoveryAccess.None,
            ) { progress ->
                val elapsed = System.currentTimeMillis() - transferStarted
                if (progress.phase == AuthorizedSourceDownloader.Phase.SELECTING_FILE && metadataAt == null) metadataAt = elapsed
                if (progress.peers > 0 && firstPeerAt == null) firstPeerAt = elapsed
                if (progress.downloaded > 0 && firstByteAt == null) firstByteAt = elapsed
                maxPeers = maxOf(maxPeers, progress.peers)
                downloadedBytes = maxOf(downloadedBytes, progress.downloaded)
            }
            val duration = System.currentTimeMillis() - transferStarted
            val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toHex()
            println(
                "MINERVA_EVIDENCE catalogMs=${resolved.catalogLatencyMs} metadataMs=${metadataAt ?: "UNKNOWN"} " +
                    "firstPeerMs=${firstPeerAt ?: "UNKNOWN"} firstByteMs=${firstByteAt ?: "UNKNOWN"} " +
                    "connectedPeers=$maxPeers bytes=$downloadedBytes durationMs=$duration sha256=$digest",
            )
            assertEquals(SIZE, file.length())
            assertEquals(SHA256, digest)
            assertTrue(downloadedBytes >= SIZE)
        } finally {
            store.deleteAll()
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {
        const val FILE = "240p Test Suite (World) (v1.061) (NTSC) (Program) (Aftermarket) (Unl).zip"
        const val SIZE = 113_992L
        const val SHA256 = "b91452606f5959b55309343676e60e49ffbd1edc0c9679c1772f274b98460d5a"
    }
}
