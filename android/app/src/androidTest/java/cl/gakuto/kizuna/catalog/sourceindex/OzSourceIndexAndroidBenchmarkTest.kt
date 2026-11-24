package cl.gakuto.kizuna.catalog.sourceindex

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.test.InstrumentationRegistry
import cl.gakuto.kizuna.BuildConfig
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OzSourceIndexAndroidBenchmarkTest {
    @Test
    fun benchmarkProductionReaderOnce() {
        val context = instrumentationContext()
        val runtime = Runtime.getRuntime()
        runtime.gc()
        Thread.sleep(BASELINE_SETTLE_MS)
        val gcBefore = gcStats()
        val heapBefore = managedHeap(runtime)
        val pssBeforeKb = pssKb()

        val started = SystemClock.elapsedRealtimeNanos()
        val reader = context.assets.open(ASSET).use { input ->
            OzSourceIndexReader.openGzip(input, SHA256)
        }
        val openMs = elapsedMs(started)
        assertEquals("snes", reader.platform)

        val heapAfter = managedHeap(runtime)
        val pssAfterKb = pssKb()
        val gcAfter = gcStats()
        val result = commonResult("PRODUCTION_READER").apply {
            put("productionReaderMemoryIsolated", true)
            put("diagnosticMemoryExcludedFromFormatDecision", true)
            put("compressedByteArrayRetainedByHarness", false)
            put("productionCandidateLookup", "NOT_IMPLEMENTED")
            put("productionReaderOpenMs", openMs)
            put("managedHeapBytes", JSONObject().apply {
                put("before", heapBefore)
                put("after", heapAfter)
                put("delta", heapAfter - heapBefore)
                put("observedPeak", "UNKNOWN")
            })
            put("processPssKb", JSONObject().apply {
                put("before", pssBeforeKb)
                put("after", pssAfterKb)
                put("delta", pssAfterKb - pssBeforeKb)
            })
            put("gc", JSONObject().apply {
                put("baselineCapturedAfterDeliberateGc", true)
                put("countDelta", delta(gcBefore.count, gcAfter.count) ?: "UNKNOWN")
                put("timeMsDelta", delta(gcBefore.timeMs, gcAfter.timeMs) ?: "UNKNOWN")
            })
            put("integrity", JSONObject().apply {
                put("platform", reader.platform)
                put("platformExpected", "snes")
                put("games", "NOT_EXPOSED_BY_READER")
                put("releases", "NOT_EXPOSED_BY_READER")
                put("candidates", "NOT_EXPOSED_BY_READER")
            })
        }
        println("SOURCE_INDEX_R1_PRODUCTION=${result}")
    }

    @Test
    fun benchmarkDiagnosticPipelineOnce() {
        val compressed = instrumentationContext().assets.open(ASSET).use { it.readBytes() }
        assertEquals(COMPRESSED_BYTES, compressed.size)
        assertEquals(SHA256, sha256(compressed))

        val decompressStart = SystemClock.elapsedRealtimeNanos()
        val uncompressed = GZIPInputStream(compressed.inputStream()).use { it.readBytes() }
        val decompressionMs = elapsedMs(decompressStart)
        assertEquals(UNCOMPRESSED_BYTES, uncompressed.size)

        val parseStart = SystemClock.elapsedRealtimeNanos()
        val root = JSONObject(uncompressed.toString(Charsets.UTF_8))
        val parseMs = elapsedMs(parseStart)
        assertEquals(GAMES, root.getJSONArray("games").length())
        assertEquals(RELEASES, root.getJSONArray("releases").length())
        val candidates = root.getJSONArray("candidates")
        assertEquals(CANDIDATES, candidates.length())

        val indexStart = SystemClock.elapsedRealtimeNanos()
        val byCandidateId = HashMap<String, JSONObject>(candidates.length() * 2)
        repeat(candidates.length()) { position ->
            val candidate = candidates.getJSONObject(position)
            byCandidateId[candidate.getString("candidateId")] = candidate
        }
        val indexMs = elapsedMs(indexStart)
        val firstStart = SystemClock.elapsedRealtimeNanos()
        assertNotNull(byCandidateId[FIRST_ID])
        val firstLookupMs = elapsedMs(firstStart)
        assertNotNull(byCandidateId[MIDDLE_ID])
        assertNotNull(byCandidateId[LAST_ID])
        val repeatedStart = SystemClock.elapsedRealtimeNanos()
        repeat(10_000) { assertNotNull(byCandidateId[MIDDLE_ID]) }
        val repeatedLookupMs = elapsedMs(repeatedStart)

        val result = commonResult("DIAGNOSTIC_ONLY").apply {
            put("candidateLookup", "androidTestPrototypeCandidateLookup")
            put("uncompressedBytes", uncompressed.size)
            put("decompressionMs", decompressionMs)
            put("jsonParseMs", parseMs)
            put("prototypeCandidateIndexMs", indexMs)
            put("firstCandidateLookupMs", firstLookupMs)
            put("repeated10000LookupMs", repeatedLookupMs)
            put("integrity", JSONObject().apply {
                put("games", GAMES)
                put("releases", RELEASES)
                put("candidates", CANDIDATES)
                put("firstMiddleLastCandidateIds", "PASS")
            })
        }
        println("SOURCE_INDEX_R1_DIAGNOSTIC=${result}")
    }

    private fun commonResult(mode: String) = JSONObject().apply {
        put("schemaVersion", 2)
        put("mode", mode)
        put("device", JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("androidRelease", Build.VERSION.RELEASE)
            put("api", Build.VERSION.SDK_INT)
            put("appVersion", BuildConfig.VERSION_NAME)
        })
        put("shard", JSONObject().apply {
            put("asset", ASSET)
            put("compressedBytes", COMPRESSED_BYTES)
            put("sha256", SHA256)
        })
        put("clock", "SystemClock.elapsedRealtimeNanos")
        put("oneInvocationOneSample", true)
    }

    @Suppress("DEPRECATION")
    private fun instrumentationContext(): Context = InstrumentationRegistry.getInstrumentation().context
    private fun managedHeap(runtime: Runtime) = runtime.totalMemory() - runtime.freeMemory()
    private fun pssKb(): Long = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss.toLong()
    private fun elapsedMs(start: Long) = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun gcStats(): GcStats {
        val stats = Debug.getRuntimeStats()
        return GcStats(stats["art.gc.gc-count"]?.toLongOrNull(), stats["art.gc.gc-time"]?.toLongOrNull())
    }
    private fun delta(before: Long?, after: Long?): Long? =
        if (before == null || after == null) null else after - before

    private data class GcStats(val count: Long?, val timeMs: Long?)

    private companion object {
        const val ASSET = "source-index-benchmark/snes.json.gz.bin"
        const val COMPRESSED_BYTES = 1_477_037
        const val UNCOMPRESSED_BYTES = 20_247_331
        const val SHA256 = "320e6f424f2ae5b5f01c58efc495d80f16a25d8106a74bbd3934cbc2935d897e"
        const val GAMES = 2_505
        const val RELEASES = 4_764
        const val CANDIDATES = 4_874
        const val BASELINE_SETTLE_MS = 200L
        const val FIRST_ID = "0041b1f9bb93930cc03806d4609222b190165116d341d526f25ad32461b549aa"
        const val MIDDLE_ID = "8251cd339744d967281294ca335d28ea2b6314812e34bb7dac53f5ad135c82cf"
        const val LAST_ID = "ffdf9ad00b7fb29669b4e3c755a1312301eafb5d16d74aea9e9ab1de66b915c5"
    }
}
