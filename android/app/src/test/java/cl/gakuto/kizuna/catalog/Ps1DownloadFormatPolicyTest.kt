package cl.gakuto.kizuna.catalog

import org.junit.Assert.*
import org.junit.Test

class Ps1DownloadFormatPolicyTest {
    @Test fun directAndDeclaredZipPayloadsAreEligible() {
        listOf("chd", "ISO", "pbp").forEach {
            assertTrue(Ps1DownloadFormatPolicy.canPrepare("game.$it", it))
            assertTrue(Ps1DownloadFormatPolicy.canPrepare("game.ZIP", " $it "))
        }
    }

    @Test fun containersMustNotMasqueradeAsPlayablePayloads() {
        listOf("cue", "bin", "bin/cue", "gdi", "zip", "unknown", "").forEach {
            assertFalse(Ps1DownloadFormatPolicy.canPrepare("game.zip", it))
        }
        listOf("bin", "cue", "m3u", "7z", "exe", "").forEach {
            assertFalse(Ps1DownloadFormatPolicy.canPrepare("game.$it", "chd"))
        }
    }
}
