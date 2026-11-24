package cl.gakuto.kizuna.launch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.InstrumentationRegistry
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.OzDatabase
import java.io.File
import android.os.ParcelFileDescriptor
import java.util.Collections
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentRomLibraryRealSafDeviceTest {
    @Test
    fun externalStorageProviderCreatesAndPromotesPendingArtifactWithoutRoomPendingReference() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "am start -W -n cl.gakuto.kizuna.test/cl.gakuto.kizuna.launch.SafGrantActivity",
            ),
        ).bufferedReader().use { output -> Log.i(TAG, "pickerLaunch=${output.readText().trim()}") }
        clickWhenAvailable(instrumentation.uiAutomation.rootInActiveWindow, "USE THIS FOLDER")
        clickWhenAvailable(instrumentation.uiAutomation.rootInActiveWindow, "ALLOW", optional = true)
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download/OzSafEvidence",
        )
        val grantDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < grantDeadline && !canOpenTree(context, treeUri)) Thread.sleep(200)
        assertTrue("The system picker grant was not transferred to the target package", canOpenTree(context, treeUri))

        val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri))
        val provider = context.packageManager.resolveContentProvider(root.uri.authority.orEmpty(), 0)
        Log.i(TAG, "provider=${provider?.packageName}/${provider?.name} authority=${root.uri.authority}")
        Log.i(TAG, "treeUri=$treeUri exists=${root.exists()} directory=${root.isDirectory} write=${root.canWrite()}")
        assertTrue(root.exists())
        assertTrue(root.isDirectory)
        assertTrue(root.canWrite())

        val renameProbe = requireNotNull(root.createFile("application/octet-stream", ".oz-rename-probe.part"))
        context.contentResolver.openOutputStream(renameProbe.uri, "w")!!.use { it.write(ByteArray(4096) { 7 }) }
        val probeBefore = renameProbe.uri
        val probeBeforeLength = renameProbe.length()
        logRenameObservation(context, "before", renameProbe, probeBefore)
        val probeRenamed = renameProbe.renameTo("OzRenameProbe.gba")
        val probeAfter = renameProbe.uri
        logRenameObservation(context, "after", renameProbe, probeAfter)
        Log.i(
            TAG,
            "renameProbe success=$probeRenamed before=$probeBefore after=$probeAfter " +
                "beforeLength=$probeBeforeLength afterLength=${renameProbe.length()} " +
                "exists=${renameProbe.exists()} readable=${runCatching { context.contentResolver.openInputStream(probeAfter)?.use { true } == true }.getOrDefault(false)}",
        )
        assertTrue("Direct provider rename probe did not remain readable", probeRenamed && renameProbe.exists())
        assertTrue(renameProbe.delete())

        context.getSharedPreferences(RECORD_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val preferences = AppPreferences(context).also { it.setSystemRomTreeUri(SYSTEM_ID, treeUri) }
        val roomQueries = Collections.synchronizedList(mutableListOf<Pair<String, List<Any?>>>() )
        val database = Room.inMemoryDatabaseBuilder(context, OzDatabase::class.java)
            .setQueryCallback(
                RoomDatabase.QueryCallback { sql, args -> roomQueries += sql to args.toList() },
                Executor { command -> command.run() },
            )
            .build()
        val game = game()
        database.gameDao().insertAll(listOf(game))
        val prepared = File(context.cacheDir, "RealSafProbe-${System.nanoTime()}.gba").apply {
            outputStream().buffered().use { output ->
                val block = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
                repeat(16) { output.write(block) }
            }
        }
        val recordKey = localReleaseRecordKey(game.id, RELEASE_ID)
        val pendingKey = "pending.$recordKey"
        val records = context.getSharedPreferences(RECORD_PREFS, Context.MODE_PRIVATE)
        val library = PersistentRomLibrary(context, preferences, database)

        try {
            val stored = runCatching {
                library.persist(
                    game,
                    prepared,
                    sourceIndexProvenance = provenance(game.id),
                )
            }.onFailure { error ->
                Log.e(TAG, "persistFailure=${error::class.java.name}: ${error.message}", error)
                Log.e(TAG, "durableRecords=${records.all}")
                Log.e(TAG, "physicalChildren=${physicalChildren(root)}")
                Log.e(TAG, "roomQueries=$roomQueries")
            }.getOrThrow()

            val children = physicalChildren(root)
            val pendingUris = children.filter { it.first.startsWith(".oz-pending-") }.map { it.second }
            Log.i(TAG, "storedUri=${stored.uri} storedName=${stored.displayName} bytes=${stored.bytes}")
            Log.i(TAG, "durableRecords=${records.all}")
            Log.i(TAG, "physicalChildren=$children")
            Log.i(TAG, "roomQueries=$roomQueries")
            assertTrue("The promoted artifact is not physically readable", root.findFile(prepared.name)?.exists() == true)
            assertTrue("A pending marker remained after promotion", pendingUris.isEmpty())
            assertTrue("The pending intent was not cleared", !records.contains(pendingKey))
            assertTrue(
                "Room received a URI that still names a pending artifact",
                roomQueries.none { (_, args) -> args.any { it?.toString()?.contains(".oz-pending-") == true } },
            )
        } finally {
            database.close()
            prepared.delete()
        }
    }

    @Test
    fun externalStorageProviderDeletesOnlyOwnedAndPreservesInactiveActiveSemantics() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        launchPicker(instrumentation)
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents", "primary:Download/OzSafEvidence",
        )
        assertTrue("The system picker grant was not transferred to the target package", canOpenTree(context, treeUri))
        val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri))
        context.getSharedPreferences(RECORD_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val preferences = AppPreferences(context).also { it.setSystemRomTreeUri(SYSTEM_ID, treeUri) }
        val database = Room.inMemoryDatabaseBuilder(context, OzDatabase::class.java).build()
        val game = game().copy(id = "real-saf-delete-game")
        database.gameDao().insertAll(listOf(game))
        val library = PersistentRomLibrary(context, preferences, database)
        val originalInput = File(context.cacheDir, "PhysicalOriginal-${System.nanoTime()}.gba").apply { writeBytes(ByteArray(4096) { 1 }) }
        val translationInput = File(context.cacheDir, "PhysicalTranslation-${System.nanoTime()}.gba").apply { writeBytes(ByteArray(4096) { 2 }) }
        try {
            val originalFirst = library.persist(game, originalInput, sourceIndexProvenance = provenance(game.id).copy(releaseId = "original", candidateId = "physical-original-a"))
            originalInput.writeBytes(ByteArray(4096) { 5 })
            val original = library.persist(game, originalInput, sourceIndexProvenance = provenance(game.id).copy(releaseId = "original", candidateId = "physical-original-b"))
            assertTrue("same-release replacement did not create the new copy", original.uri != originalFirst.uri && original.uri.toString() != originalFirst.uri.toString())
            assertTrue("same-release replacement left the old copy", !DocumentFile.fromSingleUri(context, originalFirst.uri)!!.exists())
            assertTrue("same-release replacement is not readable", DocumentFile.fromSingleUri(context, original.uri)!!.exists())
            val translation = library.persist(game, translationInput, sourceIndexProvenance = provenance(game.id).copy(releaseId = "translation", candidateId = "physical-translation"))
            Log.i(TAG, "multiLocal initial=${physicalChildren(root)} room=${database.gameDao().byId(game.id)?.contentUri}")
            assertTrue(original.uri != translation.uri && original.uri.toString() != translation.uri.toString())
            assertTrue(library.deleteLocalRelease(game, "original"))
            assertTrue("inactive owned release was not deleted", !DocumentFile.fromSingleUri(context, original.uri)!!.exists())
            assertTrue("active release was affected by inactive delete", DocumentFile.fromSingleUri(context, translation.uri)!!.exists())
            assertTrue(database.gameDao().byId(game.id)?.contentUri == translation.uri.toString())
            val freshActiveGame = requireNotNull(database.gameDao().byId(game.id))
            Log.i(TAG, "activeDelete before contentUri=${freshActiveGame.contentUri} releaseUri=${translation.uri}")
            assertTrue(library.deleteLocalRelease(freshActiveGame, "translation"))
            val persistedAfterActiveDelete = database.gameDao().byId(game.id)
            Log.i(TAG, "activeDelete after contentUri=${persistedAfterActiveDelete?.contentUri} physical=${DocumentFile.fromSingleUri(context, translation.uri)?.exists()}")
            assertTrue("active delete did not clear pointer", persistedAfterActiveDelete?.contentUri == null)
            assertTrue("active delete selected another release", library.activeLocalRelease(persistedAfterActiveDelete ?: freshActiveGame) == null)

            val imported = requireNotNull(root.createFile("application/octet-stream", "PhysicalImported.gba"))
            context.contentResolver.openOutputStream(imported.uri, "w")!!.use { it.write(ByteArray(4096) { 3 }) }
            seedRecord(context, game, "imported", imported.uri, PersistentRomLibrary.LocalReleaseOwnership.LEGACY_UNKNOWN)
            assertTrue(library.deleteLocalRelease(game, "imported"))
            assertTrue("LEGACY_UNKNOWN bytes were deleted", imported.exists())
            Log.i(TAG, "ownershipPreserved uri=${imported.uri} exists=${imported.exists()} children=${physicalChildren(root)}")

            val missing = requireNotNull(root.createFile("application/octet-stream", "PhysicalOwnershipMissing.gba"))
            context.contentResolver.openOutputStream(missing.uri, "w")!!.use { it.write(ByteArray(1024) { 6 }) }
            seedRecord(context, game, "ownership-missing", missing.uri, PersistentRomLibrary.LocalReleaseOwnership.LEGACY_UNKNOWN, ownershipRaw = null)
            assertTrue(library.deleteLocalRelease(game, "ownership-missing"))
            assertTrue("missing ownership bytes were deleted", missing.exists())

            val invalid = requireNotNull(root.createFile("application/octet-stream", "PhysicalOwnershipInvalid.gba"))
            context.contentResolver.openOutputStream(invalid.uri, "w")!!.use { it.write(ByteArray(1024) { 7 }) }
            seedRecord(context, game, "ownership-invalid", invalid.uri, PersistentRomLibrary.LocalReleaseOwnership.LEGACY_UNKNOWN, ownershipRaw = "NOT_A_REAL_OWNERSHIP")
            assertTrue(library.deleteLocalRelease(game, "ownership-invalid"))
            assertTrue("invalid ownership bytes were deleted", invalid.exists())
            Log.i(TAG, "ownershipFailSafe missingExists=${missing.exists()} invalidExists=${invalid.exists()} children=${physicalChildren(root)}")
        } finally {
            database.close()
            originalInput.delete()
            translationInput.delete()
            root.findFile("PhysicalImported.gba")?.delete()
            root.findFile("PhysicalOwnershipMissing.gba")?.delete()
            root.findFile("PhysicalOwnershipInvalid.gba")?.delete()
        }
        Unit
    }

    @Test
    fun externalStorageProviderRecoversAndCleansPendingSafely() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        launchPicker(instrumentation)
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents", "primary:Download/OzSafEvidence",
        )
        assertTrue(canOpenTree(context, treeUri))
        val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri))
        val records = context.getSharedPreferences(RECORD_PREFS, Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val preferences = AppPreferences(context).also { it.setSystemRomTreeUri(SYSTEM_ID, treeUri) }
        val database = Room.inMemoryDatabaseBuilder(context, OzDatabase::class.java).build()
        val game = game().copy(id = "real-saf-pending-game")
        database.gameDao().insertAll(listOf(game))
        val library = PersistentRomLibrary(context, preferences, database)
        try {
            val valid = requireNotNull(root.createFile("application/octet-stream", "PhysicalPendingValid.gba"))
            val validBytes = ByteArray(4096) { 9 }
            context.contentResolver.openOutputStream(valid.uri, "w")!!.use { it.write(validBytes) }
            seedRecord(context, game, "pending-valid", valid.uri, PersistentRomLibrary.LocalReleaseOwnership.OZ_OWNED,
                state = PersistentRomLibrary.LocalReleaseState.PENDING, sha256 = sha256(validBytes))
            records.edit().putString("pending.${localReleaseRecordKey(game.id, "pending-valid")}", "{\"gameId\":\"${game.id}\",\"markerName\":\".oz-pending-orphan.part\"}").commit()
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("am force-stop cl.gakuto.kizuna"),
            ).use { it.readBytes() }
            val reconstructedContext = context.createPackageContext(context.packageName, 0)
            val reconstructedPreferences = AppPreferences(reconstructedContext).also { it.setSystemRomTreeUri(SYSTEM_ID, treeUri) }
            val reconstructedLibrary = PersistentRomLibrary(reconstructedContext, reconstructedPreferences, database)
            reconstructedLibrary.reconcileLocalReleases(game)
            val recovered = reconstructedLibrary.localReleasesForGame(game.id).single()
            assertTrue(recovered.state == PersistentRomLibrary.LocalReleaseState.COMPLETE && valid.exists())
            Log.i(TAG, "pendingValid reconstructedAfterForceStop=true state=${recovered.state} uri=${recovered.uri} exists=${valid.exists()} durable=${records.all}")

            val invalid = requireNotNull(root.createFile("application/octet-stream", "PhysicalPendingInvalid.gba"))
            context.contentResolver.openOutputStream(invalid.uri, "w")!!.use { it.write(ByteArray(4096) { 8 }) }
            seedRecord(context, game, "pending-invalid", invalid.uri, PersistentRomLibrary.LocalReleaseOwnership.OZ_OWNED,
                state = PersistentRomLibrary.LocalReleaseState.PENDING, sha256 = "not-the-file")
            library.reconcileLocalReleases(game)
            assertTrue("invalid PENDING bytes were retained", !invalid.exists())
            val orphan = requireNotNull(root.createFile("application/octet-stream", ".oz-pending-orphan.part"))
            context.contentResolver.openOutputStream(orphan.uri, "w")!!.use { it.write(ByteArray(32) { 4 }) }
            records.edit().putString("pending.orphan-${game.id}", "{\"gameId\":\"${game.id}\",\"markerName\":\".oz-pending-orphan.part\"}").commit()
            library.reconcileLocalReleases(game)
            assertTrue("orphan pending marker was retained", !orphan.exists())
            Log.i(TAG, "pendingCleanup invalidExists=${invalid.exists()} orphanExists=${orphan.exists()} durable=${records.all}")
        } finally {
            database.close()
            root.findFile("PhysicalPendingValid.gba")?.delete()
            root.findFile("PhysicalPendingInvalid.gba")?.delete()
            root.findFile(".oz-pending-orphan.part")?.delete()
        }
        Unit
    }

    @Test
    fun processDeathPhase1SeedsDurablePending() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        launchPicker(instrumentation)
        assertTrue(canOpenTree(context, DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Download/OzSafEvidence")))
        val treeUri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Download/OzSafEvidence")
        val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri))
        val game = game().copy(id = PROCESS_DEATH_GAME)
        val pending = requireNotNull(root.createFile("application/octet-stream", "ProcessDeathPending.gba"))
        val bytes = ByteArray(4096) { 11 }
        context.contentResolver.openOutputStream(pending.uri, "w")!!.use { it.write(bytes) }
        seedRecord(context, game, PROCESS_DEATH_RELEASE, pending.uri, PersistentRomLibrary.LocalReleaseOwnership.OZ_OWNED,
            state = PersistentRomLibrary.LocalReleaseState.PENDING, sha256 = sha256(bytes))
        val records = context.getSharedPreferences(RECORD_PREFS, Context.MODE_PRIVATE)
        records.edit()
            .putString("pending.${localReleaseRecordKey(game.id, PROCESS_DEATH_RELEASE)}", "{\"gameId\":\"${game.id}\",\"markerName\":\"$PROCESS_DEATH_MARKER\"}")
            .commit()
        val marker = requireNotNull(root.createFile("application/octet-stream", PROCESS_DEATH_MARKER))
        context.contentResolver.openOutputStream(marker.uri, "w")!!.use { it.write(ByteArray(16) { 12 }) }
        Log.i(TAG, "processDeath phase=seed uri=${pending.uri} marker=${marker.uri} durable=${records.all}")
        assertTrue(pending.exists() && marker.exists() && records.contains("pending.${localReleaseRecordKey(game.id, PROCESS_DEATH_RELEASE)}"))
    }

    @Test
    fun processDeathPhase2RecoversAfterExternalForceStop() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val treeUri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Download/OzSafEvidence")
        assertTrue(canOpenTree(context, treeUri))
        val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri))
        val records = context.getSharedPreferences(RECORD_PREFS, Context.MODE_PRIVATE)
        val game = game().copy(id = PROCESS_DEATH_GAME)
        val database = Room.inMemoryDatabaseBuilder(context, OzDatabase::class.java).build()
        database.gameDao().insertAll(listOf(game))
        val preferences = AppPreferences(context).also { it.setSystemRomTreeUri(SYSTEM_ID, treeUri) }
        val library = PersistentRomLibrary(context.createPackageContext(context.packageName, 0), preferences, database)
        val before = library.localReleasesForGame(game.id).single()
        Log.i(TAG, "processDeath phase=relaunch-before state=${before.state} uri=${before.uri} marker=${root.findFile(PROCESS_DEATH_MARKER)?.exists()} durable=${records.all}")
        assertTrue(before.state == PersistentRomLibrary.LocalReleaseState.PENDING)
        library.reconcileLocalReleases(game)
        val after = library.localReleasesForGame(game.id).single()
        Log.i(TAG, "processDeath phase=relaunch-after state=${after.state} uri=${after.uri} marker=${root.findFile(PROCESS_DEATH_MARKER)?.exists()} durable=${records.all}")
        assertTrue(after.state == PersistentRomLibrary.LocalReleaseState.COMPLETE && DocumentFile.fromSingleUri(context, after.uri)!!.exists())
        assertTrue(!records.contains("pending.${localReleaseRecordKey(game.id, PROCESS_DEATH_RELEASE)}"))
        database.close()
        root.findFile("ProcessDeathPending.gba")?.delete()
        root.findFile(PROCESS_DEATH_MARKER)?.delete()
        Unit
    }

    private fun launchPicker(instrumentation: android.app.Instrumentation) {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "am start -W -n cl.gakuto.kizuna.test/cl.gakuto.kizuna.launch.SafGrantActivity",
            ),
        ).bufferedReader().use { output -> Log.i(TAG, "pickerLaunch=${output.readText().trim()}") }
        clickWhenAvailable(instrumentation.uiAutomation.rootInActiveWindow, "USE THIS FOLDER")
        clickWhenAvailable(instrumentation.uiAutomation.rootInActiveWindow, "ALLOW", optional = true)
    }

    private fun seedRecord(
        context: Context,
        game: GameEntity,
        releaseId: String,
        uri: Uri,
        ownership: PersistentRomLibrary.LocalReleaseOwnership,
        state: PersistentRomLibrary.LocalReleaseState = PersistentRomLibrary.LocalReleaseState.COMPLETE,
        sha256: String = "",
        ownershipRaw: String? = ownership.name,
    ) {
        val raw = JSONObject().apply {
            put("schema", 3); put("gameId", game.id); put("releaseId", releaseId); put("candidateId", "physical-$releaseId")
            put("systemId", SYSTEM_ID); put("region", "World"); put("displayName", "PhysicalImported.gba"); put("uri", uri.toString())
            put("treeUri", DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Download/OzSafEvidence").toString())
            put("bytes", 4096L); put("sha256", sha256); put("savedAt", 1L); put("state", state.name)
            if (ownershipRaw != null) put("ownership", ownershipRaw)
            put("members", org.json.JSONArray())
        }.toString()
        context.getSharedPreferences(RECORD_PREFS, Context.MODE_PRIVATE).edit()
            .putString(localReleaseRecordKey(game.id, releaseId), raw).commit()
    }

    private fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { byte -> "%02x".format(byte) }

    private fun clickWhenAvailable(
        initialRoot: AccessibilityNodeInfo?,
        text: String,
        optional: Boolean = false,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        var root = initialRoot
        while (System.nanoTime() < deadline) {
            val candidate = root?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.isEnabled }
            if (candidate?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            val clickable = generateSequence(candidate) { it.parent }.firstOrNull { it.isClickable }
            if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            Thread.sleep(250)
            root = instrumentation.uiAutomation.rootInActiveWindow
        }
        if (!optional) error("DocumentsUI action '$text' was not available")
    }

    private fun physicalChildren(root: DocumentFile): List<Pair<String, String>> =
        root.listFiles().map { child -> child.name.orEmpty() to child.uri.toString() }.sortedBy { it.first }

    private fun logRenameObservation(context: Context, phase: String, file: DocumentFile, uri: Uri) {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        val direct = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) "no-row"
                else buildString {
                    for (index in 0 until cursor.columnCount) {
                        if (index > 0) append(';')
                        append(cursor.getColumnName(index)).append('=').append(cursor.getString(index))
                    }
                }
            } ?: "null-cursor"
        }.getOrElse { "error=${it::class.java.simpleName}:${it.message}" }
        Log.i(
            TAG,
            "renameObservation phase=$phase uri=$uri documentId=$documentId " +
                "name=${file.name} exists=${file.exists()} length=${file.length()} direct=[$direct]",
        )
    }

    private fun canOpenTree(context: Context, treeUri: Uri): Boolean = runCatching {
        DocumentFile.fromTreeUri(context, treeUri)?.let { it.exists() && it.isDirectory && it.canWrite() } == true
    }.getOrDefault(false)

    private fun game() = GameEntity(
        id = "real-saf-device-game",
        title = "Real SAF Device Probe",
        normalizedTitle = "real saf device probe",
        platform = SYSTEM_ID,
        systemId = SYSTEM_ID,
        year = null,
        region = "World",
        thumbnailRepo = null,
        source = "oz-source-index",
        launchValidated = false,
        coreId = null,
        virtualExtension = "gba",
    )

    private fun provenance(gameId: String) = SourceIndexProvenance(
        gameId = gameId,
        releaseId = RELEASE_ID,
        candidateId = "real-provider-candidate-a",
        provider = "device-evidence",
        infoHash = "0123456789abcdef",
        fileIndex = 0,
        exactFullPath = "RealSafProbe.gba",
        expectedSize = 4L * 1024L * 1024L,
    )

    companion object {
        private const val TAG = "OzRealSafEvidence"
        private const val RECORD_PREFS = "oz_persistent_rom_library"
        private const val APP_PREFS = "oz_retrorom"
        private const val SYSTEM_ID = "gba"
        private const val RELEASE_ID = "real-provider-original"
        private const val PROCESS_DEATH_GAME = "real-saf-process-death-game"
        private const val PROCESS_DEATH_RELEASE = "process-death-release"
        private const val PROCESS_DEATH_MARKER = ".oz-pending-process-death.part"
    }
}

class SafGrantActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val initialUri = DocumentsContract.buildDocumentUri(
                EXTERNAL_STORAGE_AUTHORITY,
                "primary:Download/OzSafEvidence",
            )
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                    )
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                },
                REQUEST_TREE,
            )
        }
    }

    @Deprecated("Legacy callback is intentional for this isolated test-only picker bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_TREE) return
        val uri = data?.data
        Log.i("OzRealSafEvidence", "pickerResult code=$resultCode uri=$uri flags=${data?.flags}")
        if (resultCode == RESULT_OK && uri != null) {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags)
            grantUriPermission(
                TARGET_PACKAGE,
                uri,
                flags or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            Log.i("OzRealSafEvidence", "pickerGrantTransferred target=$TARGET_PACKAGE uri=$uri flags=$flags")
        }
        finish()
    }

    companion object {
        private const val TARGET_PACKAGE = "cl.gakuto.kizuna"
        private const val REQUEST_TREE = 7101
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}
