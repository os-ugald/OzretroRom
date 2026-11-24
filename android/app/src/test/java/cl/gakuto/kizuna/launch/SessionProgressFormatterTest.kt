package cl.gakuto.kizuna.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionProgressFormatterTest {
    @Test
    fun calculatesKnownProgressAndEta() {
        val state = SessionProgressFormatter.build(
            AuthorizedSourceDownloader.Progress(
                stage = "Descargando",
                downloaded = 500L,
                total = 1_000L,
                bytesPerSecond = 100L,
                peers = 12,
                elapsedMillis = 65_000L,
                etaSeconds = 5L,
                phase = AuthorizedSourceDownloader.Phase.DOWNLOADING,
            ),
        )

        assertEquals(50, state.percentage)
        assertEquals("50%", state.percentageText)
        assertEquals("01:05", state.elapsedText)
        assertEquals("12 pares conectados", state.peersText)
        assertEquals("menos de 1 min", state.etaText)
    }

    @Test
    fun metadataHasNoInventedPercentage() {
        val progress = AuthorizedSourceDownloader.Progress(
            stage = "Obteniendo metadatos",
            peers = 180,
            elapsedMillis = 300_000L,
            phase = AuthorizedSourceDownloader.Phase.METADATA,
        )
        val state = SessionProgressFormatter.build(progress)

        assertNull(state.percentage)
        assertEquals("Calculando…", state.percentageText)
        assertEquals("05:00", state.elapsedText)
        assertTrue(state.helperText.contains("metadatos"))
    }

    @Test
    fun percentageIsClamped() {
        assertEquals(100, SessionProgressFormatter.percentage(2_000L, 1_000L))
        assertEquals(0, SessionProgressFormatter.percentage(-1L, 1_000L))
        assertNull(SessionProgressFormatter.percentage(10L, -1L))
    }
}
