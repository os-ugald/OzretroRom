package cl.gakuto.kizuna.launch

import android.app.Application
import android.database.Cursor
import android.database.MatrixCursor
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.room.RoomDatabase
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryOrigin
import cl.gakuto.kizuna.data.OzDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [25])
class PersistentRomLibraryBehaviorTest {
    private lateinit var context: Application
    private lateinit var database: OzDatabase
    private lateinit var preferences: AppPreferences
    private lateinit var records: android.content.SharedPreferences
    private lateinit var documents: SyntheticDocumentsProvider
    private val roomQueries = Collections.synchronizedList(mutableListOf<RoomQuery>())

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(RECORD_PREFS, Application.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(APP_PREFS, Application.MODE_PRIVATE).edit().clear().commit()
        records = context.getSharedPreferences(RECORD_PREFS, Application.MODE_PRIVATE)
        preferences = AppPreferences(context)
        roomQueries.clear()
        database = Room.inMemoryDatabaseBuilder(context, OzDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                RoomDatabase.QueryCallback { sql, bindArgs ->
                    roomQueries += RoomQuery(sql, bindArgs.toList())
                },
                Executor { command -> command.run() },
            )
            .build()
        shadowOf(context).grantPermissions(android.Manifest.permission.MANAGE_DOCUMENTS)
        documents = Robolectric.buildContentProvider(SyntheticDocumentsProvider::class.java)
            .create(
                ProviderInfo().apply {
                    authority = SyntheticDocumentsProvider.AUTHORITY
                    readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
                    writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
                    exported = true
                    grantUriPermissions = true
                },
            )
            .get()
        ShadowContentResolver.registerProviderInternal(SyntheticDocumentsProvider.AUTHORITY, documents)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun case1_originalAndTranslationCoexistForSameGame() {
        val game = game()
        seedRecord(game.id, "original", "candidate-original", documents.createFile("Original.gba", "original"))
        seedRecord(game.id, "translation", "candidate-translation", documents.createFile("Translation.gba", "translation"))

        val releases = library().localReleasesForGame(game.id)

        assertEquals(listOf("original", "translation"), releases.map { it.releaseId })
        assertEquals(listOf("candidate-original", "candidate-translation"), releases.map { it.candidateId })
    }

    @Test
    fun case2_reconstructionUsesSharedPersistentBackingAfterLibraryRestart() {
        val game = game()
        seedRecord(game.id, "original", "candidate-original", documents.createFile("Original.gba", "original"))
        val beforeRestart = library().localReleasesForGame(game.id).single()

        val afterRestart = PersistentRomLibrary(context, AppPreferences(context), database)
            .localReleasesForGame(game.id)
            .single()

        assertEquals(beforeRestart, afterRestart)
        assertNotEquals(library(), PersistentRomLibrary(context, AppPreferences(context), database))
    }

    @Test
    fun case3_candidateProvenancePersistsAcrossLibraryRestart() {
        val game = game()
        val uri = documents.createFile("Translation.gba", "translation")
        seedRecord(
            gameId = game.id,
            releaseId = "translation",
            candidateId = "candidate-translation",
            uri = uri,
            provenance = provenance(game.id, "translation", "candidate-translation"),
        )

        val restored = PersistentRomLibrary(context, AppPreferences(context), database)
            .localReleasesForGame(game.id)
            .single()

        assertEquals("candidate-translation", restored.candidateId)
        assertEquals("candidate-translation", restored.provenance?.candidateId)
        assertEquals("translation", restored.provenance?.releaseId)
        assertEquals("minerva", restored.provenance?.provider)
        assertEquals("Game/Translation.gba", restored.provenance?.exactFullPath)
    }

    @Test
    fun case4_activeSwitchingDoesNotDeleteEitherRelease() = runBlocking {
        val game = game()
        database.gameDao().insertAll(listOf(game))
        val originalUri = documents.createFile("Original.gba", "original")
        val translationUri = documents.createFile("Translation.gba", "translation")
        seedRecord(game.id, "original", "candidate-original", originalUri)
        seedRecord(game.id, "translation", "candidate-translation", translationUri)
        val persistent = library()
        val releases = persistent.localReleasesForGame(game.id).associateBy { it.releaseId }

        val originalActive = persistent.activateLocalRelease(game, requireNotNull(releases["original"]))
        persistent.activateLocalRelease(originalActive, requireNotNull(releases["translation"]))

        assertEquals(translationUri.toString(), database.gameDao().byId(game.id)?.contentUri)
        assertEquals(2, persistent.localReleasesForGame(game.id).size)
        assertTrue(documents.exists(originalUri))
        assertTrue(documents.exists(translationUri))
    }

    @Test
    fun case5_sameReleaseNewCandidateReplacesOnlyThatRelease() = runBlocking {
        assertSyntheticSafContract()
        val game = game()
        database.gameDao().insertAll(listOf(game))
        preferences.setSystemRomTreeUri(game.systemId, documents.treeUri())
        val persistent = library()
        val firstOriginal = persistent.persist(
            game,
            preparedFile("candidate-a", "Original.gba"),
            sourceIndexProvenance = provenance(game.id, "original", "candidate-a"),
        )
        val translation = persistent.persist(
            requireNotNull(database.gameDao().byId(game.id)),
            preparedFile("translation", "Translation.gba"),
            sourceIndexProvenance = provenance(game.id, "translation", "candidate-translation"),
        )

        val replacement = persistent.persist(
            requireNotNull(database.gameDao().byId(game.id)),
            preparedFile("candidate-b", "Original.gba"),
            sourceIndexProvenance = provenance(game.id, "original", "candidate-b"),
        )
        val releases = persistent.localReleasesForGame(game.id).associateBy { it.releaseId }

        assertEquals(setOf("original", "translation"), releases.keys)
        assertEquals("candidate-b", releases["original"]?.candidateId)
        assertEquals("candidate-translation", releases["translation"]?.candidateId)
        assertNotEquals(firstOriginal.uri, replacement.uri)
        assertFalse(documents.exists(firstOriginal.uri))
        assertTrue(documents.exists(replacement.uri))
        assertTrue(documents.exists(translation.uri))
    }

    @Test
    fun case5b_providerRenameWithNewDocumentIdStoresFinalIdentity() = runBlocking {
        val game = game()
        database.gameDao().insertAll(listOf(game))
        preferences.setSystemRomTreeUri(game.systemId, documents.treeUri())
        documents.renameChangesDocumentId = true

        val stored = library().persist(
            game,
            preparedFile("new-document-id", "Identity.gba"),
            sourceIndexProvenance = provenance(game.id, "original", "candidate-new-id"),
        )

        val record = library().localReleasesForGame(game.id).single()
        assertEquals(PersistentRomLibrary.LocalReleaseState.COMPLETE, record.state)
        assertEquals(stored.uri, record.uri)
        assertTrue(documents.exists(stored.uri))
        assertEquals(stored.uri.toString(), database.gameDao().byId(game.id)?.contentUri)
    }

    @Test
    fun case6_deletingInactiveReleasePreservesActiveRelease() = runBlocking {
        val originalUri = documents.createFile("Original.gba", "original")
        val translationUri = documents.createFile("Translation.gba", "translation")
        val game = game(contentUri = originalUri.toString(), origin = LibraryOrigin.DOWNLOADED)
        database.gameDao().insertAll(listOf(game))
        seedRecord(game.id, "original", "candidate-original", originalUri)
        seedRecord(game.id, "translation", "candidate-translation", translationUri)
        val persistent = library()

        assertTrue(persistent.deleteLocalRelease(game, "translation"))

        assertEquals(originalUri.toString(), database.gameDao().byId(game.id)?.contentUri)
        assertEquals(listOf("original"), persistent.localReleasesForGame(game.id).map { it.releaseId })
        assertTrue(documents.exists(originalUri))
        assertFalse(documents.exists(translationUri))
    }

    @Test
    fun case7_deletingActiveReleaseClearsPointerWithoutAutoSwitch() = runBlocking {
        val originalUri = documents.createFile("Original.gba", "original")
        val translationUri = documents.createFile("Translation.gba", "translation")
        val game = game(contentUri = originalUri.toString(), origin = LibraryOrigin.DOWNLOADED)
        database.gameDao().insertAll(listOf(game))
        seedRecord(game.id, "original", "candidate-original", originalUri)
        seedRecord(game.id, "translation", "candidate-translation", translationUri)
        val persistent = library()

        assertTrue(persistent.deleteLocalRelease(game, "original"))

        val storedGame = requireNotNull(database.gameDao().byId(game.id))
        assertNull(storedGame.contentUri)
        assertEquals(LibraryOrigin.NONE.name, storedGame.libraryOrigin)
        assertEquals(listOf("translation"), persistent.localReleasesForGame(game.id).map { it.releaseId })
        assertTrue(documents.exists(translationUri))
        assertNotEquals(translationUri.toString(), storedGame.contentUri)
    }

    @Test
    fun case8_missingInvalidAndLegacyOwnershipNeverDeletePhysicalBytes() = runBlocking {
        val game = game()
        database.gameDao().insertAll(listOf(game))
        val missingUri = documents.createFile("MissingOwnership.gba", "missing")
        val invalidUri = documents.createFile("InvalidOwnership.gba", "invalid")
        val legacyUri = documents.createFile("Legacy.gba", "legacy")
        seedRecord(game.id, "missing", "candidate-missing", missingUri, ownership = null)
        seedRecord(game.id, "invalid", "candidate-invalid", invalidUri, ownership = "USER_OWNED")
        seedRecord(game.id, null, null, legacyUri, ownership = null)
        val persistent = library()

        assertTrue(persistent.deleteLocalRelease(game, "missing"))
        assertTrue(persistent.deleteLocalRelease(game, "invalid"))
        assertTrue(persistent.deleteLocalRelease(game, null))

        assertTrue(documents.exists(missingUri))
        assertTrue(documents.exists(invalidUri))
        assertTrue(documents.exists(legacyUri))
        assertFalse(isPhysicalDeletionAuthorized(PersistentRomLibrary.LocalReleaseOwnership.LEGACY_UNKNOWN))
    }

    @Test
    fun case9_pendingRecoveryPromotesValidAndCleansInvalidAndOrphanMarkers() = runBlocking {
        val game = game()
        database.gameDao().insertAll(listOf(game))
        preferences.setSystemRomTreeUri(game.systemId, documents.treeUri())
        val validBytes = "valid-pending".toByteArray()
        val validUri = documents.createFile("ValidPending.gba", validBytes)
        val invalidUri = documents.createFile("InvalidPending.gba", "invalid")
        seedRecord(
            game.id,
            "valid-pending",
            "candidate-valid",
            validUri,
            state = PersistentRomLibrary.LocalReleaseState.PENDING,
            sha256 = sha256(validBytes),
        )
        seedRecord(
            game.id,
            "invalid-pending",
            "candidate-invalid",
            invalidUri,
            state = PersistentRomLibrary.LocalReleaseState.PENDING,
            sha256 = sha256("different".toByteArray()),
        )
        val markerName = ".oz-pending-orphan.part"
        val markerUri = documents.createFile(markerName, "partial")
        records.edit().putString(
            "pending.orphan",
            JSONObject().put("gameId", game.id).put("markerName", markerName).toString(),
        ).commit()

        val persistent = library()
        persistent.reconcileLocalReleases(game)

        val releases = persistent.localReleasesForGame(game.id)
        assertEquals(listOf("valid-pending"), releases.map { it.releaseId })
        assertEquals(PersistentRomLibrary.LocalReleaseState.COMPLETE, releases.single().state)
        assertTrue(documents.exists(validUri))
        assertFalse(documents.exists(invalidUri))
        assertFalse(documents.exists(markerUri))
        assertFalse(records.contains("pending.orphan"))
    }

    @Test
    fun case10_pendingIntentIsDurableBeforeArtifactAndRoomNeverReceivesPendingUri() = runBlocking {
        val game = game()
        database.gameDao().insertAll(listOf(game))
        preferences.setSystemRomTreeUri(game.systemId, documents.treeUri())
        val recordKey = localReleaseRecordKey(game.id, "pending")
        val pendingKey = "pending.$recordKey"
        val queriesBeforePersistence = roomQueries.size
        var pendingIntentSeenBeforeCreate: String? = null
        var recreatedLibrary: PersistentRomLibrary? = null
        var createRequest: SyntheticDocumentsProvider.CreateRequest? = null
        documents.beforeCreate = { request ->
            val recreatedContext = RuntimeEnvironment.getApplication() as Application
            val recreatedRecords = recreatedContext.getSharedPreferences(RECORD_PREFS, Application.MODE_PRIVATE)
            pendingIntentSeenBeforeCreate = recreatedRecords.getString(pendingKey, null)
            createRequest = request
            // Host restart simulation only: this recreates durable-state consumers, not an OS process.
            recreatedLibrary = PersistentRomLibrary(
                recreatedContext,
                AppPreferences(recreatedContext),
                database,
            )
        }
        documents.failReadsAfterRename = true
        val persistent = library()

        val failure = runCatching {
            persistent.persist(
                game,
                preparedFile("candidate-pending", "Pending.gba"),
                sourceIndexProvenance = provenance(game.id, "pending", "candidate-pending"),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertNotNull(pendingIntentSeenBeforeCreate)
        assertNotNull(recreatedLibrary)
        assertEquals(
            JSONObject(requireNotNull(pendingIntentSeenBeforeCreate)).getString("markerName"),
            requireNotNull(createRequest).displayName,
        )
        assertNull(database.gameDao().byId(game.id)?.contentUri)
        val pending = JSONObject(requireNotNull(records.getString(recordKey, null)))
        assertEquals(PersistentRomLibrary.LocalReleaseState.PENDING.name, pending.getString("state"))
        val pendingDocumentUris = documents.pendingDocumentUris()
        assertTrue(pendingDocumentUris.isNotEmpty())
        val persistenceQueries = synchronized(roomQueries) {
            roomQueries.drop(queriesBeforePersistence)
        }
        assertTrue(persistenceQueries.any { query -> query.sql.contains("SELECT", ignoreCase = true) })
        assertFalse(
            persistenceQueries.any { query ->
                query.sql.contains("UPDATE", ignoreCase = true) &&
                    query.sql.contains("contentUri", ignoreCase = true)
            },
        )
        assertFalse(
            persistenceQueries.any { query ->
                query.bindArgs.any { argument -> argument?.toString() in pendingDocumentUris }
            },
        )

        documents.failReadsAfterRename = false
        requireNotNull(recreatedLibrary).reconcileLocalReleases(game)

        assertFalse(records.contains(recordKey))
        assertNull(database.gameDao().byId(game.id)?.contentUri)
    }

    private fun assertSyntheticSafContract() {
        val root = requireNotNull(DocumentFile.fromTreeUri(context, documents.treeUri()))
        val registeredProvider = ShadowContentResolver.getProvider(root.uri)
        val directCount = runCatching {
            context.contentResolver.query(
                root.uri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use(Cursor::getCount)
        }
        assertTrue(registeredProvider === documents)
        assertEquals(1, directCount.getOrThrow())
        assertTrue(
            "root=${root.uri}, permission=${context.checkCallingOrSelfUriPermission(root.uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}, operations=${documents.operations()}",
            root.exists(),
        )
        assertTrue(root.isDirectory)
        assertTrue(root.canWrite())

        val created = requireNotNull(root.createFile("application/octet-stream", "contract-probe.bin"))
        assertTrue(created.exists())
        assertTrue(created.isFile)
        assertTrue(created.canWrite())
        context.contentResolver.openOutputStream(created.uri, "w")!!.use { output ->
            output.write("contract-probe".toByteArray())
        }
        assertEquals("contract-probe".toByteArray().size.toLong(), created.length())
        assertTrue(root.listFiles().any { child -> child.uri == created.uri })

        assertTrue(created.delete())
        assertFalse(created.exists())
        assertFalse(root.listFiles().any { child -> child.uri == created.uri })
    }

    private fun library() = PersistentRomLibrary(context, preferences, database)

    private fun game(
        contentUri: String? = null,
        origin: LibraryOrigin = LibraryOrigin.NONE,
    ) = GameEntity(
        id = "gba-game",
        title = "Game",
        normalizedTitle = "game",
        platform = "gba",
        systemId = "gba",
        year = null,
        region = "World",
        thumbnailRepo = null,
        source = "oz-source-index",
        launchValidated = false,
        coreId = null,
        virtualExtension = "gba",
        contentUri = contentUri,
        libraryOrigin = origin.name,
    )

    private fun provenance(gameId: String, releaseId: String, candidateId: String) = SourceIndexProvenance(
        gameId = gameId,
        releaseId = releaseId,
        candidateId = candidateId,
        provider = "minerva",
        infoHash = "0123456789abcdef",
        fileIndex = 7,
        exactFullPath = "Game/${if (releaseId == "translation") "Translation" else "Original"}.gba",
        expectedSize = 1234L,
    )

    private fun seedRecord(
        gameId: String,
        releaseId: String?,
        candidateId: String?,
        uri: Uri,
        state: PersistentRomLibrary.LocalReleaseState = PersistentRomLibrary.LocalReleaseState.COMPLETE,
        ownership: String? = PersistentRomLibrary.LocalReleaseOwnership.OZ_OWNED.name,
        provenance: SourceIndexProvenance? = null,
        sha256: String = "",
    ) {
        val json = JSONObject().apply {
            put("schema", if (releaseId == null) 2 else 3)
            put("gameId", gameId)
            put("releaseId", releaseId ?: JSONObject.NULL)
            put("candidateId", candidateId ?: JSONObject.NULL)
            put("systemId", "gba")
            put("region", "World")
            put("displayName", documents.displayName(uri))
            put("uri", uri.toString())
            put("treeUri", documents.treeUri().toString())
            put("bytes", documents.length(uri))
            put("sha256", sha256)
            put("savedAt", 1L)
            put("state", state.name)
            if (ownership != null) put("ownership", ownership)
            put("provenance", provenance?.let { value ->
                JSONObject().apply {
                    put("gameId", value.gameId)
                    put("releaseId", value.releaseId)
                    put("candidateId", value.candidateId)
                    put("provider", value.provider)
                    put("infoHash", value.infoHash)
                    put("fileIndex", value.fileIndex)
                    put("exactFullPath", value.exactFullPath)
                    put("expectedSize", value.expectedSize)
                }
            } ?: JSONObject.NULL)
            put("members", org.json.JSONArray())
        }
        val key = releaseId?.let { localReleaseRecordKey(gameId, it) } ?: "rom.$gameId"
        records.edit().putString(key, json.toString()).commit()
    }

    private fun preparedFile(contents: String, name: String): File {
        val directory = File(context.cacheDir, "behavior-input-${System.nanoTime()}").apply { mkdirs() }
        return File(directory, name).apply { writeText(contents) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val RECORD_PREFS = "oz_persistent_rom_library"
        private const val APP_PREFS = "oz_retrorom"
    }

    private data class RoomQuery(
        val sql: String,
        val bindArgs: List<Any?>,
    )
}

class SyntheticDocumentsProvider : DocumentsProvider() {
    data class CreateRequest(
        val parentDocumentId: String,
        val mimeType: String,
        val displayName: String,
    )

    data class Node(
        val id: String,
        val parentId: String?,
        var file: File,
        val mimeType: String,
    )

    private val nodes = linkedMapOf<String, Node>()
    private val renamedIds = mutableSetOf<String>()
    private val pendingUris = linkedSetOf<String>()
    private val operationLog = Collections.synchronizedList(mutableListOf<String>())
    private var nextId = 1
    var failReadsAfterRename: Boolean = false
    var renameChangesDocumentId: Boolean = false
    var beforeCreate: ((CreateRequest) -> Unit)? = null

    override fun onCreate(): Boolean {
        val root = File(requireNotNull(context).cacheDir, "synthetic-documents-${System.nanoTime()}")
            .apply { mkdirs() }
        nodes[ROOT_ID] = Node(ROOT_ID, null, root, DocumentsContract.Document.MIME_TYPE_DIR)
        return true
    }

    fun treeUri(): Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)

    @Synchronized
    fun createFile(name: String, contents: String): Uri = createFile(name, contents.toByteArray())

    @Synchronized
    fun createFile(name: String, contents: ByteArray): Uri {
        val id = createDocument(ROOT_ID, "application/octet-stream", name)
        nodes.getValue(id).file.writeBytes(contents)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri(), id)
    }

    @Synchronized
    fun exists(uri: Uri): Boolean = node(uri)?.file?.exists() == true

    @Synchronized
    fun length(uri: Uri): Long = node(uri)?.file?.length() ?: -1L

    @Synchronized
    fun displayName(uri: Uri): String = requireNotNull(node(uri)).file.name

    @Synchronized
    fun pendingDocumentUris(): Set<String> = pendingUris.toSet()

    fun operations(): List<String> = operationLog.toList()

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: ROOT_COLUMNS
        return MatrixCursor(columns).apply {
            val row = newRow()
            columns.forEach { column ->
                row.add(
                    when (column) {
                        DocumentsContract.Root.COLUMN_ROOT_ID -> ROOT_ID
                        DocumentsContract.Root.COLUMN_DOCUMENT_ID -> ROOT_ID
                        DocumentsContract.Root.COLUMN_FLAGS ->
                            DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                        DocumentsContract.Root.COLUMN_TITLE -> "Synthetic ROMs"
                        else -> null
                    },
                )
            }
        }
    }

    @Synchronized
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        operationLog += "query:$documentId"
        return cursor(projection, listOfNotNull(nodes[documentId]?.takeIf { it.file.exists() }))
    }

    @Synchronized
    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        operationLog += "children:$parentDocumentId"
        return cursor(projection, nodes.values.filter { it.parentId == parentDocumentId && it.file.exists() })
    }

