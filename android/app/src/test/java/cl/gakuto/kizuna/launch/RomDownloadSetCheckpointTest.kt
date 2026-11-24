package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.data.AuthorizedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RomDownloadSetCheckpointTest {
    private fun source(name: String, url: String = "https://example.invalid/$name") = AuthorizedSource(
        value = url,
        expectedFileName = name,
        expectedSize = 1024L,
    )

    @Test
    fun twoThreeAndFourDiscSetsKeepOrderedIdentity() {
        listOf(2, 3, 4).forEach { count ->
            val set = RomDownloadSet(
                parentTitle = "Game",
                editionTitle = "Game (USA)",
                region = "USA",
                format = "CHD",
                artworkUrl = null,
                items = (1..count).map { index ->
                    RomDownloadSetItem(index, "Disco $index", source("disc$index.chd"))
                },
            )
            assertEquals((1..count).toList(), set.items.map { it.discIndex })
        }
    }

    @Test
    fun setSignatureChangesWhenOneDiscSourceChanges() {
        val original = RomDownloadSet(
            "Game", "Game (USA)", "USA", "CHD", null,
            listOf(
                RomDownloadSetItem(1, "Disco 1", source("disc1.chd")),
                RomDownloadSetItem(2, "Disco 2", source("disc2.chd")),
            ),
        )
        val changed = original.copy(
            items = listOf(
                original.items[0],
                RomDownloadSetItem(2, "Disco 2", source("disc2.chd", "https://mirror.invalid/disc2.chd")),
            ),
        )
        assertNotEquals(original.signature, changed.signature)
    }

    @Test
    fun checkpointCarriesDiscFileAndByteEvidence() {
        val checkpoint = RomDownloadDiscCheckpoint(
            discIndex = 2,
            sourceSignature = "source",
            preparedFileName = "disc2.chd",
            preparedBytes = 123456L,
        )
        assertEquals(2, checkpoint.discIndex)
        assertEquals("disc2.chd", checkpoint.preparedFileName)
        assertEquals(123456L, checkpoint.preparedBytes)
    }

    @Test
    fun completedCheckpointSignatureRemainsCompatibleWithSelectedCandidate() {
        val source = source("disc1.chd")
        val item = RomDownloadSet.singleSourceItem(
            parentTitle = "Game",
            editionTitle = "Game (USA)",
            region = "USA",
            format = "CHD",
            discIndex = 1,
            discLabel = "Disco 1",
            source = source,
        )

        assertEquals(source, item.sourcePlan.selectedSource)
        assertEquals("1:${source.value}:${source.expectedFileName}", RomDownloadSet(
            "Game",
            "Game (USA)",
            "USA",
            "CHD",
            null,
            listOf(
                item,
                RomDownloadSet.singleSourceItem(
                    "Game", "Game (USA)", "USA", "CHD", 2, "Disco 2", source("disc2.chd"),
                ),
            ),
        ).signature.substringBefore('|'))
    }
}
