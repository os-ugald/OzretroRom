package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.catalog.sourceindex.GenesisSourceIndexCatalog
import cl.gakuto.kizuna.catalog.sourceindex.OzSourceIndexReader
import cl.gakuto.kizuna.catalog.sourceindex.SourceIndexCatalogs
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GenesisPreDevicePlayPipelineTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun controlledArtifactUsesProductionArchiveContractWhenEvidenceIsPresent() {
        val archive = listOf(
            File(".artifacts/genesis-pre-device-play/240p-test-suite-v1.30.zip"),
            File("../.artifacts/genesis-pre-device-play/240p-test-suite-v1.30.zip"),
        ).firstOrNull(File::isFile) ?: File("missing-generated-evidence.zip")
        assumeTrue("Host evidence ZIP is generated and intentionally untracked", archive.isFile)
        assertEquals(82_432L, archive.length())
        assertEquals(ZIP_SHA256, sha256(archive))

        val targetDirectory = temporary.newFolder("prepared")
        val prepared = RomArchivePreparer.prepareZip(
            systemId = "genesis",
            platform = "Mega Drive / Genesis",
            downloaded = archive,
            targetForName = { name -> File(targetDirectory, name) },
        )

        assertEquals(NATIVE_NAME, prepared.entryName)
        assertEquals("md", prepared.file.extension)
        assertEquals(262_144L, prepared.file.length())
        assertEquals(NATIVE_SHA256, sha256(prepared.file))
    }

    @Test fun traversalEntryIsRejectedByTheSharedPreparer() {
        val archive = temporary.newFile("traversal.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.md"))
            zip.write(ByteArray(8_192) { 1 })
            zip.closeEntry()
        }

        val targetDirectory = temporary.newFolder("target")
        val failure = runCatching {
            RomArchivePreparer.prepareZip("genesis", "Mega Drive / Genesis", archive) { name ->
                File(targetDirectory, name)
            }
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertFalse(File(temporary.root.parentFile, "escape.md").exists())
    }

    @Test fun exactSourcePlanAndPreparedIdentityRemainDurableWithoutFallback() {
        val shard = listOf(
            File("src/main/assets/source-index/genesis.json.gz.bin"),
            File("app/src/main/assets/source-index/genesis.json.gz.bin"),
        ).first(File::isFile)
        val reader = shard.inputStream().use {
            OzSourceIndexReader.openGzip(it, GenesisSourceIndexCatalog.SHARD_SHA256)
        }
        val game = requireNotNull(reader.game(GAME_ID))
        val projection = GenesisSourceIndexCatalog.project(reader, game)
        val selection = GenesisSourceIndexCatalog.resolve(
            reader, projection, "world", RELEASE_ID, CANDIDATE_ID,
        )

        assertEquals("genesis", projection.systemId)
        assertTrue(projection.id.endsWith(GAME_ID))
        assertEquals(CANDIDATE_ID, selection.sourcePlan.selectedCandidate.identity)
        assertEquals(
            LogicalDownloadIdentity.from("oz-source-index-release-v1", RELEASE_ID),
            selection.sourcePlan.logicalIdentity,
        )
        assertEquals(1, selection.sourcePlan.candidates.size)
        assertEquals(0, selection.sourcePlan.selectedCandidateIndex)
        assertEquals("cf45a4f757253f0c44931d353cbc95bc3c6ef4f9", selection.candidate.infoHash)
        assertEquals(7, selection.sourcePlan.selectedSource.torrentFileIndex)
        assertEquals(EXACT_PATH, selection.sourcePlan.selectedSource.torrentFilePath)
        assertEquals(82_432L, selection.sourcePlan.selectedSource.expectedSize)
        assertEquals(selection.sourcePlan, selection.sourcePlan.toStoredSourcePlan().toSourcePlan())
        assertTrue(projection.launchValidated)
        assertTrue(GenesisSourceIndexCatalog.acquisitionEnabled(projection))
        assertEquals(GenesisSourceIndexCatalog, SourceIndexCatalogs.acquisitionForProjection(projection))
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    companion object {
        const val GAME_ID = "393411b9ec7e074469ac08acf21ba92c66a47ba7decd74b0d51fb5d54e4d60a8"
        const val RELEASE_ID = "f7e52049909d969559d78a4337cf981e92c138e039a365738fd1e19c0565e46c"
        const val CANDIDATE_ID = "9deae8309b64342026dba31215ba28df37ba3c049884e53398b21580a6dd0dcd"
        const val ZIP_SHA256 = "2099544016d8d7a6c30c7450cc121466b7e34e9af8cdf64ea6e326deb06e6ffd"
        const val NATIVE_SHA256 = "6d7dd3ae921d294bdc64d1733ff9cc82cdc996d716b68408c0fe38371b77e5b8"
        const val NATIVE_NAME = "240p Test Suite (World) (v1.30) (Program) (Aftermarket) (Unl).md"
        const val EXACT_PATH = "Minerva_Myrient/No-Intro/Sega - Mega Drive - Genesis (Aftermarket)/" +
            "240p Test Suite (World) (v1.30) (Program) (Aftermarket) (Unl).zip"
    }
}
