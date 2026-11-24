package cl.gakuto.kizuna.catalog

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import cl.gakuto.kizuna.data.GameDescriptor
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Local SQL metadata and reserved .invalid URLs only; never contacts a game source. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [25])
class Ps1CatalogResolutionTest {
    private lateinit var context: Application
    private lateinit var dbFile: File
    private val descriptor = GameDescriptor("ps1-fixture", "Orbit Fixture", "ps1", "PlayStation", "ps1", "", region = "USA")

    @Before fun seed() {
        context = RuntimeEnvironment.getApplication()
        dbFile = File(context.filesDir, "romgi-catalog/romdb.db")
        dbFile.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE entries(slug TEXT, rom_id TEXT, title TEXT, platform TEXT, boxart_url TEXT)")
            db.execSQL("CREATE TABLE links(entry TEXT, name TEXT, type TEXT, format TEXT, url TEXT, filename TEXT, host TEXT, size INTEGER)")
            db.execSQL("CREATE TABLE entry_groups(id TEXT, title TEXT, kind TEXT, member_count INTEGER)")
            db.execSQL("CREATE TABLE entry_group_members(entry TEXT, group_id TEXT, member_index INTEGER, member_label TEXT)")
            // Exercise the production catalog validity gate rather than bypassing it for tests.
            repeat(101) { n ->
                db.execSQL("INSERT INTO entries VALUES (?, NULL, ?, 'nes', NULL)", arrayOf("padding$n", "Padding $n"))
                db.execSQL("INSERT INTO links VALUES (?, 'padding', 'Game', 'nes', 'https://example.invalid/padding.nes', 'padding.nes', 'fixture', 8192)", arrayOf("padding$n"))
            }
            db.execSQL("CREATE TABLE padding(data BLOB)")
            db.execSQL("INSERT INTO padding VALUES(zeroblob(1000000))")
        }
    }

    @Test fun searchAndSingleSourcePreferAPreparableAlternative() = runBlocking {
        entry("one", "Orbit Fixture (USA)")
        link("one", "game.bin", "BIN")
        link("one", "game.7z", "CHD")
        link("one", "game.zip", "BIN/CUE")
        link("one", "game.chd", "CHD")
        val catalog = RomgiCatalog(context, allowTorrents = false, preferredRegion = "usa")
        assertEquals("game.chd", catalog.resolve(descriptor).fileName)
        val resolution = catalog.resolveEditions(descriptor)
        assertEquals(listOf("CHD"), resolution.allPlans.map { it.format })
        assertEquals("game.chd", resolution.recommended.discs.single().source.fileName)
    }

    @Test fun completeMultidiscZipChdEditionKeepsEveryExactSource() = runBlocking {
        entry("one", "Orbit Fixture (USA) (Disc 1)")
        entry("two", "Orbit Fixture (USA) (Disc 2)")
        group()
        link("one", "disc1.zip", "CHD")
        link("two", "disc2.zip", "CHD")
        val plan = RomgiCatalog(context, allowTorrents = false).resolveEditions(descriptor).recommended
        assertTrue(plan.isComplete)
        assertTrue(plan.isMultiDisc)
        assertEquals(listOf(1, 2), plan.discs.map { it.index })
        assertEquals(listOf("disc1.zip", "disc2.zip"), plan.discs.map { it.source.fileName })
    }

    @Test fun unsupportedSecondDiscMustNotReduceEditionToDiscOne() = runBlocking {
        entry("one", "Orbit Fixture (USA) (Disc 1)")
        entry("two", "Orbit Fixture (USA) (Disc 2)")
        group()
        link("one", "disc1.chd", "CHD")
        link("two", "disc2.zip", "BIN/CUE")
        assertTrue(runCatching { RomgiCatalog(context).resolveEditions(descriptor) }.isFailure)
    }

    @Test fun fallbackDiscMetadataAlsoPreventsReducingEditionToDiscOne() = runBlocking {
        entry("one", "Orbit Fixture (USA) (Disc 1)")
        entry("two", "Orbit Fixture (USA) (Disc 2)")
        link("one", "disc1.chd", "CHD")
        link("two", "disc2.zip", "BIN/CUE")
        assertTrue(runCatching { RomgiCatalog(context).resolveEditions(descriptor) }.isFailure)
    }

    private fun entry(slug: String, title: String) = write { db ->
        db.execSQL("INSERT INTO entries VALUES (?, NULL, ?, 'ps1', NULL)", arrayOf(slug, title))
    }

    private fun link(slug: String, file: String, format: String) = write { db ->
        db.execSQL("INSERT INTO links VALUES (?, 'USA', 'Game', ?, ?, ?, 'fixture', 8192)",
            arrayOf(slug, format, "https://example.invalid/$file", file))
    }

    private fun group() = write { db ->
        db.execSQL("INSERT INTO entry_groups VALUES ('orbit', 'Orbit Fixture (USA)', 'disc', 2)")
        db.execSQL("INSERT INTO entry_group_members VALUES ('one', 'orbit', 1, 'Disc 1')")
        db.execSQL("INSERT INTO entry_group_members VALUES ('two', 'orbit', 2, 'Disc 2')")
    }

    private fun write(block: (SQLiteDatabase) -> Unit) {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use(block)
    }
}
