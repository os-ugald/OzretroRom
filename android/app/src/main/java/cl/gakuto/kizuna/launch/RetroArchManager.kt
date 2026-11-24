package cl.gakuto.kizuna.launch

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import cl.gakuto.kizuna.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Native bridge between Oz RetroRom and RetroArch.
 *
 * Android prevents one app from writing directly into another app's private data directory.
 * Oz therefore uses either:
 *  1) a user-selected external core directory shared with RetroArch, or
 *  2) RetroArch's exported CoreSideloadActivity, staging the official core in Downloads first.
 */
class RetroArchManager(private val context: Context) {
    private val trustedCoreCatalog = TrustedCoreCatalog(context)

    data class Variant(val packageName: String, val label: String, val installed: Boolean)

    data class CoreQuery(
        val supported: Boolean,
        val cores: Set<String>,
    )

    data class CoreProgress(
        val stage: String,
        val downloaded: Long = 0L,
        val total: Long = 0L,
    )

    data class CoreArtifact(
        val coreName: String,
        val absolutePath: String,
        val directExternalCore: Boolean,
    )

    fun variants(): List<Variant> = KNOWN_VARIANTS.map { (packageName, fallbackLabel) ->
        val installed = isInstalled(packageName)
        val label = if (installed) {
            runCatching {
                val info = context.packageManager.getApplicationInfoCompat(packageName)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(fallbackLabel)
        } else {
            fallbackLabel
        }
        Variant(packageName, label, installed)
    }

    fun installedVariants(): List<Variant> = variants().filter(Variant::installed)

    fun preferredInstalledPackage(preferred: String?): String? {
        val preferredPackage = preferred?.trim().orEmpty()
        if (preferredPackage.isNotBlank() && isInstalled(preferredPackage)) return preferredPackage
        return installedVariants().firstOrNull()?.packageName
    }

    fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfoCompat(packageName)
        true
    }.getOrDefault(false)

