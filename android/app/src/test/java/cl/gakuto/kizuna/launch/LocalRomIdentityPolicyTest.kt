package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.catalog.sourceindex.GenesisSourceIndexCatalog
import cl.gakuto.kizuna.catalog.sourceindex.NesSourceIndexCatalog
import cl.gakuto.kizuna.catalog.sourceindex.OzSourceIndexReader
import cl.gakuto.kizuna.catalog.sourceindex.SnesSourceIndexCatalog
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryOrigin
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRomIdentityPolicyTest {
    @Test fun genesisStreetsSequelsExposeHistoricalCollisionButStrongIdentityRejectsFallback() {
        val reader = reader("genesis", GenesisSourceIndexCatalog.SHARD_SHA256)
        val streets = projection(reader, GenesisSourceIndexCatalog, "Streets of Rage")
        val streets2 = projection(reader, GenesisSourceIndexCatalog, "Streets of Rage 2")
        val streets3 = projection(reader, GenesisSourceIndexCatalog, "Streets of Rage 3")

        assertTrue(LocalRomIdentityPolicy.legacyPrefixMatches("streets of rage", "streets of rage 3"))
        listOf(streets, streets2, streets3).forEach {
            assertTrue(LocalRomIdentityPolicy.hasStrongSourceIndexIdentity(it))
            assertFalse(LocalRomIdentityPolicy.allowsLegacyLibraryScan(it))
        }
        assertIndependent(streets, streets3)
        assertIndependent(streets3, streets)
    }

    @Test fun realSnesMegaManCollisionCannotCrossGameIdentity() {
        val reader = reader("snes", SnesSourceIndexCatalog.SHARD_SHA256)
        val megaMan = projection(reader, SnesSourceIndexCatalog, "Mega Man")
        val megaMan7 = projection(reader, SnesSourceIndexCatalog, "Mega Man 7")
        assertTrue(LocalRomIdentityPolicy.legacyPrefixMatches("mega man", "mega man 7"))
        assertIndependent(megaMan, megaMan7)
        assertFalse(LocalRomIdentityPolicy.allowsLegacyLibraryScan(megaMan))
    }

    @Test fun realNesMarioAndMegaManSequelsRemainIndependent() {
        val reader = reader("nes", NesSourceIndexCatalog.SHARD_SHA256)
        listOf("Super Mario Bros.", "Super Mario Bros. 2", "Super Mario Bros. 3").map {
            projection(reader, NesSourceIndexCatalog, it)
        }.zipWithNext().forEach { (a, b) -> assertIndependent(a, b) }
        listOf("Mega Man", "Mega Man 2", "Mega Man 3").map {
            projection(reader, NesSourceIndexCatalog, it)
        }.zipWithNext().forEach { (a, b) -> assertIndependent(a, b) }
    }

    @Test fun sonicSequelsAndSameTitleAcrossPlatformsCannotShareLocalContent() {
        val genesis = reader("genesis", GenesisSourceIndexCatalog.SHARD_SHA256)
        val sonic1 = projection(genesis, GenesisSourceIndexCatalog, "Sonic The Hedgehog")
        val sonic2 = projection(genesis, GenesisSourceIndexCatalog, "Sonic The Hedgehog 2")
        val sonic3 = projection(genesis, GenesisSourceIndexCatalog, "Sonic The Hedgehog 3")
        assertIndependent(sonic1, sonic2)
        assertIndependent(sonic1, sonic3)

        val snes = reader("snes", SnesSourceIndexCatalog.SHARD_SHA256)
        val snesMario = projection(snes, SnesSourceIndexCatalog, "Super Mario Bros.")
        val genesisMario = projection(genesis, GenesisSourceIndexCatalog, "Super Mario Bros.")
        assertEquals(snesMario.title, genesisMario.title)
        assertFalse(snesMario.systemId == genesisMario.systemId)
        assertIndependent(snesMario, genesisMario)
    }

    @Test fun exactSameGameIdOverlayRemainsAllowedWhileDifferentGameIsIgnored() {
        val reader = reader("genesis", GenesisSourceIndexCatalog.SHARD_SHA256)
        val projection = projection(reader, GenesisSourceIndexCatalog, "Streets of Rage")
        val local = projection.copy(
            contentUri = "content://library/streets-of-rage.md",
            libraryOrigin = LibraryOrigin.DOWNLOADED.name,
        )
        val other = projection(reader, GenesisSourceIndexCatalog, "Streets of Rage 3").copy(
            contentUri = "content://library/streets-of-rage-3.md",
            libraryOrigin = LibraryOrigin.DOWNLOADED.name,
        )
        assertEquals(local, GenesisSourceIndexCatalog.mergeOverlays(listOf(projection), mapOf(projection.id to local)).single())
        assertEquals(projection, GenesisSourceIndexCatalog.mergeOverlays(listOf(projection), mapOf(other.id to other)).single())
        assertEquals(other.copy(contentUri = null, libraryOrigin = LibraryOrigin.NONE.name), GenesisSourceIndexCatalog.mergeOverlays(
            listOf(other.copy(contentUri = null, libraryOrigin = LibraryOrigin.NONE.name)),
            mapOf(projection.id to local),
        ).single())
        assertNull(projection.contentUri)
    }

    @Test fun regionalReleasesWithinOneLogicalGameKeepOneProjectionIdentity() {
        val reader = reader("snes", SnesSourceIndexCatalog.SHARD_SHA256)
        val game = requireNotNull(reader.game(SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID))
        assertTrue(reader.releasesForGame(game.gameId).map { it.releaseId }.distinct().size > 1)
        assertEquals(SnesSourceIndexCatalog.projectionId(game.gameId), SnesSourceIndexCatalog.project(reader, game).id)
    }

    @Test fun frozenShardLegacyPrefixCollisionCountsRemainVisibleAsDiagnosticRisk() {
        assertEquals(262, collisionGroupCount(reader("snes", SnesSourceIndexCatalog.SHARD_SHA256)))
        assertEquals(318, collisionGroupCount(reader("nes", NesSourceIndexCatalog.SHARD_SHA256)))
        assertEquals(170, collisionGroupCount(reader("genesis", GenesisSourceIndexCatalog.SHARD_SHA256)))
    }

    @Test fun legacyRowsRetainNarrowCompatibilityScan() {
        val legacy = game("legacy-smb", "Super Mario Bros.", "nes", "libretro")
        assertFalse(LocalRomIdentityPolicy.hasStrongSourceIndexIdentity(legacy))
        assertTrue(LocalRomIdentityPolicy.allowsLegacyLibraryScan(legacy))
    }

    @Test fun sourceIndexOriginIsStrongWithoutDependingOnProjectionIdShape() {
        val futureProjection = game("future-projection-v2:genesis:123", "Future", "genesis", "oz-source-index")
        assertTrue(LocalRomIdentityPolicy.hasStrongSourceIndexIdentity(futureProjection))
        assertFalse(LocalRomIdentityPolicy.allowsLegacyLibraryScan(futureProjection))
    }

    @Test fun sourceIndexLookingIdCannotForgeStrongOrigin() {
        val fake = game("oz-source-index-genesis-game-fake", "Fake", "genesis", "libretro")
        assertFalse(LocalRomIdentityPolicy.hasStrongSourceIndexIdentity(fake))
        assertTrue(LocalRomIdentityPolicy.allowsLegacyLibraryScan(fake))
    }

    private fun assertIndependent(a: GameEntity, b: GameEntity) {
        assertFalse(a.id == b.id)
        assertNull(a.contentUri)
        assertNull(b.contentUri)
    }

    private fun projection(
        reader: OzSourceIndexReader,
        catalog: cl.gakuto.kizuna.catalog.sourceindex.CartridgeSourceIndexCatalog,
        title: String,
    ): GameEntity = catalog.project(reader, reader.games().single { it.canonicalTitle.equals(title, ignoreCase = true) })

    private fun reader(system: String, hash: String): OzSourceIndexReader {
        val shard = listOf(
            File("src/main/assets/source-index/$system.json.gz.bin"),
            File("app/src/main/assets/source-index/$system.json.gz.bin"),
        ).first(File::isFile)
        return shard.inputStream().use { OzSourceIndexReader.openGzip(it, hash) }
    }

    private fun collisionGroupCount(reader: OzSourceIndexReader): Int {
        val keys = reader.games().map { it.canonicalTitleKey }
        return keys.count { target -> keys.any { candidate -> candidate != target && candidate.startsWith("$target ") } }
    }

    private fun game(id: String, title: String, system: String, source: String) = GameEntity(
        id = id, title = title, normalizedTitle = title.lowercase(), platform = system,
        systemId = system, year = null, region = "World", thumbnailRepo = null,
        source = source, launchValidated = false, coreId = null, virtualExtension = "rom",
        canonicalTitle = title,
    )
}
