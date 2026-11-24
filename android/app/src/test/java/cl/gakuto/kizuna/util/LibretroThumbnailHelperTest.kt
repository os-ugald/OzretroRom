package cl.gakuto.kizuna.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibretroThumbnailHelperTest {

    @Test
    fun `buildUrl converts spaces in repo to underscores never percent20`() {
        val url = LibretroThumbnailHelper.buildUrl(
            repo = "Nintendo - Super Nintendo Entertainment System",
            folder = "Named_Boxarts",
            name = "Super Mario World (USA)",
        )
        assertTrue(
            "Expected underscore repo segment, got: $url",
            url.contains("/Nintendo_-_Super_Nintendo_Entertainment_System/master/Named_Boxarts/"),
        )
        assertFalse(
            "Repo segment must never contain %20 spaces: $url",
            url.substringBefore("/master/").contains("%20"),
        )
        assertTrue(url.endsWith("/Super%20Mario%20World%20(USA).png"))
    }

    @Test
    fun `candidateUrls expands untagged seed titles with colon and leading article`() {
        val zelda = LibretroThumbnailHelper.candidateUrls(
            repo = null,
            systemId = "snes",
            title = "The Legend of Zelda: A Link to the Past",
        )
        assertTrue(
            "Expected article + colon transform with USA suffix, got $zelda",
            zelda.any { it.endsWith("/Legend%20of%20Zelda,%20The%20-%20A%20Link%20to%20the%20Past%20(USA).png") },
        )

        val actRaiser = LibretroThumbnailHelper.candidateUrls(
            repo = "Nintendo_-_Super_Nintendo_Entertainment_System",
            systemId = "snes",
            title = "ActRaiser",
        )
        assertTrue(
            "Expected ActRaiser (USA).png before bare ActRaiser.png",
            actRaiser.first().endsWith("/ActRaiser%20(USA).png"),
        )
    }

    @Test
    fun `illegal libretro characters are sanitized to underscores`() {
        val url = LibretroThumbnailHelper.buildUrl(
            repo = "Nintendo_-_Super_Nintendo_Entertainment_System",
            folder = "Named_Boxarts",
            name = "Spider-Man & Venom",
        )
        assertTrue(url.endsWith("/Spider-Man%20_%20Venom.png"))
    }
}
