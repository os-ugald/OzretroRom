package cl.gakuto.kizuna.launch

import android.content.Context
import cl.gakuto.kizuna.data.AuthorizedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class RomDownloadStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

data class RomDownloadDiscCheckpoint(
    val discIndex: Int,
    val sourceSignature: String,
    val preparedFileName: String,
    val preparedBytes: Long,
)

data class RomDownloadTaskSnapshot(
    val taskId: String,
    val gameId: String,
    val source: AuthorizedSource,
    val downloadSet: RomDownloadSet?,
    val biosSources: List<Pair<String, AuthorizedSource>>,
    val resolvedRegion: String?,
    val externalLauncher: Boolean,
    val stagingId: String,
    val status: RomDownloadStatus,
    val stage: String,
    val downloaded: Long,
    val total: Long,
    val bytesPerSecond: Long,
    val peers: Int,
    val fileName: String,
    val error: String?,
    val storedUri: String?,
    val storedDisplayName: String?,
    val startedAt: Long,
    val updatedAt: Long,
    val completedDiscs: List<RomDownloadDiscCheckpoint> = emptyList(),
    val sourcePlan: DownloadSourcePlan = DownloadSourcePlan.single(
        LogicalDownloadIdentity.forTask(
            gameId = gameId,
            resolvedRegion = resolvedRegion,
            expectedFileName = source.expectedFileName,
            expectedSize = source.expectedSize,
        ),
        source,
    ),
    val httpRecoveries: List<HttpCandidateRecovery> = emptyList(),
    val unsafeLegacyPartial: Boolean = false,
) {
    init {
        require(sourcePlan.selectedSource == source) {
            "La fuente activa de la tarea debe coincidir con el candidato seleccionado."
        }
    }

    val isActive: Boolean get() = status == RomDownloadStatus.QUEUED || status == RomDownloadStatus.RUNNING
}

/**
 * Persistent state store for single-ROM and complete multi-disc transfer workflows.
 *
 * Credentials are never serialized here: AuthorizedSource only contains the resolved transport
 * reference and metadata. Internet Archive authentication continues to live in its Keystore-backed
 * manager and is applied by AuthorizedSourceDownloader at request time.
 */
class RomDownloadTaskStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun createOrReuse(
        gameId: String,
        source: AuthorizedSource,
        biosSources: List<Pair<String, AuthorizedSource>>,
        resolvedRegion: String?,
        externalLauncher: Boolean = false,
        downloadSet: RomDownloadSet? = null,
        sourcePlan: DownloadSourcePlan? = null,
    ): RomDownloadTaskSnapshot {
        val requestedPlan = sourcePlan ?: DownloadSourcePlan.single(
            LogicalDownloadIdentity.forTask(gameId, resolvedRegion, source.expectedFileName, source.expectedSize),
            source,
        )
        require(requestedPlan.selectedSource == source) {
            "La fuente solicitada debe coincidir con el candidato seleccionado."
        }
        val previous = get(gameId)
        val canResume = previous != null &&
            previous.sourcePlan.selectedCandidate.identity == requestedPlan.selectedCandidate.identity &&
            previous.source == source &&
            previous.downloadSet?.signature == downloadSet?.signature &&
            previous.status != RomDownloadStatus.SUCCESS &&
            (previous.status != RomDownloadStatus.CANCELLED || hasAnyStagedFile(previous))
        // A newly selected source/edition must not erase the previous candidate's recoverable
        // bytes or validators. Keep that task authoritative until the user resumes it or uses
        // the existing explicit partial-deletion action.
        require(canResume || previous == null || !hasAnyStagedFile(previous)) {
            "Hay archivos recuperables de otra fuente o edición. Reanuda la descarga anterior o " +
                "usa 'Borrar parcial' en su pantalla de descarga antes de cambiar la selección."
        }
        val now = System.currentTimeMillis()
        val next = RomDownloadTaskSnapshot(
            taskId = if (canResume) previous!!.taskId else UUID.randomUUID().toString(),
            gameId = gameId,
            source = if (canResume) previous!!.source else source,
            downloadSet = if (canResume && previous!!.downloadSet != null) previous.downloadSet else downloadSet,
            completedDiscs = if (canResume) previous!!.completedDiscs else emptyList(),
            biosSources = biosSources,
            resolvedRegion = resolvedRegion,
            externalLauncher = if (canResume) previous!!.externalLauncher || externalLauncher else externalLauncher,
            stagingId = if (canResume) previous!!.stagingId else "download-${UUID.randomUUID()}",
            status = RomDownloadStatus.QUEUED,
            stage = if (canResume && partialBytes(previous!!) > 0L) "Reanudando descarga…" else "Preparando descarga…",
            downloaded = if (canResume) partialBytes(previous!!) else 0L,
            total = (downloadSet?.totalSize ?: source.expectedSize).takeIf { it > 0L } ?: previous?.total ?: -1L,
            bytesPerSecond = 0L,
            peers = 0,
            fileName = downloadSet?.playlistName ?: source.expectedFileName.orEmpty(),
            error = null,
            storedUri = null,
            storedDisplayName = null,
            startedAt = if (canResume) previous!!.startedAt else now,
            updatedAt = now,
            sourcePlan = if (canResume) {
                previous!!.sourcePlan
            } else {
                requestedPlan
            },
            httpRecoveries = if (canResume) previous!!.httpRecoveries else emptyList(),
            unsafeLegacyPartial = canResume && previous!!.unsafeLegacyPartial,
        )
        save(next)
        return next
    }

    @Synchronized
    fun updateProgress(gameId: String, progress: AuthorizedSourceDownloader.Progress): RomDownloadTaskSnapshot? {
        val current = get(gameId) ?: return null

        // Network callbacks may arrive after the user has paused/cancelled.
        // Never allow stale progress to resurrect a terminal task state.
        if (!current.isActive) return current

        val downloaded = if (progress.resetDownloadedBaseline) {
            progress.downloaded.coerceAtLeast(0L)
        } else {
            maxOf(
                current.downloaded.coerceAtLeast(0L),
                progress.downloaded.coerceAtLeast(0L),
            )
        }

        val next = current.copy(
            status = RomDownloadStatus.RUNNING,
            stage = progress.stage,
            downloaded = downloaded,
            total = progress.total,
            bytesPerSecond = progress.bytesPerSecond,
            peers = progress.peers,
            fileName = progress.fileName.ifBlank { current.fileName },
            error = null,
            updatedAt = System.currentTimeMillis(),
        )
        save(next)
        return next
    }

    @Synchronized
    fun markRunning(gameId: String, stage: String = "Iniciando descarga…"): RomDownloadTaskSnapshot? {
        val current = get(gameId) ?: return null
        val next = current.copy(
            status = RomDownloadStatus.RUNNING,
            stage = stage,
            error = null,
            updatedAt = System.currentTimeMillis(),
        )
        save(next)
        return next
    }

    @Synchronized
    fun markSuccess(gameId: String, uri: String, displayName: String, bytes: Long): RomDownloadTaskSnapshot? {
        val current = get(gameId) ?: return null
        val next = current.copy(
            status = RomDownloadStatus.SUCCESS,
            stage = "ROM guardada en la biblioteca",
            downloaded = if (bytes > 0L) bytes else current.downloaded,
            total = if (bytes > 0L) bytes else current.total,
            bytesPerSecond = 0L,
            peers = 0,
            fileName = displayName,
            error = null,
            storedUri = uri,
            storedDisplayName = displayName,
            updatedAt = System.currentTimeMillis(),
        )
        save(next)
        return next
    }

    @Synchronized
    fun markFailed(gameId: String, message: String): RomDownloadTaskSnapshot? {
        val current = get(gameId) ?: return null
        val next = current.copy(
            status = RomDownloadStatus.FAILED,
            stage = "Descarga interrumpida",
            bytesPerSecond = 0L,
            error = message.take(600),
            updatedAt = System.currentTimeMillis(),
        )
        save(next)
        return next
    }

    @Synchronized
    fun markCancelled(gameId: String): RomDownloadTaskSnapshot? {
        val current = get(gameId) ?: return null
        val next = current.copy(
            status = RomDownloadStatus.CANCELLED,
            stage = "Descarga cancelada",
            bytesPerSecond = 0L,
            error = null,
            updatedAt = System.currentTimeMillis(),
        )
        save(next)
        return next
    }

    fun get(gameId: String): RomDownloadTaskSnapshot? = prefs.getString(key(gameId), null)?.let(::decode)

    fun findByTaskId(taskId: String): RomDownloadTaskSnapshot? = prefs.all.values.asSequence()
        .filterIsInstance<String>()
        .mapNotNull(::decode)
        .firstOrNull { it.taskId == taskId }

    // Legacy/unclassified staged bytes still need an explicit user decision; keep the existing
    // resume/delete controls reachable even when HTTP recovery metadata cannot account for them.
    fun hasPartial(gameId: String): Boolean = get(gameId)?.let { hasAnyStagedFile(it) } == true

    fun completedDisc(gameId: String, item: RomDownloadSetItem): RomDownloadDiscCheckpoint? =
        get(gameId)?.completedDiscs?.firstOrNull { checkpoint ->
            checkpoint.discIndex == item.discIndex &&
                checkpoint.sourceSignature == sourceSignature(item.source)
        }

    @Synchronized
    fun markDiscComplete(gameId: String, item: RomDownloadSetItem, prepared: File): RomDownloadTaskSnapshot? {
        val current = get(gameId) ?: return null
        require(prepared.isFile && prepared.length() > 0L) { "El disco preparado no existe o esta vacio." }
        val checkpoint = RomDownloadDiscCheckpoint(
            discIndex = item.discIndex,
            sourceSignature = sourceSignature(item.source),
            preparedFileName = prepared.name,
            preparedBytes = prepared.length(),
        )
        val next = current.copy(
            completedDiscs = (current.completedDiscs.filterNot { it.discIndex == item.discIndex } + checkpoint)
                .sortedBy { it.discIndex },
            updatedAt = System.currentTimeMillis(),
        )
        save(next)
        return next
    }

    @Synchronized
    fun clearDiscCheckpoint(gameId: String, discIndex: Int): RomDownloadTaskSnapshot? {
        val current = get(gameId) ?: return null
        val next = current.copy(
            completedDiscs = current.completedDiscs.filterNot { it.discIndex == discIndex },
            updatedAt = System.currentTimeMillis(),
        )
        save(next)
        return next
    }

    fun partialBytes(snapshot: RomDownloadTaskSnapshot): Long {
        val directory = stagingDirectory(snapshot)
        val completed = snapshot.downloadSet?.items.orEmpty().mapNotNull { item ->
            val checkpoint = snapshot.completedDiscs.firstOrNull {
                it.discIndex == item.discIndex && it.sourceSignature == sourceSignature(item.source)
            } ?: return@mapNotNull null
            val file = File(directory, checkpoint.preparedFileName)
            if (file.isFile && file.length() == checkpoint.preparedBytes) item to file else null
        }
        val completedOwners = completed.map { (item, _) ->
            HttpPartialOwner(item.sourcePlan.logicalIdentity.value, item.sourcePlan.selectedCandidate.identity)
        }.toSet()
        val activeOwners = buildSet {
            add(HttpPartialOwner(snapshot.sourcePlan.logicalIdentity.value, snapshot.sourcePlan.selectedCandidate.identity))
            snapshot.downloadSet?.items?.forEach { item ->
                add(HttpPartialOwner(item.sourcePlan.logicalIdentity.value, item.sourcePlan.selectedCandidate.identity))
            }
        }
        val activeRecoveryPaths = snapshot.httpRecoveries.asSequence()
            .filterNot { it.quarantined }
            .filter { it.owner in activeOwners }
            .filterNot { it.owner in completedOwners }
            .map { it.relativePath }
            .toSet()
        // This is transfer progress/remaining-network estimation, not staging disk usage. A
        // completed disc's retained transport and prepared checkpoint represent the same bytes
        // acquired once; expanded ZIP payload size must not be counted as more network progress.
        val completedBytes = completed.sumOf { (item, file) ->
            item.source.expectedSize.takeIf { it > 0L } ?: file.length()
        }
        return completedBytes + activeRecoveryPaths.mapNotNull { safeRelativeFile(directory, it) }
            .distinctBy { it.absolutePath }
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    @Synchronized
    internal fun httpRecoveryAccess(gameId: String): HttpRecoveryAccess = object : HttpRecoveryAccess {
        override fun get(owner: HttpPartialOwner): HttpCandidateRecovery? = synchronized(this@RomDownloadTaskStore) {
            this@RomDownloadTaskStore.get(gameId)?.httpRecoveries?.firstOrNull { it.owner == owner && !it.quarantined }
        }

        override fun hasQuarantined(owner: HttpPartialOwner): Boolean = synchronized(this@RomDownloadTaskStore) {
            this@RomDownloadTaskStore.get(gameId)?.httpRecoveries?.any { it.owner == owner && it.quarantined } == true
        }

        override fun persist(recovery: HttpCandidateRecovery) = synchronized(this@RomDownloadTaskStore) {
            val current = this@RomDownloadTaskStore.get(gameId) ?: return
            val next = current.withPersistedHttpRecovery(recovery, System.currentTimeMillis())
            save(next, durable = true)
        }

        override fun quarantine(owner: HttpPartialOwner) = synchronized(this@RomDownloadTaskStore) {
            val current = this@RomDownloadTaskStore.get(gameId) ?: return
            val next = current.copy(
                httpRecoveries = current.httpRecoveries.map {
                    if (it.owner == owner) it.copy(quarantined = true) else it
                },
                updatedAt = System.currentTimeMillis(),
            )
            save(next, durable = true)
        }

        override fun markUnsafeArtifactsPresent() = synchronized(this@RomDownloadTaskStore) {
            val current = this@RomDownloadTaskStore.get(gameId) ?: return
            if (!current.unsafeLegacyPartial) {
                save(
                    current.copy(
                        unsafeLegacyPartial = true,
                        updatedAt = System.currentTimeMillis(),
                    ),
                    durable = true,
                )
            }
        }
    }

    @Synchronized
    fun deletePartial(gameId: String): Boolean {
        val current = get(gameId) ?: return false
        val deleted = deleteStaging(current)
        val next = current.copy(
            status = if (current.status == RomDownloadStatus.SUCCESS) current.status else RomDownloadStatus.CANCELLED,
            stage = if (current.status == RomDownloadStatus.SUCCESS) current.stage else "Descarga parcial eliminada",
            downloaded = if (current.status == RomDownloadStatus.SUCCESS) current.downloaded else 0L,
            bytesPerSecond = 0L,
            error = null,
            httpRecoveries = if (deleted) emptyList() else current.httpRecoveries,
            unsafeLegacyPartial = if (deleted) false else current.unsafeLegacyPartial,
            updatedAt = System.currentTimeMillis(),
        )
        save(next)
        return deleted
    }

    fun clear(gameId: String) {
        prefs.edit().remove(key(gameId)).apply()
    }

    fun stagingDirectory(snapshot: RomDownloadTaskSnapshot): File =
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "sessions/${snapshot.stagingId}")

    private fun deleteStaging(snapshot: RomDownloadTaskSnapshot): Boolean =
        runCatching { stagingDirectory(snapshot).deleteRecursively() }.getOrDefault(false)

    private fun save(snapshot: RomDownloadTaskSnapshot, durable: Boolean = false) {
        val edit = prefs.edit().putString(key(snapshot.gameId), encode(snapshot).toString())
        if (durable) {
            check(edit.commit()) { "No fue posible persistir el estado durable de recuperación HTTP." }
        } else {
            edit.apply()
        }
    }

    companion object {
        private const val PREFS = "oz_background_rom_downloads"

        internal fun encodeForTest(snapshot: RomDownloadTaskSnapshot): String = encode(snapshot).toString()

        internal fun decodeForTest(raw: String): RomDownloadTaskSnapshot? = decode(raw)

    private fun encode(snapshot: RomDownloadTaskSnapshot): JSONObject = JSONObject().apply {
        put("schema", 6)
        put("taskId", snapshot.taskId)
        put("gameId", snapshot.gameId)
        put("source", sourceJson(snapshot.source))
        put("sourcePlan", sourcePlanJson(snapshot.sourcePlan))
        put("unsafeLegacyPartial", snapshot.unsafeLegacyPartial)
        put("httpRecoveries", JSONArray().apply {
            snapshot.httpRecoveries.forEach { recovery -> put(httpRecoveryJson(recovery)) }
        })
        put("downloadSet", snapshot.downloadSet?.let(::downloadSetJson) ?: JSONObject.NULL)
        put("completedDiscs", JSONArray().apply {
            snapshot.completedDiscs.forEach { checkpoint ->
                put(JSONObject().apply {
                    put("discIndex", checkpoint.discIndex)
                    put("sourceSignature", checkpoint.sourceSignature)
                    put("preparedFileName", checkpoint.preparedFileName)
                    put("preparedBytes", checkpoint.preparedBytes)
                })
            }
        })
        put("biosSources", JSONArray().apply {
            snapshot.biosSources.forEach { (name, source) ->
                put(JSONObject().apply { put("name", name); put("source", sourceJson(source)) })
            }
        })
        put("resolvedRegion", snapshot.resolvedRegion ?: JSONObject.NULL)
        put("externalLauncher", snapshot.externalLauncher)
        put("stagingId", snapshot.stagingId)
        put("status", snapshot.status.name)
        put("stage", snapshot.stage)
        put("downloaded", snapshot.downloaded)
        put("total", snapshot.total)
        put("bytesPerSecond", snapshot.bytesPerSecond)
        put("peers", snapshot.peers)
        put("fileName", snapshot.fileName)
        put("error", snapshot.error ?: JSONObject.NULL)
        put("storedUri", snapshot.storedUri ?: JSONObject.NULL)
        put("storedDisplayName", snapshot.storedDisplayName ?: JSONObject.NULL)
        put("startedAt", snapshot.startedAt)
        put("updatedAt", snapshot.updatedAt)
    }

    private fun decode(raw: String): RomDownloadTaskSnapshot? = runCatching {
        val json = JSONObject(raw)
        val schema = json.optInt("schema", 4)
        val bios = json.optJSONArray("biosSources") ?: JSONArray()
        val completed = json.optJSONArray("completedDiscs") ?: JSONArray()
        val source = parseSource(json.getJSONObject("source"))
        val logicalIdentity = LogicalDownloadIdentity.forTask(
            gameId = json.getString("gameId"),
            resolvedRegion = json.optString("resolvedRegion").takeIf { it.isNotBlank() && it != "null" },
            expectedFileName = source.expectedFileName,
            expectedSize = source.expectedSize,
        )
        RomDownloadTaskSnapshot(
            taskId = json.getString("taskId"),
            gameId = json.getString("gameId"),
            source = source,
            downloadSet = json.optJSONObject("downloadSet")?.let(::parseDownloadSet),
            completedDiscs = buildList {
                for (index in 0 until completed.length()) {
                    val checkpoint = completed.getJSONObject(index)
                    add(
                        RomDownloadDiscCheckpoint(
                            discIndex = checkpoint.getInt("discIndex"),
                            sourceSignature = checkpoint.getString("sourceSignature"),
                            preparedFileName = checkpoint.getString("preparedFileName"),
                            preparedBytes = checkpoint.optLong("preparedBytes", -1L),
                        ),
                    )
                }
            },
            biosSources = buildList {
                for (index in 0 until bios.length()) {
                    val item = bios.getJSONObject(index)
                    add(item.getString("name") to parseSource(item.getJSONObject("source")))
                }
            },
            resolvedRegion = json.optString("resolvedRegion").takeIf { it.isNotBlank() && it != "null" },
            externalLauncher = json.optBoolean("externalLauncher", false),
            stagingId = json.getString("stagingId"),
            status = runCatching { RomDownloadStatus.valueOf(json.getString("status")) }.getOrDefault(RomDownloadStatus.FAILED),
            stage = json.optString("stage"),
            downloaded = json.optLong("downloaded", 0L),
            total = json.optLong("total", -1L),
            bytesPerSecond = json.optLong("bytesPerSecond", 0L),
            peers = json.optInt("peers", 0),
            fileName = json.optString("fileName"),
            error = json.optString("error").takeIf { it.isNotBlank() && it != "null" },
            storedUri = json.optString("storedUri").takeIf { it.isNotBlank() && it != "null" },
            storedDisplayName = json.optString("storedDisplayName").takeIf { it.isNotBlank() && it != "null" },
            startedAt = json.optLong("startedAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            sourcePlan = json.optJSONObject("sourcePlan")?.let(::parseSourcePlan)?.copy(
                logicalIdentity = logicalIdentity,
            )
                ?: DownloadSourcePlan.single(
                    logicalIdentity,
                    source,
                ),
            httpRecoveries = parseHttpRecoveries(json.optJSONArray("httpRecoveries")),
            unsafeLegacyPartial = if (schema < 6) true else json.optBoolean("unsafeLegacyPartial", false),
        )
    }.getOrNull()

    private fun sourceJson(source: AuthorizedSource): JSONObject = JSONObject().apply {
        put("value", source.value)
        put("expectedFileName", source.expectedFileName ?: JSONObject.NULL)
        put("torrentFileIndex", source.torrentFileIndex ?: JSONObject.NULL)
        put("torrentFilePath", source.torrentFilePath ?: JSONObject.NULL)
        put("expectedSize", source.expectedSize)
        put("sourceLabel", source.sourceLabel ?: JSONObject.NULL)
        put("requiresIa", source.requiresInternetArchiveAuth)
    }

    private fun sourcePlanJson(plan: DownloadSourcePlan): JSONObject = JSONObject().apply {
        val stored = plan.toStoredSourcePlan()
        put("logicalIdentity", stored.logicalIdentity)
        put("selectedCandidateIndex", stored.selectedCandidateIndex)
        put("sourceIndexProvenance", stored.sourceIndexProvenance?.let(::sourceIndexProvenanceJson) ?: JSONObject.NULL)
        put("candidates", JSONArray().apply {
            stored.candidates.forEach { candidate ->
                put(JSONObject().apply {
                    put("identity", candidate.identity)
                    put("source", sourceJson(candidate.source))
                })
            }
        })
    }

    private fun parseSourcePlan(json: JSONObject): DownloadSourcePlan {
        val candidates = json.getJSONArray("candidates")
        return StoredDownloadSourcePlan(
            logicalIdentity = json.getString("logicalIdentity"),
            candidates = buildList {
                for (index in 0 until candidates.length()) {
                    val candidate = candidates.getJSONObject(index)
                    add(
                        StoredDownloadSourceCandidate(
                            identity = candidate.getString("identity"),
                            source = parseSource(candidate.getJSONObject("source")),
                        ),
                    )
                }
            },
            selectedCandidateIndex = json.optInt("selectedCandidateIndex", 0),
            sourceIndexProvenance = json.optJSONObject("sourceIndexProvenance")?.let(::parseSourceIndexProvenance),
        ).toSourcePlan()
    }

    private fun sourceIndexProvenanceJson(value: SourceIndexProvenance): JSONObject = JSONObject().apply {
        put("gameId", value.gameId)
        put("releaseId", value.releaseId)
        put("candidateId", value.candidateId)
        put("provider", value.provider)
        put("infoHash", value.infoHash)
        put("fileIndex", value.fileIndex)
        put("exactFullPath", value.exactFullPath)
        put("expectedSize", value.expectedSize)
    }

    private fun parseSourceIndexProvenance(json: JSONObject): SourceIndexProvenance = SourceIndexProvenance(
        gameId = json.getString("gameId"),
        releaseId = json.getString("releaseId"),
        candidateId = json.getString("candidateId"),
        provider = json.getString("provider"),
        infoHash = json.getString("infoHash"),
        fileIndex = json.getInt("fileIndex"),
        exactFullPath = json.getString("exactFullPath"),
        expectedSize = json.getLong("expectedSize"),
    )

    private fun httpRecoveryJson(recovery: HttpCandidateRecovery): JSONObject = JSONObject().apply {
        put("logicalIdentity", recovery.owner.logicalIdentity)
        put("candidateIdentity", recovery.owner.candidateIdentity)
        put("relativePath", recovery.relativePath)
        put("quarantined", recovery.quarantined)
        put("strongETag", recovery.representation.strongETag ?: JSONObject.NULL)
        put("weakETag", recovery.representation.weakETag ?: JSONObject.NULL)
        put("lastModified", recovery.representation.lastModified ?: JSONObject.NULL)
        put("lastModifiedStrong", recovery.representation.lastModifiedStrong)
        put("totalLength", recovery.representation.totalLength)
        put("effectiveUrl", recovery.representation.effectiveUrl ?: JSONObject.NULL)
    }

    private fun parseHttpRecoveries(array: JSONArray?): List<HttpCandidateRecovery> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                HttpCandidateRecovery(
                    owner = HttpPartialOwner(item.getString("logicalIdentity"), item.getString("candidateIdentity")),
                    relativePath = item.getString("relativePath"),
                    representation = HttpRepresentationState(
                        strongETag = nullableString(item, "strongETag"),
                        weakETag = nullableString(item, "weakETag"),
                        lastModified = nullableString(item, "lastModified"),
                        lastModifiedStrong = item.optBoolean("lastModifiedStrong", false),
                        totalLength = item.optLong("totalLength", -1L),
                        effectiveUrl = nullableString(item, "effectiveUrl"),
                    ),
                    quarantined = item.optBoolean("quarantined", false),
                ),
            )
        }
    }

    private fun downloadSetJson(set: RomDownloadSet): JSONObject = JSONObject().apply {
        put("parentTitle", set.parentTitle)
        put("editionTitle", set.editionTitle)
        put("region", set.region)
        put("format", set.format)
        put("artworkUrl", set.artworkUrl ?: JSONObject.NULL)
        put("items", JSONArray().apply {
            set.items.forEach { item ->
                put(JSONObject().apply {
                    put("discIndex", item.discIndex)
                    put("discLabel", item.discLabel)
                    put("source", sourceJson(item.source))
                    put("sourcePlan", sourcePlanJson(item.sourcePlan))
                })
            }
        })
    }

    private fun parseDownloadSet(json: JSONObject): RomDownloadSet {
        val items = json.getJSONArray("items")
        return RomDownloadSet(
            parentTitle = json.getString("parentTitle"),
            editionTitle = json.getString("editionTitle"),
            region = json.getString("region"),
            format = json.getString("format"),
            artworkUrl = json.optString("artworkUrl").takeIf { it.isNotBlank() && it != "null" },
            items = buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val source = parseSource(item.getJSONObject("source"))
                    val logicalIdentity = LogicalDownloadIdentity.forDisc(
                        parentTitle = json.getString("parentTitle"),
                        editionTitle = json.getString("editionTitle"),
                        region = json.getString("region"),
                        format = json.getString("format"),
                        discIndex = item.getInt("discIndex"),
                        expectedFileName = source.expectedFileName,
                        expectedSize = source.expectedSize,
                    )
                    add(
                        RomDownloadSetItem(
                            discIndex = item.getInt("discIndex"),
                            discLabel = item.optString("discLabel", "Disco ${index + 1}"),
                            source = source,
                            sourcePlan = item.optJSONObject("sourcePlan")?.let(::parseSourcePlan)?.copy(
                                logicalIdentity = logicalIdentity,
                            )
                                ?: DownloadSourcePlan.single(
                                    logicalIdentity,
                                    source,
                                ),
                        ),
                    )
                }
            },
        )
    }

    private fun parseSource(json: JSONObject): AuthorizedSource = AuthorizedSource(
        value = json.getString("value"),
        expectedFileName = json.optString("expectedFileName").takeIf { it.isNotBlank() && it != "null" },
        torrentFileIndex = if (json.isNull("torrentFileIndex")) null else json.optInt("torrentFileIndex"),
        torrentFilePath = json.optString("torrentFilePath").takeIf { it.isNotBlank() && it != "null" },
        expectedSize = json.optLong("expectedSize", -1L),
        sourceLabel = json.optString("sourceLabel").takeIf { it.isNotBlank() && it != "null" },
        requiresInternetArchiveAuth = json.optBoolean("requiresIa", false),
    )

    private fun nullableString(json: JSONObject, name: String): String? =
        json.optString(name).takeIf { it.isNotBlank() && it != "null" }

    private fun sourceSignature(source: AuthorizedSource): String =
        listOf(
            source.value,
            source.expectedFileName.orEmpty(),
            source.expectedSize.toString(),
            source.torrentFileIndex?.toString().orEmpty(),
            source.torrentFilePath.orEmpty(),
        ).joinToString("|")

    }

    private fun key(gameId: String) = "task.$gameId"

    private fun hasAnyStagedFile(snapshot: RomDownloadTaskSnapshot): Boolean =
        stagingDirectory(snapshot).walkTopDown().any { it.isFile }

    private fun safeRelativeFile(root: File, relativePath: String): File? {
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(root, relativePath).canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.path.startsWith(canonicalRoot.path + File.separator) }
    }

}