    fun openRetroArch(packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launch); true }.getOrDefault(false)
    }

    fun openOfficialInstallPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_ANDROID_INSTALL_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openStorageAccessSettings(packageName: String): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun normalizeCoreId(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "fbneo", "finalburn neo", "finalburnneo" -> "fbneo_libretro_android.so"
        else -> value.substringAfterLast('/').let { if (it.endsWith(".so")) it else "${it}_libretro_android.so" }
    }

    fun directCorePath(packageName: String, coreName: String): String {
        val targetInfo = context.packageManager.getApplicationInfoCompat(packageName)
        return resolvedCorePath(targetInfo.dataDir, coreName)
    }

    /**
     * Returns the configuration file path used by RetroArch's Android frontend.
     *
     * Oz never reads or writes this file: Android scoped storage may prevent that and, more
     * importantly, RetroArch must remain the owner of its configuration. We only pass the path
     * back to RetroActivityFuture through the documented CONFIGFILE intent extra, matching
     * MainMenuActivity.startRetroActivity(). This makes external launches reuse the same global
     * settings (language, video driver, shaders, input, directories, etc.) as a normal RetroArch
     * launch instead of booting with an anonymous/default configuration.
     */
    fun defaultConfigPath(packageName: String): String {
        val targetExternalFiles = runCatching {
            context.createPackageContext(packageName, 0).getExternalFilesDir(null)
        }.getOrNull()

        val configRoot = targetExternalFiles ?: File(
            Environment.getExternalStorageDirectory(),
            "Android/data/$packageName/files",
        )
        return File(configRoot, RETROARCH_CONFIG_FILE).absolutePath
    }

    /**
     * Reproduces the parameters prepared by RetroArch's own
     * MainMenuActivity.startRetroActivity(), so Oz does not depend on iiSU.
     */
    fun buildDirectLaunchIntent(
        packageName: String,
        corePath: String,
        romPath: String,
    ): Intent {
        val externalRoot = Environment.getExternalStorageDirectory().absolutePath
        val targetInfo = context.packageManager.getApplicationInfoCompat(packageName)
        val dataDir = targetInfo.dataDir
        val sourceApk = targetInfo.sourceDir
        val configFile = defaultConfigPath(packageName)
        val ime = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        )
        return Intent().apply {
            component = ComponentName(packageName, RETRO_ACTIVITY)
            putExtra(EXTRA_ROM, romPath)
            putExtra(EXTRA_CORE, corePath)
            putExtra(EXTRA_CONFIGFILE, configFile)
            putExtra("DATADIR", dataDir)
            putExtra("IME", ime.orEmpty())
            sourceApk?.takeIf(String::isNotBlank)?.let { putExtra("APK", it) }
            putExtra("SDCARD", externalRoot)
            putExtra("EXTERNAL", externalRoot)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    fun directLaunchActivityAvailable(packageName: String): Boolean = runCatching {
        val component = ComponentName(packageName, RETRO_ACTIVITY)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getActivityInfo(component, 0)
        }
        info.exported && info.enabled
    }.getOrDefault(false)

    fun buildCoreSideloadIntent(
        packageName: String,
        artifact: CoreArtifact,
        romPath: String? = null,
    ): Intent = Intent().apply {
        component = ComponentName(packageName, CORE_SIDELOAD_ACTIVITY)
        putExtra(EXTRA_CORE, artifact.absolutePath)
        romPath?.takeIf(String::isNotBlank)?.let { putExtra(EXTRA_ROM, it) }
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    fun selectedFolderContains(treeUri: Uri?, coreName: String): Boolean = runCatching {
        val root = treeUri?.let { DocumentFile.fromTreeUri(context, it) } ?: return@runCatching false
        root.findFile(coreName)?.isFile == true
    }.getOrDefault(false)

    /**
     * Queries RetroArch through its exported InstalledCoresReceiver.
     *
     * Modern Android package visibility and broadcast delivery are more reliable when the
     * receiver is resolved first and then targeted explicitly. Some RetroArch builds still
     * answer through a secondary INSTALLED_CORES_RESULT broadcast, while others can return
     * ordered-broadcast result extras/data, so Oz accepts both verified response shapes.
     *
     * supported=false means the installed RetroArch build did not expose/respond to the
     * enumeration contract. It does NOT prove that the requested core is absent.
     */
    suspend fun queryInstalledCores(packageName: String, timeoutMs: Long = 4_000L): CoreQuery =
        withContext(Dispatchers.Main) {
            val receiverComponent = installedCoresReceiver(packageName)
                ?: return@withContext CoreQuery(supported = false, cores = emptySet())
            val result = CompletableDeferred<CoreQuery>()
            var orderedReceiverResponded = false
            val responseReceiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    val cores = linkedSetOf<String>().apply {
                        addAll(extractCoreNames(intent?.extras))
                        addAll(extractCoreNames(intent?.dataString))
                    }
                    if (!result.isCompleted) {
                        result.complete(CoreQuery(supported = true, cores = cores))
                    }
                }
            }

            val registered = runCatching {
                ContextCompat.registerReceiver(
                    context,
                    responseReceiver,
                    IntentFilter(ACTION_INSTALLED_CORES_RESULT),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                true
            }.getOrDefault(false)
            if (!registered) return@withContext CoreQuery(supported = false, cores = emptySet())

            try {
                val finalReceiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, intent: Intent?) {
                        val cores = linkedSetOf<String>().apply {
                            addAll(extractCoreNames(getResultExtras(false)))
                            addAll(extractCoreNames(resultData))
                            addAll(extractCoreNames(intent?.extras))
                        }
                        orderedReceiverResponded = true
                        if (cores.isNotEmpty() && !result.isCompleted) {
                            result.complete(CoreQuery(supported = true, cores = cores))
                        }
                    }
                }
                val query = Intent(ACTION_QUERY_INSTALLED_CORES).apply {
                    component = receiverComponent
                    putExtra("CALLER_PACKAGE", context.packageName)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                val sent = runCatching {
                    context.sendOrderedBroadcast(
                        query,
                        null,
                        finalReceiver,
                        Handler(Looper.getMainLooper()),
                        Activity.RESULT_OK,
                        null,
                        null,
                    )
                    true
                }.getOrDefault(false)
                if (!sent) return@withContext CoreQuery(supported = false, cores = emptySet())
                withTimeoutOrNull(timeoutMs) { result.await() }
                    ?: if (orderedReceiverResponded) {
                        CoreQuery(supported = true, cores = emptySet())
                    } else {
                        CoreQuery(supported = false, cores = emptySet())
                    }
            } finally {
                runCatching { context.unregisterReceiver(responseReceiver) }
            }
        }

    /** Resolve only the documented/exported RetroArch installed-core receiver. */
    fun installedCoresReceiver(packageName: String): ComponentName? = runCatching {
        val query = Intent(ACTION_QUERY_INSTALLED_CORES).setPackage(packageName)
        val receivers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryBroadcastReceivers(
                query,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryBroadcastReceivers(query, 0)
        }
        receivers.asSequence()
            .mapNotNull { it.activityInfo }
            .firstOrNull { info ->
                info.packageName == packageName && info.exported && info.enabled &&
                    info.name == INSTALLED_CORES_RECEIVER
            }
            ?.let { ComponentName(it.packageName, it.name) }
    }.getOrNull()

    fun coreCandidatesFor(value: String): List<String> = coreCandidates(normalizeCoreId(value))

    fun trustedCoreAvailable(requestedCore: String): Boolean {
        val abi = supportedAbiDirectory() ?: return false
        return coreCandidatesFor(requestedCore).any { trustedCoreCatalog.contains(abi, it) }
    }

    fun canAutomaticallyInstall(requestedCore: String): Boolean =
        BuildConfig.CORE_DOWNLOAD_POLICY == "development" || trustedCoreAvailable(requestedCore)

    fun installedCompatibleCore(query: CoreQuery, requestedCore: String): String? =
        coreCandidatesFor(requestedCore).firstOrNull { candidate ->
            val wanted = normalizeDetectedName(candidate)
            query.cores.any { normalizeDetectedName(it) == wanted }
        }

    fun coreIsPresent(query: CoreQuery, coreName: String): Boolean =
        installedCompatibleCore(query, coreName) != null

    fun selectedFolderCompatibleCore(treeUri: Uri?, requestedCore: String): String? =
        coreCandidatesFor(requestedCore).firstOrNull { selectedFolderContains(treeUri, it) }

    suspend fun downloadAndStageCore(
        requestedCoreName: String,
        externalCoreTreeUri: Uri?,
        onProgress: (CoreProgress) -> Unit = {},
    ): CoreArtifact = withContext(Dispatchers.IO) {
        require(supportedAbiDirectory() != null) {
            "La instalación automática de cores requiere Android ARM64 o ARMv7."
        }
        val candidates = coreCandidates(normalizeCoreId(requestedCoreName))
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                onProgress(CoreProgress("Buscando core $candidate"))
                val extracted = downloadAndExtract(candidate, onProgress)
                return@withContext if (externalCoreTreeUri != null) {
                    writeToSelectedTree(externalCoreTreeUri, extracted, candidate)
                } else {
                    writeToSharedDownloads(extracted, candidate)
                }
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IOException(
            "No se pudo obtener un core compatible (${candidates.joinToString()}). ${lastError?.message.orEmpty()}",
            lastError,
        )
    }

    private fun downloadAndExtract(coreName: String, onProgress: (CoreProgress) -> Unit): File {
        val abi = supportedAbiDirectory() ?: error("ABI no compatible")
        val trusted = trustedCoreCatalog.find(abi, coreName)
        val developmentMode = BuildConfig.CORE_DOWNLOAD_POLICY == "development"
        require(trusted != null || developmentMode) {
            "El core $coreName no está incluido en el catálogo SHA-256 de esta versión. " +
                "Instálalo desde RetroArch o publica una nueva APK con trusted_cores.json actualizado."
        }

        val url = URL(trusted?.url ?: developmentCoreArtifactUrl(abi, coreName))
        require(url.protocol.equals("https", true) && url.host.equals(TRUSTED_CORE_HOST, true)) {
            "La fuente del core no está autorizada."
        }

        val archive = File.createTempFile("oz-core-", ".zip", context.cacheDir)
        val output = File.createTempFile("oz-core-", ".so", context.cacheDir)
        var connection: HttpURLConnection? = null
        try {
            val activeConnection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "OzRetroRom/${Build.VERSION.SDK_INT}")
            }
            connection = activeConnection
            activeConnection.connect()
            if (activeConnection.responseCode !in 200..299) {
                throw IOException("Servidor de cores respondió HTTP ${activeConnection.responseCode} para $coreName")
            }
            val total = activeConnection.contentLengthLong.coerceAtLeast(0L)
            require(total == 0L || total <= MAX_CORE_ARCHIVE_BYTES) { "El paquete del core excede el tamaño permitido." }
            activeConnection.inputStream.use { input ->
                FileOutputStream(archive).use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        require(downloaded <= MAX_CORE_ARCHIVE_BYTES) { "El paquete del core excede el tamaño permitido." }
                        target.write(buffer, 0, read)
                        onProgress(CoreProgress("Descargando core", downloaded, total))
                    }
                }
            }

            trusted?.let { entry ->
                require(sha256(archive) == entry.archiveSha256) {
                    "La firma SHA-256 del paquete $coreName no coincide. No se instalará."
                }
            }

            var extracted = false
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                var entries = 0
                while (entries++ < MAX_CORE_ZIP_ENTRIES) {
                    val entry = zip.nextEntry ?: break
                    val entryName = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && entryName.equals(coreName, true)) {
                        FileOutputStream(output).use { zip.copyTo(it) }
                        extracted = true
                        break
                    }
                }
            }
            require(extracted && output.length() > 4_096L) {
                "El paquete del core no contenía la biblioteca Android esperada."
            }
            trusted?.let { entry ->
                require(sha256(output) == entry.librarySha256) {
                    "La firma SHA-256 de la biblioteca $coreName no coincide. No se instalará."
                }
            }
            onProgress(CoreProgress("Core verificado", output.length(), output.length()))
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            connection?.disconnect()
            archive.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun writeToSelectedTree(treeUri: Uri, extracted: File, coreName: String): CoreArtifact {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("La carpeta de cores seleccionada ya no está disponible.")
        root.findFile(coreName)?.delete()
        val target = root.createFile("application/octet-stream", coreName)
            ?: error("No se pudo crear $coreName en la carpeta seleccionada.")
        context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
            extracted.inputStream().use { it.copyTo(output) }
        } ?: error("No se pudo escribir el core en la carpeta seleccionada.")
        val rootPath = ExternalPathResolver.resolve(treeUri.toString())
            ?: error("La carpeta de cores debe pertenecer al almacenamiento interno o a una tarjeta SD visible por RetroArch.")
        extracted.delete()
        // The selected SAF folder is a staging source. RetroArch must import the core through
        // CoreSideloadActivity; Oz never treats a shared folder as RetroArch's private core dir.
        return CoreArtifact(coreName, File(rootPath, coreName).absolutePath, directExternalCore = false)
    }

    private fun writeToSharedDownloads(extracted: File, coreName: String): CoreArtifact {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/OzRetroRom/Cores/"
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(coreName, relativePath),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    resolver.delete(Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()), null, null)
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, coreName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Android no permitió crear el archivo puente del core.")
            try {
                resolver.openOutputStream(target, "w")?.use { output ->
                    extracted.inputStream().use { it.copyTo(output) }
                } ?: error("No se pudo escribir el archivo puente del core.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(target, values, null, null)
            } catch (error: Throwable) {
                resolver.delete(target, null, null)
                throw error
            } finally {
                extracted.delete()
            }
            val path = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "OzRetroRom/Cores/$coreName",
            ).absolutePath
            return CoreArtifact(coreName, path, directExternalCore = false)
        }

        @Suppress("DEPRECATION")
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OzRetroRom/Cores")
        require(folder.exists() || folder.mkdirs()) { "No se pudo crear la carpeta compartida de cores." }
        val target = File(folder, coreName)
        extracted.copyTo(target, overwrite = true)
        extracted.delete()
        return CoreArtifact(coreName, target.absolutePath, directExternalCore = false)
    }

    private fun supportedAbiDirectory(): String? = when {
        Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", true) } -> "arm64-v8a"
        Build.SUPPORTED_ABIS.any { it.equals("armeabi-v7a", true) } -> "armeabi-v7a"
        else -> null
    }

    private fun coreCandidates(normalized: String): List<String> = buildList {
        add(normalized)
        when (normalized.lowercase(Locale.ROOT)) {
            "azahar_libretro_android.so" -> add("panda3ds_libretro_android.so")
            "swanstation_libretro_android.so" -> add("pcsx_rearmed_libretro_android.so")
            "pcsx_rearmed_libretro_android.so" -> add("swanstation_libretro_android.so")
        }
    }.distinct()

    private fun extractCoreNames(bundle: Bundle?): Set<String> {
        if (bundle == null) return emptySet()
        val result = linkedSetOf<String>()
        bundle.keySet().forEach { key ->
            when (val value = bundle.get(key)) {
                is String -> value.split('\n', ',', ';').mapTo(result) { it.trim() }
                is Array<*> -> value.filterIsInstance<String>().mapTo(result) { it.trim() }
                is Collection<*> -> value.filterIsInstance<String>().mapTo(result) { it.trim() }
                is Bundle -> result += extractCoreNames(value)
            }
        }
        return result.filterTo(linkedSetOf()) { it.contains("libretro", true) || it.endsWith(".so", true) }
    }

    private fun extractCoreNames(value: String?): Set<String> = value
        ?.split('\n', ',', ';')
        ?.map(String::trim)
        ?.filterTo(linkedSetOf()) { it.contains("libretro", true) || it.endsWith(".so", true) }
        ?: emptySet()

    private fun normalizeDetectedName(value: String): String = value
        .substringAfterLast('/')
        .lowercase(Locale.ROOT)
        .removeSuffix(".zip")
        .replace("_libretro.so", "_libretro_android.so")

    private fun PackageManager.getPackageInfoCompat(packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, 0)
        }

    private fun PackageManager.getApplicationInfoCompat(packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getApplicationInfo(packageName, 0)
        }

    companion object {
        const val ACTION_QUERY_INSTALLED_CORES = "com.retroarch.QUERY_INSTALLED_CORES"
        const val ACTION_INSTALLED_CORES_RESULT = "com.retroarch.INSTALLED_CORES_RESULT"
        const val INSTALLED_CORES_RECEIVER = "com.retroarch.browser.receiver.InstalledCoresReceiver"
        const val RETRO_ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture"
        const val CORE_SIDELOAD_ACTIVITY = "com.retroarch.browser.debug.CoreSideloadActivity"
        const val EXTRA_ROM = "ROM"
        const val EXTRA_CORE = "LIBRETRO"
        const val EXTRA_CONFIGFILE = "CONFIGFILE"
        const val RETROARCH_CONFIG_FILE = "retroarch.cfg"

        const val OFFICIAL_ANDROID_INSTALL_URL = "https://docs.libretro.com/guides/install-android/"
        const val CORE_BUILDBOT_BASE = "https://buildbot.libretro.com/nightly/android/latest"
        private const val TRUSTED_CORE_HOST = "buildbot.libretro.com"
        private const val MAX_CORE_ARCHIVE_BYTES = 256L * 1024L * 1024L
        private const val MAX_CORE_ZIP_ENTRIES = 64

        internal fun developmentCoreArtifactUrl(abi: String, coreName: String): String =
            "$CORE_BUILDBOT_BASE/$abi/$coreName.zip"

        internal fun resolvedCorePath(dataDir: String, coreName: String): String =
            File(dataDir, "cores/$coreName").absolutePath

        val KNOWN_VARIANTS: List<Pair<String, String>> = listOf(
            "com.retroarch.aarch64" to "RetroArch AArch64 (64 bits)",
            "com.retroarch" to "RetroArch Universal",
            "com.retroarch.ra32" to "RetroArch RA32 (32 bits)",
        )
    }
}
