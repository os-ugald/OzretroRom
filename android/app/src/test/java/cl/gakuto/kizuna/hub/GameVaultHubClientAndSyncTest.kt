package cl.gakuto.kizuna.hub

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread

class GameVaultHubClientAndSyncTest {

    @Test
    fun `lan host policy accepts RFC1918 and dot-local mDNS names and rejects public hosts`() {
        assertTrue(GameVaultHubClient.isAllowedLanHost("192.168.1.50"))
        assertTrue(GameVaultHubClient.isAllowedLanHost("10.0.0.8"))
        assertTrue(GameVaultHubClient.isAllowedLanHost("172.16.0.1"))
        assertTrue(GameVaultHubClient.isAllowedLanHost("mi-pc-gamer.local"))
        assertTrue(GameVaultHubClient.isAllowedLanHost("192-168-100-173.lan.ozvault.local"))
        assertEquals("192.168.100.173", GameVaultHubClient.decodeSyntheticLanHost("192-168-100-173.lan.ozvault.local"))

        assertFalse(GameVaultHubClient.isAllowedLanHost("8.8.8.8"))
        assertFalse(GameVaultHubClient.isAllowedLanHost("example.com"))
        assertNull(GameVaultHubClient.decodeSyntheticLanHost("8-8-8-8.lan.ozvault.local"))
    }

    @Test
    fun `mdns helper builds valid base url and parses TXT attributes`() {
        assertEquals("http://192.168.100.173:17894", LanHubDiscovery.buildLanBaseUrl("192.168.100.173", 17894))
        assertEquals("http://desktop.local:17894", LanHubDiscovery.buildLanBaseUrl("desktop.local.", 17894))
        assertNull(LanHubDiscovery.buildLanBaseUrl("8.8.8.8", 17894))
        assertNull(LanHubDiscovery.buildLanBaseUrl("192.168.1.10", 0))

        val parsed = LanHubDiscovery.parseTxtAttributes(
            mapOf(
                "version" to "2".toByteArray(),
                "events" to "/api/v1/events".toByteArray(),
                "empty" to null,
            ),
        )
        assertEquals("2", parsed["version"])
        assertEquals("/api/v1/events", parsed["events"])
        assertFalse(parsed.containsKey("empty"))
    }

    @Test
    fun `okhttp hub client handles gzip health, incremental sync, and SSE event stream`() {
        val running = AtomicBoolean(true)
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val acceptThread = thread(isDaemon = true) {
            while (running.get()) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    sock.use { s ->
                        val bis = BufferedInputStream(s.getInputStream())
                        val headerBuf = ByteArrayOutputStream()
                        var state = 0
                        while (true) {
                            val b = bis.read()
                            if (b < 0) return@use
                            headerBuf.write(b)
                            state = when {
                                b == '\r'.code && (state == 0 || state == 2) -> state + 1
                                b == '\n'.code && (state == 1 || state == 3) -> state + 1
                                b == '\r'.code -> 1
                                else -> 0
                            }
                            if (state == 4) break
                        }
                        val lines = headerBuf.toString(StandardCharsets.ISO_8859_1).split("\r\n")
                        val requestLine = lines.firstOrNull().orEmpty()
                        val parts = requestLine.split(' ')
                        val method = parts.getOrNull(0).orEmpty()
                        val target = parts.getOrNull(1).orEmpty()
                        val reqHeaders = lines.drop(1)
                            .filter { ':' in it }
                            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
                        val contentLen = reqHeaders["content-length"]?.toIntOrNull() ?: 0
                        if (contentLen > 0) {
                            val drain = ByteArray(contentLen)
                            var read = 0
                            while (read < contentLen) {
                                val n = bis.read(drain, read, contentLen - read)
                                if (n <= 0) break
                                read += n
                            }
                        }

                        val out = s.getOutputStream()
                        when {
                            target.startsWith("/health") -> {
                                val raw = """{"status":"ok","roms_count":42,"minerva_entries":47000,"cached_covers":15}"""
                                    .toByteArray(StandardCharsets.UTF_8)
                                val gz = ByteArrayOutputStream().apply {
                                    GZIPOutputStream(this).use { it.write(raw) }
                                }.toByteArray()
                                val hdr = (
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: application/json\r\n" +
                                        "Content-Encoding: gzip\r\n" +
                                        "Content-Length: ${gz.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(StandardCharsets.US_ASCII)
                                out.write(hdr)
                                out.write(gz)
                            }
                            target.startsWith("/api/v1/sync") && method == "POST" -> {
                                val json = """{"applied":1,"latestRevision":9,"serverTime":"2026-11-24T12:00:00Z"}"""
                                    .toByteArray(StandardCharsets.UTF_8)
                                val hdr = (
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: application/json\r\n" +
                                        "Content-Length: ${json.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(StandardCharsets.US_ASCII)
                                out.write(hdr)
                                out.write(json)
                            }
                            target.startsWith("/api/v1/sync") && method == "GET" -> {
                                val json = """{"latestRevision":9,"hasMore":false,"serverTime":"2026-11-24T12:00:00Z","changes":[{"revision":9,"entityType":"game_state","entityId":"rom-smw","op":"upsert","payload":{"entryId":"rom-smw","favorite":true,"playCount":4},"updatedAt":"2026-11-24T12:00:00Z","sourceDevice":"pc-hub"}]}"""
                                    .toByteArray(StandardCharsets.UTF_8)
                                val hdr = (
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: application/json\r\n" +
                                        "Content-Length: ${json.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(StandardCharsets.US_ASCII)
                                out.write(hdr)
                                out.write(json)
                            }
                            target.startsWith("/api/v1/events") -> {
                                val sse = (
                                    "event: connected\ndata: {\"latestEventId\":2}\n\n" +
                                        "id: 2\nevent: covers.complete\ndata: {\"found\":5}\n\n"
                                    ).toByteArray(StandardCharsets.UTF_8)
                                val hdr = (
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: text/event-stream\r\n" +
                                        "Content-Length: ${sse.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(StandardCharsets.US_ASCII)
                                out.write(hdr)
                                out.write(sse)
                            }
                            else -> {
                                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            }
                        }
                        out.flush()
                    }
                }
            }
        }

        try {
            val baseUrl = "http://127.0.0.1:${server.localPort}"
            runBlocking {
                val health = GameVaultHubClient.testHealth(baseUrl)
                assertEquals(42, health.romsCount)
                assertEquals(47000, health.minervaEntries)
                assertEquals(15, health.cachedCovers)

                val push = GameVaultHubClient.pushSyncDelta(
                    baseUrl = baseUrl,
                    deviceId = "pixel-test",
                    changes = listOf(HubSyncOutboundChange(entityId = "rom-smw", favorite = true, playCount = 4)),
                )
                assertEquals(1, push.applied)
                assertEquals(9L, push.latestRevision)

                val delta = GameVaultHubClient.fetchSyncDelta(baseUrl, sinceRevision = 0L)
                assertEquals(9L, delta.latestRevision)
                assertEquals(1, delta.changes.size)
                assertEquals("rom-smw", delta.changes.first().entityId)

                val events = GameVaultHubClient.streamEvents(baseUrl, once = true).toList()
                assertEquals(2, events.size)
                assertEquals("connected", events[0].event)
                assertEquals("2", events[1].id)
                assertEquals("covers.complete", events[1].event)
                assertNotNull(events[1].data)
            }
        } finally {
            running.set(false)
            runCatching { server.close() }
            acceptThread.join(1_000)
        }
    }
}
