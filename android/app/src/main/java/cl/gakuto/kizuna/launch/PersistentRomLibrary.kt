package cl.gakuto.kizuna.launch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AuthorizedSource
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.catalog.M3uPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Persistent user-owned ROM library.
 *
 * Downloads are written only inside a SAF tree explicitly chosen by the user. Oz never writes
 * into RetroArch's private app directory. The selected tree may be the same shared ROM folder
 * that RetroArch browses. The resulting content URI is stored in Room so the next launch uses the
 * local copy before ROMgi.
 */
class PersistentRomLibrary(
    context: Context,
    private val preferences: AppPreferences,
    private val database: OzDatabase = OzDatabase.get(context),
) {
    enum class LocalReleaseState { PENDING, COMPLETE }
    enum class LocalReleaseOwnership { OZ_OWNED, LEGACY_UNKNOWN }

    data class LocalReleaseProvenance(
        val gameId: String,
        val releaseId: String,
        val candidateId: String,
        val provider: String,
        val infoHash: String,
        val fileIndex: Int,
        val exactFullPath: String,
        val expectedSize: Long,
    )

    data class StoredRomMember(
        val discIndex: Int,
        val discLabel: String,
        val displayName: String,
        val uri: Uri,
        val bytes: Long,
        val sha256: String,
    )

    data class StoredRom(
        val gameId: String,
        val systemId: String,
        val region: String,
        val displayName: String,
        val uri: Uri,
        val treeUri: Uri,
        val bytes: Long,
        val sha256: String,
        val savedAt: Long,
        val members: List<StoredRomMember> = emptyList(),
        val releaseId: String? = null,
        val candidateId: String? = null,
        val provenance: LocalReleaseProvenance? = null,
        val state: LocalReleaseState = LocalReleaseState.COMPLETE,
        val ownership: LocalReleaseOwnership = LocalReleaseOwnership.LEGACY_UNKNOWN,
    ) {
        val isMultiDisc: Boolean get() = members.size > 1 && displayName.endsWith(".m3u", ignoreCase = true)

        fun asSource(): AuthorizedSource = AuthorizedSource(
            value = uri.toString(),
            expectedFileName = displayName,
            expectedSize = bytes,
            sourceLabel = "Biblioteca ROMs · descargado",
        )
    }

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val records = appContext.getSharedPreferences(RECORD_PREFS, Context.MODE_PRIVATE)

    fun hasDestination(systemId: String): Boolean = preferences.romTreeUriFor(systemId) != null

    suspend fun resolve(game: GameEntity): StoredRom? = withContext(Dispatchers.IO) {
        reconcileLocalReleases(game)
        val recordsForGame = localReleasesForGame(game.id)
            .filter { it.state == LocalReleaseState.COMPLETE }
        val uriText = game.contentUri?.takeIf(String::isNotBlank)
            ?: recordsForGame.singleOrNull()?.uri?.toString()
        if (uriText.isNullOrBlank()) return@withContext null
        val uri = runCatching { Uri.parse(uriText) }.getOrNull()
            ?: return@withContext clearMissing(game.id, null)
        val persisted = recordsForGame.firstOrNull { it.uri == uri }
        if (!canRead(uri)) return@withContext clearMissing(game.id, persisted)
        if (persisted != null) {
            if (persisted.members.all { canRead(it.uri) }) return@withContext persisted
            return@withContext clearMissing(game.id, persisted)
        }

        val name = queryDisplayName(uri) ?: "${safeName(game.title)}.${game.virtualExtension.ifBlank { "rom" }}"
        val bytes = queryLength(uri).coerceAtLeast(0L)
        val tree = preferences.romTreeUriFor(game.systemId) ?: uri
        StoredRom(
            gameId = game.id,
            systemId = game.systemId,
            region = game.region,
            displayName = name,
            uri = uri,
            treeUri = tree,
            bytes = bytes,
            sha256 = "",
            savedAt = 0L,
            members = emptyList(),
            ownership = LocalReleaseOwnership.LEGACY_UNKNOWN,
        )
    }

    fun localReleasesForGame(gameId: String): List<StoredRom> = records.all
        .asSequence()
        .filter { (key, _) -> key == legacyRecordKey(gameId) || key.startsWith(V3_KEY_PREFIX) }
        .mapNotNull { (key, raw) -> decodeRecord(raw as? String, key.removePrefix("rom.")) }
        .filter { it.gameId == gameId }
        .filter { it.state == LocalReleaseState.COMPLETE }
        .sortedWith(compareBy<StoredRom> { it.releaseId == null }.thenBy { it.releaseId.orEmpty() })
        .toList()

    suspend fun reconcileLocalReleases(game: GameEntity) = withContext(Dispatchers.IO) {
        val recordsForGame = records.all.asSequence()
            .mapNotNull { (key, raw) ->
                decodeRecord(raw as? String, key.removePrefix("rom."))?.let { key to it }
            }
            .filter { (_, value) -> value.gameId == game.id }
            .toList()
        recordsForGame.forEach { (key, value) ->
            val valid = canRead(value.uri) && queryLength(value.uri).let { it <= 0L || it == value.bytes }
            if (!valid) {
                records.edit().remove(key).commit()
                if (isPhysicalDeletionAuthorized(value.ownership)) deleteUri(value.uri)
                if (game.contentUri == value.uri.toString()) {
                    database.gameDao().updateLocalState(game.id, null, cl.gakuto.kizuna.data.LibraryOrigin.NONE.name)
                }
            } else if (value.state == LocalReleaseState.PENDING) {
                val hashMatches = value.sha256.isBlank() || sha256(value.uri).equals(value.sha256, true)
                if (hashMatches) {
                    saveRecord(value.copy(state = LocalReleaseState.COMPLETE), key)
                    clearPendingIntent(pendingIntentKey(key))
                }
                else {
                    records.edit().remove(key).commit()
                    if (isPhysicalDeletionAuthorized(value.ownership)) deleteUri(value.uri)
                }
            }
        }
        reconcilePendingMarkers(game)
        if (game.contentUri?.isNotBlank() == true) {
            val active = runCatching { Uri.parse(game.contentUri) }.getOrNull()
            if (active == null || !canRead(active)) {
                database.gameDao().updateLocalState(game.id, null, cl.gakuto.kizuna.data.LibraryOrigin.NONE.name)
            }
        }
    }

    suspend fun activeLocalRelease(game: GameEntity): StoredRom? = withContext(Dispatchers.IO) {
        val uri = game.contentUri?.takeIf(String::isNotBlank) ?: return@withContext null
        localReleasesForGame(game.id).firstOrNull { it.uri.toString() == uri && canRead(it.uri) }
    }

    suspend fun activateLocalRelease(game: GameEntity, release: StoredRom): GameEntity = withContext(Dispatchers.IO) {
        require(release.gameId == game.id && release.state == LocalReleaseState.COMPLETE) {
            "El release local no pertenece al juego o no estÃ¡ completo."
        }
        require(canRead(release.uri)) { "El release local ya no estÃ¡ disponible." }
        database.gameDao().updateLocalState(
            game.id,
            release.uri.toString(),
            cl.gakuto.kizuna.data.LibraryOrigin.DOWNLOADED.name,
        )
        game.copy(contentUri = release.uri.toString(), libraryOrigin = cl.gakuto.kizuna.data.LibraryOrigin.DOWNLOADED.name)
    }

    suspend fun persist(
        game: GameEntity,
        prepared: File,
        resolvedRegion: String? = null,
        sourceIndexProvenance: SourceIndexProvenance? = null,
    ): StoredRom = withContext(Dispatchers.IO) {
        require(prepared.isFile && prepared.length() > 0L) { "La ROM preparada no existe o está vacía." }
        val systemSpecificTree = preferences.systemRomTreeUri(game.systemId)
        val treeUri = systemSpecificTree ?: preferences.globalRomTreeUri
            ?: error("Selecciona primero la carpeta ROMs que usará Oz RetroRom.")
        val selectedRoot = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: error("Android ya no puede abrir la carpeta ROMs seleccionada.")
        require(selectedRoot.exists() && selectedRoot.isDirectory && selectedRoot.canWrite()) {
            "La carpeta ROMs seleccionada no permite escritura. Vuelve a elegirla en Configuración."
        }

        val destination = if (systemSpecificTree != null) {
            selectedRoot
        } else {
            val folderName = safeFolderName(game.platform.ifBlank { game.systemId })
            selectedRoot.findFile(folderName)?.takeIf { it.isDirectory }
                ?: selectedRoot.createDirectory(folderName)
                ?: error("No pude crear la carpeta $folderName dentro de la biblioteca ROMs.")
        }

        val fileName = safeFileName(prepared.name.ifBlank { "${game.title}.${game.virtualExtension}" })
        val expectedSha = sha256(prepared)
        val recordKey = sourceIndexProvenance?.let { v3RecordKey(it.gameId, it.releaseId) } ?: legacyRecordKey(game.id)
        val previous = decodeRecord(records.getString(recordKey, null), game.id)
            ?.takeIf { it.state == LocalReleaseState.COMPLETE }
        val existing = destination.findFile(fileName)?.takeIf { it.isFile }
        if (
            existing != null && previous != null && previous.sha256.equals(expectedSha, true) &&
            previous.bytes == prepared.length() && canRead(existing.uri)
        ) {
            database.gameDao().updateLocalState(game.id, existing.uri.toString(), cl.gakuto.kizuna.data.LibraryOrigin.DOWNLOADED.name)
            return@withContext previous.copy(uri = existing.uri, treeUri = treeUri)
        }

        // Never delete an existing user file just because ROMgi resolved the same display name.
        // If the name is occupied and it is not the verified Oz copy, create a deterministic
        // alternative name. This is safer for shared RetroArch libraries managed by the user.
        val targetName = if (existing == null) fileName else uniqueFileName(destination, fileName)
        val pendingKey = pendingIntentKey(recordKey)
        savePendingIntent(pendingKey, game.id, markerName = pendingMarkerName(recordKey))
        val target = destination.createFile(mimeType(targetName), pendingMarkerName(recordKey))
            ?: run {
                clearPendingIntent(pendingKey)
                error("Android no permitió crear $targetName en la biblioteca ROMs.")
            }
        try {
            writePreparedDurably(target.uri, prepared)

            val written = queryLength(target.uri)
            require(written <= 0L || written == prepared.length()) {
                "La copia persistente quedó incompleta ($written de ${prepared.length()} bytes)."
            }

            val record = StoredRom(
                gameId = game.id,
                systemId = game.systemId,
                region = resolvedRegion?.takeIf(String::isNotBlank) ?: game.region,
                displayName = target.name ?: targetName,
                uri = target.uri,
                treeUri = treeUri,
                bytes = prepared.length(),
                sha256 = expectedSha,
                savedAt = System.currentTimeMillis(),
                releaseId = sourceIndexProvenance?.releaseId,
                candidateId = sourceIndexProvenance?.candidateId,
                provenance = sourceIndexProvenance?.let(::toLocalProvenance),
                state = LocalReleaseState.PENDING,
                ownership = LocalReleaseOwnership.OZ_OWNED,
            )
            saveRecord(record, recordKey)
            require(target.renameTo(targetName)) {
                "Android no pudo completar el rename de la ROM persistente."
            }
            val finalUri = target.uri
            val finalDisplayName = target.name ?: targetName
            val persistedLength = queryLength(finalUri)
            require(canRead(finalUri) && (persistedLength <= 0L || persistedLength == record.bytes)) {
                "La ROM persistida no coincide con el tamaÃ±o esperado."
            }
            val complete = record.copy(
                state = LocalReleaseState.COMPLETE,
                displayName = finalDisplayName,
                uri = finalUri,
            )
            saveRecord(complete, recordKey)
            clearPendingIntent(pendingKey)
            database.gameDao().updateLocalState(game.id, finalUri.toString(), cl.gakuto.kizuna.data.LibraryOrigin.DOWNLOADED.name)
            if (previous != null && previous.uri != finalUri && isPhysicalDeletionAuthorized(previous.ownership)) {
                deleteUri(previous.uri)
            }
            complete
        } catch (error: Throwable) {
            runCatching { target.delete() }
            clearPendingIntent(pendingKey)
            throw error
        }
    }

    /** Persists every disc beside a relative M3U playlist and records all owned URIs. */
    suspend fun persistSet(
        game: GameEntity,
        set: RomDownloadSet,
        preparedDiscs: List<Pair<RomDownloadSetItem, File>>,
        resolvedRegion: String? = null,
    ): StoredRom = withContext(Dispatchers.IO) {
        require(preparedDiscs.size == set.items.size) { "El conjunto preparado no contiene todos los discos." }
        require(preparedDiscs.map { it.first.discIndex } == (1..set.items.size).toList()) {
            "Los discos preparados no están completos u ordenados."
        }
        preparedDiscs.forEach { (_, file) ->
            require(file.isFile && file.length() > 0L) { "Uno de los discos preparados no existe o está vacío." }
        }

        val systemSpecificTree = preferences.systemRomTreeUri(game.systemId)
        val treeUri = systemSpecificTree ?: preferences.globalRomTreeUri
            ?: error("Selecciona primero la carpeta ROMs que usará Oz RetroRom.")
        val selectedRoot = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: error("Android ya no puede abrir la carpeta ROMs seleccionada.")
        require(selectedRoot.exists() && selectedRoot.isDirectory && selectedRoot.canWrite()) {
            "La carpeta ROMs seleccionada no permite escritura. Vuelve a elegirla en Configuración."
        }
        val destination = if (systemSpecificTree != null) {
            selectedRoot
        } else {
            val folderName = safeFolderName(game.platform.ifBlank { game.systemId })
            selectedRoot.findFile(folderName)?.takeIf { it.isDirectory }
                ?: selectedRoot.createDirectory(folderName)
                ?: error("No pude crear la carpeta $folderName dentro de la biblioteca ROMs.")
        }

        val created = mutableListOf<DocumentFile>()
        try {
            val storedMembers = preparedDiscs.map { (item, prepared) ->
                val desiredName = safeFileName(prepared.name)
                val targetName = if (destination.findFile(desiredName) == null) desiredName
                    else uniqueFileName(destination, desiredName)
                val target = destination.createFile(mimeType(targetName), targetName)
                    ?: error("Android no permitió crear $targetName en la biblioteca ROMs.")
                created += target
                writePreparedDurably(target.uri, prepared)
                val written = queryLength(target.uri)
                require(written <= 0L || written == prepared.length()) {
                    "La copia del ${item.discLabel.lowercase()} quedó incompleta ($written de ${prepared.length()} bytes)."
                }
                StoredRomMember(
                    discIndex = item.discIndex,
                    discLabel = item.discLabel,
                    displayName = target.name ?: targetName,
                    uri = target.uri,
                    bytes = prepared.length(),
                    sha256 = sha256(prepared),
                )
            }

            val playlistText = M3uPlaylist.content(storedMembers.map { it.displayName })
            val desiredPlaylistName = safeFileName(set.playlistName)
            val playlistName = if (destination.findFile(desiredPlaylistName) == null) desiredPlaylistName
                else uniqueFileName(destination, desiredPlaylistName)
            val playlist = destination.createFile("audio/x-mpegurl", playlistName)
                ?: error("Android no permitió crear la lista multidisco $playlistName.")
            created += playlist
            writeTextDurably(playlist.uri, playlistText)
            val playlistBytes = playlistText.toByteArray(Charsets.UTF_8).size.toLong()
            val record = StoredRom(
                gameId = game.id,
                systemId = game.systemId,
                region = resolvedRegion?.takeIf(String::isNotBlank) ?: set.region,
                displayName = playlist.name ?: playlistName,
                uri = playlist.uri,
                treeUri = treeUri,
                bytes = storedMembers.sumOf { it.bytes } + playlistBytes,
                sha256 = sha256(playlistText.toByteArray(Charsets.UTF_8)),
                savedAt = System.currentTimeMillis(),
                members = storedMembers,
            )
            saveRecord(record)
            database.gameDao().updateLocalState(
                game.id,
                record.uri.toString(),
                cl.gakuto.kizuna.data.LibraryOrigin.DOWNLOADED.name,
            )
            record
        } catch (error: Throwable) {
            created.asReversed().forEach { document -> runCatching { document.delete() } }
            throw error
        }
    }

    suspend fun deleteLocalRelease(game: GameEntity, releaseId: String?): Boolean = withContext(Dispatchers.IO) {
        val target = localReleasesForGame(game.id).firstOrNull { it.releaseId == releaseId }
            ?: return@withContext false
        val key = if (target.releaseId == null) legacyRecordKey(game.id) else v3RecordKey(game.id, target.releaseId)
        records.edit().remove(key).commit()
        if (isPhysicalDeletionAuthorized(target.ownership)) deleteUri(target.uri)
        if (game.contentUri == target.uri.toString()) {
            database.gameDao().updateLocalState(game.id, null, cl.gakuto.kizuna.data.LibraryOrigin.NONE.name)
        }
        true
    }

    suspend fun forget(gameId: String) = withContext(Dispatchers.IO) {
        localReleasesForGame(gameId).forEach { release ->
            val key = if (release.releaseId == null) legacyRecordKey(gameId) else v3RecordKey(gameId, release.releaseId)
            records.edit().remove(key).commit()
            if (isPhysicalDeletionAuthorized(release.ownership)) deleteUri(release.uri)
        }
        database.gameDao().updateLocalState(gameId, null, cl.gakuto.kizuna.data.LibraryOrigin.NONE.name)
    }

    /** Deletes only a ROM that Oz itself persisted. Imported user files are never deleted here. */
    suspend fun deleteDownloaded(game: GameEntity): Boolean = withContext(Dispatchers.IO) {
        require(game.libraryOrigin == cl.gakuto.kizuna.data.LibraryOrigin.DOWNLOADED.name) {
            "Solo las ROM descargadas por Oz pueden eliminarse desde esta acción."
        }
        val record = activeLocalRelease(game)
        val uri = record?.uri ?: game.contentUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
            ?: run {
                database.gameDao().updateLocalState(game.id, null, cl.gakuto.kizuna.data.LibraryOrigin.NONE.name)
                return@withContext true
            }
        val ownedUris = buildList {
            add(uri)
            record?.members?.forEach { add(it.uri) }
        }.distinct()
        // Attempt every Oz-owned URI even if one provider refuses deletion; Iterable.all()
        // would short-circuit and leave later discs untouched after the first failure.
        val deleted = ownedUris.map(::deleteUri).all { it }
        if (deleted || ownedUris.none(::canRead)) {
            record?.let {
                val key = if (it.releaseId == null) legacyRecordKey(game.id) else v3RecordKey(game.id, it.releaseId)
                records.edit().remove(key).commit()
            }
            database.gameDao().updateLocalState(game.id, null, cl.gakuto.kizuna.data.LibraryOrigin.NONE.name)
            true
        } else {
            false
        }
    }

    /**
     * Writes a prepared ROM and requests a durable flush before the download task is marked as
     * complete. External-storage DocumentsProviders can expose a path immediately while the
     * backing file is still propagating through the Android storage/FUSE layer. Disc images are
     * especially sensitive because RetroArch opens them for random access as soon as it starts.
     *
     * Not every provider supports a writable ParcelFileDescriptor. In that case Oz falls back to
     * ContentResolver.openOutputStream(), preserving compatibility with generic SAF providers.
     */
    private fun writePreparedDurably(uri: Uri, prepared: File) {
        val descriptorWrite = runCatching {
            resolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    prepared.inputStream().buffered(COPY_BUFFER).use { input ->
                        input.copyTo(output, COPY_BUFFER)
                    }
                    output.flush()
                    // Best effort: some document providers do not implement a meaningful fsync.
                    runCatching { output.fd.sync() }
                }
                true
            } ?: false
        }.getOrDefault(false)

        if (!descriptorWrite) {
            resolver.openOutputStream(uri, "w")?.use { output ->
                prepared.inputStream().buffered(COPY_BUFFER).use { input ->
                    input.copyTo(output, COPY_BUFFER)
                }
                output.flush()
            } ?: error("Android no permitió escribir la ROM en la carpeta seleccionada.")
        }
    }

    private fun writeTextDurably(uri: Uri, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val descriptorWrite = runCatching {
            resolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    output.write(bytes)
                    output.flush()
                    runCatching { output.fd.sync() }
                }
                true
            } ?: false
        }.getOrDefault(false)
        if (!descriptorWrite) {
            resolver.openOutputStream(uri, "w")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error("Android no permitió escribir la lista M3U.")
        }
    }

    private fun deleteUri(uri: Uri): Boolean {
        val document = DocumentFile.fromSingleUri(appContext, uri)
        return when {
            document == null || !document.exists() -> true
            document.delete() -> true
            else -> runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
        }
    }

    private suspend fun clearMissing(gameId: String, record: StoredRom?): StoredRom? {
        if (record != null) {
            val key = if (record.releaseId == null) legacyRecordKey(gameId) else v3RecordKey(gameId, record.releaseId)
            records.edit().remove(key).commit()
        }
        database.gameDao().updateLocalState(gameId, null, cl.gakuto.kizuna.data.LibraryOrigin.NONE.name)
        return null
    }

    private fun canRead(uri: Uri): Boolean = runCatching {
        resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor -> descriptor.fileDescriptor.valid() } == true
    }.getOrDefault(false)

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun queryLength(uri: Uri): Long = runCatching {
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun decodeRecord(raw: String?, fallbackGameId: String? = null): StoredRom? = runCatching {
        raw ?: return null
        val json = JSONObject(raw)
        val schema = json.optInt("schema", 2)
        val provenance = json.optJSONObject("provenance")?.let { value ->
            LocalReleaseProvenance(
                gameId = value.getString("gameId"),
                releaseId = value.getString("releaseId"),
                candidateId = value.getString("candidateId"),
                provider = value.getString("provider"),
                infoHash = value.getString("infoHash"),
                fileIndex = value.getInt("fileIndex"),
                exactFullPath = value.getString("exactFullPath"),
                expectedSize = value.getLong("expectedSize"),
            )
        }
        StoredRom(
            gameId = json.optString("gameId").takeIf { it.isNotBlank() && it != "null" } ?: fallbackGameId
                ?: return@runCatching null,
            systemId = json.optString("systemId"),
            region = json.optString("region"),
            displayName = json.optString("displayName"),
            uri = Uri.parse(json.getString("uri")),
            treeUri = Uri.parse(json.getString("treeUri")),
            bytes = json.optLong("bytes", 0L),
            sha256 = json.optString("sha256"),
            savedAt = json.optLong("savedAt", 0L),
            members = json.optJSONArray("members")?.let { members ->
                buildList {
                    for (index in 0 until members.length()) {
                        val item = members.getJSONObject(index)
                        add(
                            StoredRomMember(
                                discIndex = item.getInt("discIndex"),
                                discLabel = item.optString("discLabel", "Disco ${index + 1}"),
                                displayName = item.getString("displayName"),
                                uri = Uri.parse(item.getString("uri")),
                                bytes = item.optLong("bytes", 0L),
                                sha256 = item.optString("sha256"),
                            ),
                        )
                    }
                }
            } ?: emptyList(),
            releaseId = json.optString("releaseId").takeIf { it.isNotBlank() && it != "null" },
            candidateId = json.optString("candidateId").takeIf { it.isNotBlank() && it != "null" },
            provenance = provenance,
            state = runCatching { LocalReleaseState.valueOf(json.optString("state", "COMPLETE")) }
                .getOrDefault(LocalReleaseState.COMPLETE),
            ownership = runCatching {
                LocalReleaseOwnership.valueOf(json.optString("ownership", "LEGACY_UNKNOWN"))
            }.getOrDefault(LocalReleaseOwnership.LEGACY_UNKNOWN),
        )
    }.getOrNull()

    private fun saveRecord(record: StoredRom, key: String = if (record.releaseId == null) legacyRecordKey(record.gameId) else v3RecordKey(record.gameId, record.releaseId)) {
        val json = JSONObject().apply {
            put("schema", if (record.releaseId == null) 2 else 3)
            put("gameId", record.gameId)
            put("releaseId", record.releaseId ?: JSONObject.NULL)
            put("candidateId", record.candidateId ?: JSONObject.NULL)
            put("systemId", record.systemId)
            put("region", record.region)
            put("displayName", record.displayName)
            put("uri", record.uri.toString())
            put("treeUri", record.treeUri.toString())
            put("bytes", record.bytes)
            put("sha256", record.sha256)
            put("savedAt", record.savedAt)
            put("state", record.state.name)
            put("ownership", record.ownership.name)
            put("provenance", record.provenance?.let { value ->
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
            put("members", JSONArray().apply {
                record.members.forEach { member ->
                    put(JSONObject().apply {
                        put("discIndex", member.discIndex)
                        put("discLabel", member.discLabel)
                        put("displayName", member.displayName)
                        put("uri", member.uri.toString())
                        put("bytes", member.bytes)
                        put("sha256", member.sha256)
                    })
                }
            })
        }
        records.edit().putString(key, json.toString()).commit()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(COPY_BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun safeFolderName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim().take(80).ifBlank { "Sistema" }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim().take(180).ifBlank { "game.rom" }

    private fun safeName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        .trim().take(120).ifBlank { "game" }

    private fun uniqueFileName(directory: DocumentFile, original: String): String {
        val dot = original.lastIndexOf('.').takeIf { it > 0 } ?: -1
        val stem = if (dot >= 0) original.substring(0, dot) else original
        val extension = if (dot >= 0) original.substring(dot) else ""
        for (index in 2..99) {
            val candidate = safeFileName("$stem (Oz $index)$extension")
            if (directory.findFile(candidate) == null) return candidate
        }
        return safeFileName("$stem (Oz ${System.currentTimeMillis()})$extension")
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "zip" -> "application/zip"
        "cue", "m3u" -> "text/plain"
        "iso" -> "application/x-iso9660-image"
        else -> "application/octet-stream"
    }

    private fun legacyRecordKey(gameId: String) = "rom.${gameId}"

    private fun v3RecordKey(gameId: String, releaseId: String): String =
        localReleaseRecordKey(gameId, releaseId)

    private fun toLocalProvenance(value: SourceIndexProvenance): LocalReleaseProvenance = LocalReleaseProvenance(
        gameId = value.gameId,
        releaseId = value.releaseId,
        candidateId = value.candidateId,
        provider = value.provider,
        infoHash = value.infoHash,
        fileIndex = value.fileIndex,
        exactFullPath = value.exactFullPath,
        expectedSize = value.expectedSize,
    )

    private fun sha256(uri: Uri): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.buffered()?.use { input ->
            val buffer = ByteArray(COPY_BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return null
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }.getOrNull()

    private fun pendingIntentKey(recordKey: String): String = "pending.$recordKey"

    private fun pendingMarkerName(recordKey: String): String = ".oz-pending-${recordKey.removePrefix("rom.v3.")}.part"

    private fun savePendingIntent(key: String, gameId: String, markerName: String) {
        records.edit().putString(
            key,
            JSONObject().put("gameId", gameId).put("markerName", markerName).toString(),
        ).commit()
    }

    private fun clearPendingIntent(key: String) {
        records.edit().remove(key).commit()
    }

    private fun reconcilePendingMarkers(game: GameEntity) {
        val treeUri = preferences.systemRomTreeUri(game.systemId) ?: preferences.globalRomTreeUri ?: return
        val root = DocumentFile.fromTreeUri(appContext, treeUri) ?: return
        val destination = if (preferences.systemRomTreeUri(game.systemId) != null) root else
            root.findFile(safeFolderName(game.platform.ifBlank { game.systemId }))?.takeIf { it.isDirectory } ?: return
        records.all.asSequence()
            .filter { (key, _) -> key.startsWith("pending.") }
            .mapNotNull { (key, raw) -> runCatching { key to JSONObject(raw as String) }.getOrNull() }
            .filter { (_, json) -> json.optString("gameId") == game.id }
            .forEach { (key, json) ->
                destination.findFile(json.optString("markerName"))?.let { marker ->
                    if (marker.name?.startsWith(".oz-pending-") == true) runCatching { marker.delete() }
                }
                clearPendingIntent(key)
            }
    }

    companion object {
        private const val RECORD_PREFS = "oz_persistent_rom_library"
        private const val V3_KEY_PREFIX = "rom.v3."
        private const val COPY_BUFFER = 256 * 1024
    }
}

internal fun localReleaseRecordKey(gameId: String, releaseId: String): String {
    val canonical = buildString {
        append(gameId.length).append(':').append(gameId)
        append(releaseId.length).append(':').append(releaseId)
    }

    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return "rom.v3.$digest"
}

internal fun isPhysicalDeletionAuthorized(ownership: PersistentRomLibrary.LocalReleaseOwnership): Boolean =
    ownership == PersistentRomLibrary.LocalReleaseOwnership.OZ_OWNED
