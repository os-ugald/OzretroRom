package cl.gakuto.kizuna.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringReader

class LibretroDatParserTest {
    @Test
    fun parsesNoIntroAndFbneoFieldsInBatches() {
        val dat = """
            clrmamepro (
              name "Test"
            )
            game (
              name "ActRaiser (USA)"
              region "USA"
              rom ( name "ActRaiser (USA).sfc" size 1 crc 00000000 )
            )
            game (
              name "1941: Counter Attack (World 900227)"
              releaseyear "1990"
              rom ( name 1941.zip size 1 crc 00000000 )
            )
        """.trimIndent()
        val batches = mutableListOf<List<DatGame>>()
        val count = LibretroDatParser().parse(StringReader(dat), batchSize = 1) { batches += it }

        assertEquals(2, count)
        assertEquals(2, batches.size)
        assertEquals("USA", batches[0].single().region)
        assertEquals("ActRaiser (USA).sfc", batches[0].single().romName)
        assertNull(batches[0].single().releaseYear)
        assertEquals(1990, batches[1].single().releaseYear)
        assertEquals("1941.zip", batches[1].single().romName)
    }

    @Test
    fun exposesLazyBoundedBatchesForSuspendingConsumers() {
        val dat = (1..5).joinToString("\n") { index ->
            """
                game (
                  name "Game $index"
                )
            """.trimIndent()
        }

        val batches = LibretroDatParser().batches(StringReader(dat), batchSize = 2).toList()

        assertEquals(listOf(2, 2, 1), batches.map { it.size })
        assertEquals((1..5).map { "Game $it" }, batches.flatten().map { it.name })
    }
}