    @Synchronized
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        if (failReadsAfterRename && documentId in renamedIds && mode.contains('r')) {
            throw FileNotFoundException("Synthetic post-rename read failure")
        }
        val node = nodes[documentId]?.takeIf { it.file.exists() }
            ?: throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(node.file, ParcelFileDescriptor.parseMode(mode))
    }

    @Synchronized
    override fun getDocumentType(documentId: String): String =
        nodes[documentId]?.takeIf { it.file.exists() }?.mimeType ?: throw FileNotFoundException(documentId)

    @Synchronized
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        nodes[documentId]?.parentId == parentDocumentId

    @Synchronized
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        beforeCreate?.invoke(CreateRequest(parentDocumentId, mimeType, displayName))
        val parent = nodes[parentDocumentId]?.takeIf { it.file.isDirectory }
            ?: throw FileNotFoundException(parentDocumentId)
        val id = "doc-${nextId++}"
        val file = File(parent.file, displayName)
        val created = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) file.mkdirs() else file.createNewFile()
        if (!created) throw FileNotFoundException("Could not create synthetic document $displayName")
        nodes[id] = Node(id, parentDocumentId, file, mimeType)
        if (displayName.startsWith(".oz-pending-")) {
            pendingUris += DocumentsContract.buildDocumentUriUsingTree(treeUri(), id).toString()
        }
        return id
    }

    @Synchronized
    override fun renameDocument(documentId: String, displayName: String): String {
        val node = nodes[documentId] ?: throw FileNotFoundException(documentId)
        val renamed = File(requireNotNull(node.file.parentFile), displayName)
        check(node.file.renameTo(renamed)) { "Could not rename synthetic document" }
        node.file = renamed
        renamedIds += documentId
        if (!renameChangesDocumentId) return documentId
        val replacementId = "doc-${nextId++}"
        nodes.remove(documentId)
        nodes[replacementId] = Node(replacementId, node.parentId, renamed, node.mimeType)
        return replacementId
    }

    @Synchronized
    override fun deleteDocument(documentId: String) {
        val node = nodes[documentId] ?: return
        if (node.file.isDirectory) node.file.deleteRecursively() else node.file.delete()
        val removedIds = buildSet {
            add(documentId)
            var added: Boolean
            do {
                val known = toSet()
                val descendants = nodes.values
                    .filter { candidate -> candidate.parentId in known }
                    .map { candidate -> candidate.id }
                added = addAll(descendants)
            } while (added)
        }
        removedIds.forEach { id ->
            nodes.remove(id)
            renamedIds.remove(id)
        }
    }

    @Synchronized
    private fun node(uri: Uri): Node? = runCatching {
        nodes[DocumentsContract.getDocumentId(uri)]
    }.getOrNull()

    private fun cursor(projection: Array<out String>?, values: List<Node>): Cursor {
        val columns = projection ?: DOCUMENT_COLUMNS
        return MatrixCursor(columns).apply {
            values.forEach { node ->
                val row = newRow()
                columns.forEach { column ->
                    row.add(
                        when (column) {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> node.id
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> node.file.name
                            DocumentsContract.Document.COLUMN_MIME_TYPE -> node.mimeType
                            DocumentsContract.Document.COLUMN_SIZE -> node.file.length()
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED -> node.file.lastModified()
                            DocumentsContract.Document.COLUMN_FLAGS -> documentFlags(node)
                            else -> null
                        },
                    )
                }
            }
        }
    }

    private fun documentFlags(node: Node): Int {
        var flags = DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
            DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        if (node.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        }
        return flags
    }

    companion object {
        const val AUTHORITY = "cl.gakuto.kizuna.synthetic.documents"
        private const val ROOT_ID = "root"
        private val ROOT_COLUMNS = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_TITLE,
        )
        private val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    }
}
