package cl.gakuto.kizuna.launch

import android.app.Application
import cl.gakuto.kizuna.data.AuthorizedSource
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.util.DiagnosticLogger
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Random
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real loopback HTTP transfer through the production downloader; synthetic, unplayable bytes. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [25])
class Ps1HttpAcquisitionTest {
    @Test fun partialZipResumesWithValidatorThenPreparesAndCheckpointsExactPayload() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val payload = ByteArray(16384).also { Random(42).nextBytes(it) }
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("fixture.chd"))
                zip.write(payload)
                zip.closeEntry()
            }
        }.toByteArray()
        val rangeSeen = AtomicReference<String>()
        val validatorSeen = AtomicReference<String>()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 10000 }
        val serverFailure = AtomicReference<Throwable>()
        val worker = Thread {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    check(reader.readLine().startsWith("GET /fixture.zip "))
                    val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }
                        .associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
                    rangeSeen.set(headers["range"])
                    validatorSeen.set(headers["if-range"])
                    val response = "HTTP/1.1 206 Partial Content\r\nETag: \"fixture-v1\"\r\n" +
                        "Content-Range: bytes 4096-${bytes.lastIndex}/${bytes.size}\r\n" +
                        "Content-Length: ${bytes.size - 4096}\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().use { output ->
                        output.write(response.toByteArray(Charsets.US_ASCII))
                        output.write(bytes, 4096, bytes.size - 4096)
                        output.flush()
                    }
                }
            } catch (error: Throwable) {
                serverFailure.set(error)
            }
        }.apply { isDaemon = true; start() }
        try {
            val game = GameEntity(id = "ps1-http-fixture", title = "HTTP Fixture", normalizedTitle = "http fixture",
                platform = "PlayStation", systemId = "ps1", year = null, region = "USA", thumbnailRepo = null,
                source = "fixture", launchValidated = false, coreId = null, virtualExtension = "chd")
            val source = AuthorizedSource("http://127.0.0.1:${server.localPort}/fixture.zip",
                expectedFileName = "fixture.zip", expectedSize = bytes.size.toLong())
            val tasks = RomDownloadTaskStore(context)
            val task = tasks.createOrReuse(game.id, source, emptyList(), "USA")
            val store = SessionFileStore(context, task.stagingId)
            val owner = HttpPartialOwner(task.sourcePlan.logicalIdentity.value, task.sourcePlan.selectedCandidate.identity)
            val partial = store.candidateFile(owner, "fixture.zip")
            partial.writeBytes(bytes.copyOfRange(0, 4096))
            tasks.httpRecoveryAccess(game.id).persist(HttpCandidateRecovery(owner,
                partial.relativeTo(store.directory).invariantSeparatorsPath,
                HttpRepresentationState(strongETag = "\"fixture-v1\"", totalLength = bytes.size.toLong())))
            tasks.markFailed(game.id, "Synthetic process interruption")

            val restarted = RomDownloadTaskStore(context)
            val resumed = restarted.createOrReuse(game.id, source, emptyList(), "USA")
            assertEquals(task.stagingId, resumed.stagingId)
            val diagnostics = DiagnosticLogger.startSession(context, task.stagingId)
            val downloaded = AuthorizedSourceDownloader(context, store, diagnostics).prepare(game, resumed.sourcePlan,
                restarted.httpRecoveryAccess(game.id)) { }
            assertEquals("bytes=4096-", rangeSeen.get())
            assertEquals("\"fixture-v1\"", validatorSeen.get())
            worker.join(5000)
            assertNull(serverFailure.get())
            assertArrayEquals(bytes, downloaded.readBytes())
            val prepared = RomArchivePreparer.prepare(game, downloaded, store, diagnostics)
            RomPreparedFileValidator.validate(game, prepared, diagnostics)
            val checkpoint = store.promoteDiscForCheckpoint(prepared, 1)
            assertArrayEquals(payload, checkpoint.readBytes())
            assertArrayEquals(bytes, partial.readBytes())
            assertFalse(restarted.httpRecoveryAccess(game.id).get(owner)!!.quarantined)
        } finally {
            server.close()
            worker.join(5000)
        }
    }
}
