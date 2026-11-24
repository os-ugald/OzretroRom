package cl.gakuto.kizuna.catalog

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

class GenreDatParserTest {
    @Test
    fun parsesOfficialGenreFragment() {
        val dat = """
            game (
              comment "Sunset Riders (World)"
              genre "Beat 'em up"
            )
        """.trimIndent()

        val result = GenreDatParser().parse(StringReader(dat))

        assertEquals("Beat 'em up", result["sunset riders world"])
    }
}
